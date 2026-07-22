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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
/**
 * Core flight-search orchestrator; no HTTP layer—invoked by FlightSearchController.
 * Gateway entry: GET /api/flights/search. Feign deps: PricingClient, AirlineClient,
 * LocationClient, SeatClient. Data flow: JPA spec on local instances → per-row fare/cabin
 * filter via pricing + seat → enrich with airline/airport/aircraft from Feign caches.
 */
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
    public Page<FlightInstanceResponse> searchFlights(
            FlightSearchRequest request,
            Pageable pageable
    ) {

        log.debug(
                "Searching flights departureAirportId={} arrivalAirportId={} departureDate={} cabinClass={} passengers={}",
                request.getDepartureAirportId(),
                request.getArrivalAirportId(),
                request.getDepartureDate(),
                request.getCabinClass(),
                request.getPassengers()
        );

        // ── Phase 1: Build pageable with requested sorting ───────────────────────
        Pageable sortedPageable =
                applySort(
                        pageable,
                        request.getSortBy(),
                        request.getSortOrder()
                );

        // ── Phase 2: Execute dynamic database search ─────────────────────────────
        Specification<FlightInstance> spec =
                FlightInstanceSpecification.buildSearchSpec(request);

        Page<FlightInstance> dbPage =
                flightInstanceRepository.findAll(
                        spec,
                        sortedPageable
                );

        if (dbPage.isEmpty()) {

            log.debug(
                    "No flight instances found for search request."
            );

            return Page.empty(sortedPageable);
        }

        List<FlightInstance> instances =
                new ArrayList<>(dbPage.getContent());

        Map<Long, FareResponse> fareMap =
                Collections.emptyMap();

        // ── Phase 3: Cabin-class & pricing filter ────────────────────────────────
        if (request.getCabinClass() != null) {

            final boolean hasPriceFilter =
                    request.getMinPrice() != null
                            || request.getMaxPrice() != null;

            Map<Long, FareResponse> mergedFareMap =
                    new HashMap<>();

            List<FlightInstance> filtered =
                    new ArrayList<>();

            for (FlightInstance fi : instances) {

                Long cabinClassId =
                        resolveCabinClassId(
                                request.getCabinClass(),
                                fi.getFlight().getAircraftId()
                        );

                if (cabinClassId == null) {

                    log.debug(
                            "Cabin class {} not available for aircraftId={}",
                            request.getCabinClass(),
                            fi.getFlight().getAircraftId()
                    );

                    continue;
                }

                try {

                    FareResponse fare =
                            pricingClient.getLowestFareForFlightAndCabinClass(
                                    fi.getFlight().getId(),
                                    cabinClassId
                            );

                    if (fare == null) {

                        log.debug(
                                "No fare found for flightId={} cabinClassId={}",
                                fi.getFlight().getId(),
                                cabinClassId
                        );

                        continue;
                    }

                    if (hasPriceFilter) {

                        Double price =
                                fare.getTotalPrice();

                        if (price == null) {
                            continue;
                        }

                        if (request.getMinPrice() != null
                                && price < request.getMinPrice()) {
                            continue;
                        }

                        if (request.getMaxPrice() != null
                                && price > request.getMaxPrice()) {
                            continue;
                        }
                    }

                    mergedFareMap.put(
                            fi.getFlight().getId(),
                            fare
                    );

                    filtered.add(fi);

                } catch (Exception exception) {

                    log.warn(
                            "Pricing lookup failed flightId={} cabinClassId={}. Skipping flight.",
                            fi.getFlight().getId(),
                            cabinClassId,
                            exception
                    );
                }
            }

            fareMap = mergedFareMap;
            instances = filtered;

            if (instances.isEmpty()) {

                log.debug(
                        "No flight instances remained after cabin-class and pricing filters."
                );

                return Page.empty(sortedPageable);
            }
        }

        // ── Phase 4: Enrich response with airline, airport and fare data ─────────
        List<FlightInstanceResponse> responses =
                enrichWithExternalData(
                        instances,
                        fareMap
                );

        log.debug(
                "Flight search completed matchedInstances={} returnedResponses={}",
                instances.size(),
                responses.size()
        );

        /*
         * totalElements reflects the database query before post-processing.
         * Price filtering occurs after retrieval, so counts may differ slightly.
         */
        return new PageImpl<>(
                responses,
                sortedPageable,
                dbPage.getTotalElements()
        );
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the cabin class ID from seat-service once for the whole page.
     * Tries each unique aircraft in the result set until one succeeds.
     * Returns null if seat-service is unavailable or the cabin class doesn't exist.
     */
    private Long resolveCabinClassId(
            CabinClassType cabinClassName,
            Long aircraftId
    ) {

        try {

            CabinClassResponse cabin =
                    seatClient.getCabinClassByAircraftIdAndName(
                            cabinClassName,
                            aircraftId
                    );

            if (cabin != null) {
                return cabin.getId();
            }

            log.debug(
                    "Cabin class {} not found for aircraftId={}",
                    cabinClassName,
                    aircraftId
            );

        } catch (Exception exception) {

            log.warn(
                    "Failed to resolve cabin class {} for aircraftId={}",
                    cabinClassName,
                    aircraftId,
                    exception
            );
        }

        return null;
    }


    /**
     * Fetches airline and airport details from remote services, deduplicating
     * calls with per-invocation caches so each unique ID is fetched at most once.
     * Uses the already-fetched {@code fareMap} — no extra calls to pricing or seat services.
     */
    private List<FlightInstanceResponse> enrichWithExternalData(
            List<FlightInstance> instances,
            Map<Long, FareResponse> fareMap
    ) {

        Map<Long, AirlineResponse> airlineCache =
                new HashMap<>();

        Map<Long, AirportResponse> airportCache =
                new HashMap<>();

        Map<Long, AircraftResponse> aircraftCache =
                new HashMap<>();

        List<FlightInstanceResponse> results =
                new ArrayList<>(instances.size());

        for (FlightInstance flightInstance : instances) {

            try {

                AircraftResponse aircraft =
                        aircraftCache.computeIfAbsent(
                                flightInstance.getFlight().getAircraftId(),
                                airlineClient::getAircraftById
                        );

                AirlineResponse airline =
                        airlineCache.computeIfAbsent(
                                flightInstance.getAirlineId(),
                                airlineClient::getAirlineById
                        );

                AirportResponse departureAirport =
                        airportCache.computeIfAbsent(
                                flightInstance.getDepartureAirportId(),
                                locationClient::getAirportById
                        );

                AirportResponse arrivalAirport =
                        airportCache.computeIfAbsent(
                                flightInstance.getArrivalAirportId(),
                                locationClient::getAirportById
                        );

                FlightInstanceResponse response =
                        FlightInstanceMapper.toResponse(
                                flightInstance,
                                aircraft,
                                airline,
                                departureAirport,
                                arrivalAirport
                        );

                response.setFare(
                        fareMap.get(
                                flightInstance.getFlight().getId()
                        )
                );

                results.add(response);

            } catch (Exception exception) {

                log.error(
                        "Failed to enrich flight instance response flightInstanceId={}. Skipping record.",
                        flightInstance.getId(),
                        exception
                );
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
    private Pageable applySort(
            Pageable pageable,
            String sortBy,
            String sortOrder
    ) {

        Sort.Direction direction =
                "desc".equalsIgnoreCase(sortOrder)
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;

        Sort sort =
                (sortBy == null || sortBy.isBlank())
                        ? Sort.by(direction, "departureDateTime")
                        : switch (sortBy.toLowerCase()) {

                    case "arrival" ->
                            Sort.by(direction, "arrivalDateTime");

                    case "duration" ->
                            JpaSort.unsafe(
                                    direction,
                                    "TIMESTAMPDIFF(MINUTE, departure_date_time, arrival_date_time)"
                            );

                    default ->
                            Sort.by(direction, "departureDateTime");
                };

        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sort
        );
    }
}
