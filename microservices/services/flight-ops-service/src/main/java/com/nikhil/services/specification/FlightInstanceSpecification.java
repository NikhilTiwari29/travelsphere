package com.nikhil.services.specification;

import com.nikhil.common_lib.enums.FlightStatus;
import com.nikhil.common_lib.payload.request.FlightSearchRequest;
import com.nikhil.services.model.FlightInstance;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * JPA Specification for dynamic {@link FlightInstance} search.
 *
 * <p><b>Gateway:</b> Results surface through api-gateway {@code /api/flight-instances/**}
 * (JWT-protected). Clients never hit flight-ops directly in production.
 *
 * <p><b>Feign (downstream):</b> Price filtering is <em>not</em> in this spec — after the DB query,
 * FlightInstanceServiceImpl batch-calls pricing-service via PricingClient to apply fare filters.
 *
 * <h3>What this class handles (pure DB filters)</h3>
 * <ul>
 *   <li>isActive = true</li>
 *   <li>Status not in {CANCELLED, COMPLETED, DIVERTED}</li>
 *   <li>departureDateTime strictly in the future</li>
 *   <li>Origin / destination airport IDs</li>
 *   <li>Departure date range (startOfDay … endOfDay)</li>
 *   <li>Total available seats &ge; requested passengers (general guard)</li>
 *   <li>Airline IDs (pre-resolved from IATA codes / alliance in the service layer)</li>
 *   <li>Departure / arrival time-range buckets (MORNING, AFTERNOON, EVENING, NIGHT)</li>
 *   <li>Maximum flight duration in minutes</li>
 * </ul>
 *
 * <h3>What this class does NOT handle</h3>
 * <ul>
 *   <li><b>Price filtering</b> – fare data lives in pricing-service; handled in
 *       the service layer via a single batch Feign call after the DB query.</li>
 *   <li><b>Per-cabin-class seat count</b> – FlightInstanceCabin lives in
 *       seat-service; the {@code availableSeats} guard here is aggregate.</li>
 * </ul>
 *
 * <p><b>DB assumption:</b> {@code TIMESTAMPDIFF} and {@code HOUR} are MySQL functions.
 * For PostgreSQL use {@code EXTRACT(EPOCH FROM age(...))/60} and
 * {@code EXTRACT(HOUR FROM ...)}.
 */
public class FlightInstanceSpecification {

    private static final Set<FlightStatus> EXCLUDED_STATUSES = Set.of(
            FlightStatus.CANCELLED,
            FlightStatus.COMPLETED,
            FlightStatus.DIVERTED
    );

    private FlightInstanceSpecification() {}

    /**
     * Builds a composite {@link Specification} from the search request.
     *
     * @param request            search criteria – all fields null-safe / optional

     */
    public static Specification<FlightInstance> buildSearchSpec(
            FlightSearchRequest request) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            /*
             * Eagerly fetch the Flight association so the mapper can read
             * flight.flightNumber without an extra SELECT per row.
             * Skip for count queries (Long return type) that Spring Data issues
             * internally for pagination totals.
             */
//            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
//                root.fetch("flight", JoinType.LEFT);
//            }

            // ── Always-on real-world rules ───────────────────────────────────

            // 1. Only active instances
            predicates.add(cb.isTrue(root.get("isActive")));

            // 2. Exclude terminal statuses
            predicates.add(root.get("status").in(EXCLUDED_STATUSES).not());

            // 3. Flight must still be in the future
            predicates.add(cb.greaterThan(root.get("departureDateTime"), LocalDateTime.now()));

            // 4. Origin airport
            predicates.add(cb.equal(root.get("departureAirportId"), request.getDepartureAirportId()));

            // 5. Destination airport
            predicates.add(cb.equal(root.get("arrivalAirportId"), request.getArrivalAirportId()));

            // ── Optional DB-level filters ────────────────────────────────────

            // 6. Departure date: entire calendar day (startOfDay … endOfDay inclusive)
            if (request.getDepartureDate() != null) {
                LocalDateTime startOfDay = request.getDepartureDate().atStartOfDay();
                LocalDateTime endOfDay   = request.getDepartureDate().atTime(LocalTime.MAX);
                predicates.add(cb.between(root.get("departureDateTime"), startOfDay, endOfDay));
            }

            // 7. Seat availability guard – total available seats >= passengers
            //    (per-cabin availability is checked at booking time via seat-service)
            if (request.getPassengers() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("availableSeats"), request.getPassengers()));
            }

            // 8. Airline IDs (optional – pass a non-empty list to restrict results)
            if (request.getAirlines() != null && !request.getAirlines().isEmpty()) {
                predicates.add(root.get("airlineId").in(request.getAirlines()));
            }

            // 9. Departure time-range bucket
            if (isFilterableTimeRange(request.getDepartureTimeRange())) {
                applyTimeRangePredicate(predicates, root, cb,
                        "departureDateTime", request.getDepartureTimeRange());
            }

            // 10. Arrival time-range bucket
            if (isFilterableTimeRange(request.getArrivalTimeRange())) {
                applyTimeRangePredicate(predicates, root, cb,
                        "arrivalDateTime", request.getArrivalTimeRange());
            }

            // 11. Maximum flight duration in minutes (MySQL TIMESTAMPDIFF)
            if (request.getMaxDuration() != null) {
                Expression<Integer> durationMinutes = cb.function(
                        "TIMESTAMPDIFF",
                        Integer.class,
                        cb.literal("MINUTE"),
                        root.get("departureDateTime"),
                        root.get("arrivalDateTime")
                );
                predicates.add(cb.lessThanOrEqualTo(durationMinutes, request.getMaxDuration()));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static boolean isFilterableTimeRange(String range) {
        return range != null && !range.isBlank() && !range.equalsIgnoreCase("any");
    }

    /**
     * Restricts the hour-of-day of a datetime column to a named bucket.
     *
     * <pre>
     * morning   →  06:00 – 11:59
     * afternoon →  12:00 – 17:59
     * evening   →  18:00 – 20:59
     * night     →  21:00 – 05:59
     * </pre>
     */
    private static void applyTimeRangePredicate(
            List<Predicate> predicates,
            Root<FlightInstance> root,
            CriteriaBuilder cb,
            String dateTimeField,
            String timeRange) {

        Expression<Integer> hour = cb.function("HOUR", Integer.class, root.get(dateTimeField));

        switch (timeRange.toLowerCase()) {
            case "morning"   -> predicates.add(cb.between(hour, 6,  11));
            case "afternoon" -> predicates.add(cb.between(hour, 12, 17));
            case "evening"   -> predicates.add(cb.between(hour, 18, 20));
            case "night"     -> predicates.add(cb.or(
                    cb.greaterThanOrEqualTo(hour, 21),
                    cb.lessThanOrEqualTo(hour, 5)));
            default          -> { /* unknown bucket → no-op */ }
        }
    }
}
