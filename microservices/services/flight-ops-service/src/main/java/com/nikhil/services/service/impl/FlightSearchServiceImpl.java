package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.request.FlightSearchRequest;
import com.nikhil.common_lib.payload.response.*;
import com.nikhil.services.client.AirlineClient;
import com.nikhil.services.client.LocationClient;
import com.nikhil.services.client.PricingClient;
import com.nikhil.services.client.SeatClient;
import com.nikhil.services.mapper.FlightInstanceMapper;
import com.nikhil.services.model.FlightInstance;
import com.nikhil.services.repository.FlightInstanceRepository;
import com.nikhil.services.service.FlightSearchService;
import com.nikhil.services.specification.FlightInstanceSpecification;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightSearchServiceImpl implements FlightSearchService {

    private final FlightInstanceRepository flightInstanceRepository;
    private final LocationClient locationClient;
    private final AirlineClient airlineClient;
    private final PricingClient pricingClient;
    private final SeatClient seatClient;


    /**
     * Three-phase flight search that respects microservice boundaries
     * (no cross-service DB joins).
     *
     * <h3>Phase 1 – Cross-service filter resolution (before DB)</h3>
     * Resolves optional {@code airlines} (IATA codes) and {@code alliance}
     * to concrete airline IDs via single bulk Feign calls to airline-core-service.
     *
     * <h3>Phase 2 – DB query via JPA Specification</h3>
     * Filters everything owned by this service's own table: active/future status,
     * airports, departure date range, seat-count guard, airline IDs,
     * departure/arrival time-range buckets, max duration.
     *
     * <h3>Phase 3 – Price + cabin-class post-filter (after DB)</h3>
     * Resolves cabinClassId once from seat-service, then does a single batch
     * call to pricing-service. Filters by cabin class and price range, then
     * passes the already-fetched fare map to enrichment — no redundant calls.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<FlightInstanceResponse> searchFlights(FlightSearchRequest request, Pageable pageable) {

        System.out.println("searchFlights called with request: {}"+ request);


        // ── Phase 2: paginated DB query with dynamic Specification ────────────
        Pageable sortedPageable = applySort(pageable, request.getSortBy(), request.getSortOrder());

        Specification<FlightInstance> spec =
                FlightInstanceSpecification.buildSearchSpec(request);

        Page<FlightInstance> dbPage = flightInstanceRepository.findAll(spec, sortedPageable);

        System.out.println(" searchFlights: DB returned {} results " + dbPage.getContent().size());

        if (dbPage.isEmpty()) {
            return Page.empty(sortedPageable);
        }

        List<FlightInstance> instances = new ArrayList<>(dbPage.getContent());

        // ── Phase 3: cabin-class + price filtering via pricing-service ────────
        // Resolve cabin class ID once (not per-instance), fetch fares once.
        Map<Long, FareResponse> fareMap = Collections.emptyMap();

//        if (request.getCabinClass() != null) {
//            Long cabinClassId = resolveCabinClassId(request, instances);
//
//            if (cabinClassId == null) {
//                log.warn("searchFlights: cabin class '{}' not found in seat-service – returning empty page",
//                        request.getCabinClass());
//                return Page.empty(sortedPageable);
//            }
//
//            List<Long> flightIds = instances.stream()
//                    .map(instance->instance.getFlight().getId())
//                    .distinct()
//                    .collect(Collectors.toList());
//
//            fareMap = fetchLowestFares(flightIds, cabinClassId);
//
//
//
//            // Apply cabin-class + price range filter
//            final Map<Long, FareResponse> finalFareMap = fareMap;
//            System.out.println("is fare map empty? " + finalFareMap.toString());
//            final boolean hasPriceFilter = request.getMinPrice() != null || request.getMaxPrice() != null;
//
//            instances = instances.stream()
//                    .filter(fi -> {
//                        FareResponse fare = finalFareMap.get(fi.getFlight().getId());
//                        if (fare == null) return false; // no fare for requested cabin class
//
//                        if (hasPriceFilter) {
//                            Double price = fare.getTotalPrice();
//                            if (price == null) return false;
//                            if (request.getMinPrice() != null && price < request.getMinPrice()) return false;
//                            if (request.getMaxPrice() != null && price > request.getMaxPrice()) return false;
//                        }
//                        return true;
//                    })
//                    .collect(Collectors.toList());
//
//            System.out.println("cabin class not null "+ cabinClassId + " --- " + instances.size());
//
//            if (instances.isEmpty()) {
//                return Page.empty(sortedPageable);
//            }
//        }

        if (request.getCabinClass() != null) {
            final boolean hasPriceFilter = request.getMinPrice() != null || request.getMaxPrice() != null;
            Map<Long, FareResponse> mergedFareMap = new HashMap<>();

            List<FlightInstance> filtered = new ArrayList<>();

            for (FlightInstance fi : instances) {

                // 1. get cabinClassId for this specific aircraft
                Long cabinClassId = resolveCabinClassId(
                        request.getCabinClass(),
                        fi.getFlight().getAircraftId()
                );

                if (cabinClassId == null) continue; // this aircraft doesn't have the requested cabin

                // 2. fetch fare for this specific flight + cabinClass
                FareResponse fare = pricingClient.getLowestFareForFlightAndCabinClass(
                        fi.getFlight().getId(),
                        cabinClassId
                );

                if (fare == null) continue; // no fare available

                // 3. apply price filter
                if (hasPriceFilter) {
                    Double price = fare.getTotalPrice();
                    if (price == null) continue;
                    if (request.getMinPrice() != null && price < request.getMinPrice()) continue;
                    if (request.getMaxPrice() != null && price > request.getMaxPrice()) continue;
                }

                mergedFareMap.put(fi.getFlight().getId(), fare);
                filtered.add(fi);
            }

            fareMap = mergedFareMap;
            instances = filtered;

            if (instances.isEmpty()) {
                return Page.empty(sortedPageable);
            }
        }


        // ── Enrichment: airline + airport (per-request cache), fare already fetched ──
        List<FlightInstanceResponse> responses = enrichWithExternalData(instances, fareMap);

        System.out.println("searchFlights: returning {} enriched results"+ responses.size());

        // totalElements from DB page may slightly overcount when price filter
        // removes results post-DB. For perfect counts at scale use a search index.
        return new PageImpl<>(responses, sortedPageable, dbPage.getTotalElements());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the cabin class ID from seat-service once for the whole page.
     * Tries each unique aircraft in the result set until one succeeds.
     * Returns null if seat-service is unavailable or the cabin class doesn't exist.
     */
    private Long resolveCabinClassId(CabinClassType cabinClassName, Long aircraftId) {
        try {
            CabinClassResponse cabin = seatClient.getCabinClassByAircraftIdAndName(cabinClassName, aircraftId);
            if (cabin != null) return cabin.getId();
        } catch (Exception e) {
            log.warn("seat-service call failed for aircraftId={}: {}", aircraftId, e.getMessage());
        }
        return null;
    }

    /**
     * Single batch call to pricing-service.
     * Returns an empty map on failure so the search degrades gracefully.
     */
//    private Map<Long, FareResponse> fetchLowestFares(List<Long> flightIds, Long cabinClassId) {
//        System.out.println("fetchLowestFares cabinClassId: " + cabinClassId + " flightIds: " + flightIds.size());
//        if (cabinClassId == null || flightIds.isEmpty()) return Collections.emptyMap();
//        try {
//            Map<Long, FareResponse> result = pricingClient.getLowestFarePerFlight(flightIds, cabinClassId);
//            System.out.println("result: " + result);
//            return result ;
//        } catch (FeignException e) {
//            System.out.println("searchFlights: pricing-service call failed – price/cabin filter skipped: {}"+
//                    e.getMessage());
//            return Collections.emptyMap();
//        }
//    }



    /**
     * Fetches airline and airport details from remote services, deduplicating
     * calls with per-invocation caches so each unique ID is fetched at most once.
     * Uses the already-fetched {@code fareMap} — no extra calls to pricing or seat services.
     */
    private List<FlightInstanceResponse> enrichWithExternalData(
            List<FlightInstance> instances,
            Map<Long, FareResponse> fareMap) {

        Map<Long, AirlineResponse> airlineCache  = new HashMap<>();
        Map<Long, AirportResponse> airportCache  = new HashMap<>();
        Map<Long, AircraftResponse> aircraftCache = new HashMap<>();
        List<FlightInstanceResponse> results = new ArrayList<>(instances.size());

        for (FlightInstance fi : instances) {
            try {
                AircraftResponse aircraft = aircraftCache.computeIfAbsent(
                        fi.getFlight().getAircraftId(), airlineClient::getAircraftById);

                AirlineResponse airline = airlineCache.computeIfAbsent(
                        fi.getAirlineId(), airlineClient::getAirlineById);

                AirportResponse depAirport = airportCache.computeIfAbsent(
                        fi.getDepartureAirportId(), locationClient::getAirportById);

                AirportResponse arrAirport = airportCache.computeIfAbsent(
                        fi.getArrivalAirportId(), locationClient::getAirportById);

                FlightInstanceResponse response = FlightInstanceMapper.toResponse(
                        fi, aircraft, airline, depAirport, arrAirport);

                // Attach the pre-fetched fare (null if no cabin filter was applied)
                response.setFare(fareMap.get(fi.getFlight().getId()));

                results.add(response);

            } catch (Exception e) {
                log.error("searchFlights: enrichment failed for FlightInstance id={} – skipping: {}",
                        fi.getId(), e.getMessage());
            }
        }
        return results;
    }

    /**
     * Builds a sort-aware {@link Pageable}.
     *
     * <table>
     *   <tr><th>sortBy</th><th>DB expression</th></tr>
     *   <tr><td>departure (default)</td><td>departureDateTime</td></tr>
     *   <tr><td>arrival</td><td>arrivalDateTime</td></tr>
     *   <tr><td>duration</td><td>TIMESTAMPDIFF(MINUTE, departure_date_time, arrival_date_time)</td></tr>
     *   <tr><td>price</td><td>falls back to departureDateTime (price lives in pricing-service)</td></tr>
     * </table>
     */
    private Pageable applySort(Pageable pageable, String sortBy, String sortOrder) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

        Sort sort = (sortBy == null || sortBy.isBlank())
                ? Sort.by(direction, "departureDateTime")
                : switch (sortBy.toLowerCase()) {
                    case "arrival"  -> Sort.by(direction, "arrivalDateTime");
                    case "duration" -> JpaSort.unsafe(direction,
                            "TIMESTAMPDIFF(MINUTE, departure_date_time, arrival_date_time)");
                    default         -> Sort.by(direction, "departureDateTime");
                };

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
