package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.enums.PaymentGateway;
import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.BookingRequest;
import com.nikhil.common_lib.payload.request.PassengerRequest;
import com.nikhil.common_lib.payload.request.PaymentInitiateRequest;
import com.nikhil.common_lib.payload.response.*;
import com.nikhil.services.clients.*;
import com.nikhil.services.integration.PricingIntegrationService;
import com.nikhil.services.mapper.BookingMapper;
import com.nikhil.services.model.Booking;
import com.nikhil.services.model.Passenger;
import com.nikhil.services.repository.BookingRepository;
import com.nikhil.services.service.BookingService;
import com.nikhil.services.service.PassengerService;
import com.nikhil.services.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core booking orchestration service.
 *
 * Manages local Booking persistence and coordinates with downstream services
 * for flight validation, fare pricing, seat pricing, ancillary pricing,
 * meal pricing, and payment initialization.
 *
 * Booking creation flow:
 *
 * BookingRequest
 *      ↓
 * Validate Flight
 *      ↓
 * Resolve/Create Passengers
 *      ↓
 * Create PENDING Booking
 *      ↓
 * Generate Tickets
 *      ↓
 * Calculate Fare + Seat + Ancillary + Meal totals
 *      ↓
 * Payment Service
 *      ↓
 * Return payment checkout information
 *
 * Payment confirmation is handled asynchronously:
 *
 * payment.completed
 *      ↓
 * PaymentEventListener
 *      ↓
 * Booking → CONFIRMED
 *      ↓
 * booking.confirmed
 *      ↓
 * Seat Service + Notification Service
 *
 * Read operations use batch downstream calls where possible to avoid
 * N+1 remote service requests.
 *
 * Transaction strategy:
 *   - Class-level read-only transaction for query operations.
 *   - Explicit writable transactions for create, update, cancel, and delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final PassengerService passengerService;
    private final TicketService ticketService;
    private final PricingIntegrationService pricingIntegrationService;
    private final PricingClient pricingClient;
    private final AncillaryClient ancillaryClient;
    private final PaymentClient paymentClient;
    private final SeatClient seatClient;
    private final FlightClient flightClient;
    private final AirlineClient airlineClient;


    /**
     * Creates a PENDING booking and initializes payment.
     *
     * The booking remains PENDING until payment-service publishes the
     * payment completion event and the booking event listener confirms it.
     */
    @Override
    @Transactional
    public PaymentInitiateResponse createBooking(
            BookingRequest request,
            Long userId
    ) {

        log.info(
                "Starting booking creation userId={} flightId={} flightInstanceId={} fareId={}",
                userId,
                request.getFlightId(),
                request.getFlightInstanceId(),
                request.getFareId()
        );

        /*
         * Generate the public booking reference used by customers to identify
         * and track the Booking.
         *
         * Example:
         *
         *     Internal database ID : 125
         *     Public reference     : BK7A3F91C285AD4EA1
         *
         * The generator does not query the database before returning the value.
         * Uniqueness is enforced by the UNIQUE constraint on booking_reference.
         */
        String bookingReference =
                generateBookingReference();

        log.debug(
                "Generated booking reference bookingReference={} userId={}",
                bookingReference,
                userId
        );

        /*
         * Resolve the Passenger records participating in the Booking.
         *
         * For every traveller in the request, PassengerService either finds the
         * corresponding existing Passenger or creates a new Passenger entity.
         */
        Set<Passenger> passengers =
                new HashSet<>();

        for (PassengerRequest passengerRequest : request.getPassengers()) {

            Passenger passenger =
                    passengerService.findOrCreatePassengerEntity(
                            passengerRequest,
                            userId
                    );

            passengers.add(passenger);
        }

        log.debug(
                "Passengers resolved bookingReference={} passengerCount={}",
                bookingReference,
                passengers.size()
        );

        /*
         * Validate the requested Flight and obtain its owning Airline.
         *
         * Flight belongs to flight-ops-service, therefore Booking Service does
         * not maintain a JPA relationship with the Flight entity. Validation and
         * data retrieval are performed through FlightClient.
         */
        FlightResponse flightResponse =
                flightClient.getFlightById(
                        request.getFlightId()
                );

        log.debug(
                "Flight validated bookingReference={} flightId={} airlineId={}",
                bookingReference,
                request.getFlightId(),
                flightResponse.getAirline().getId()
        );

        /*
         * Build the local Booking aggregate.
         *
         * The Booking is initially created with PENDING status. Payment success
         * is processed asynchronously, after which the Booking can transition
         * to CONFIRMED.
         */
        Booking booking =
                BookingMapper.toEntity(
                        request,
                        userId,
                        passengers,
                        bookingReference
                );

        booking.setStatus(
                BookingStatus.PENDING
        );

        booking.setAirlineId(
                flightResponse.getAirline().getId()
        );

        /*
         * Extract the selected SeatInstance IDs from Passenger requests.
         *
         * SeatInstance belongs to seat-service, therefore Booking Service stores
         * only the external identifiers instead of creating cross-service JPA
         * relationships.
         *
         * null seat IDs are ignored because a Passenger may not have selected
         * a seat at booking creation time.
         */
        List<Long> seatInstanceIds =
                request.getPassengers()
                        .stream()
                        .map(PassengerRequest::getSeatInstanceId)
                        .filter(Objects::nonNull)
                        .toList();

        booking.setSeatInstanceIds(
                seatInstanceIds
        );

        /*
         * Persist the initial Booking.
         *
         * The booking_reference column must have a UNIQUE database constraint.
         * This constraint is the final concurrency-safe protection against the
         * extremely unlikely event of two requests generating the same reference.
         */
        booking =
                bookingRepository.save(booking);

        log.info(
                "Pending booking persisted bookingId={} bookingReference={} userId={}",
                booking.getId(),
                booking.getBookingReference(),
                userId
        );

        /*
         * Associate all resolved Passenger entities with the persisted Booking.
         *
         * The Booking must be persisted first because Passenger records require
         * the Booking relationship and its generated identifier.
         */
        for (Passenger passenger : passengers) {

            passenger.setBooking(
                    booking
            );
        }

        /*
         * Generate one local Ticket record for each Passenger associated with
         * the Booking.
         *
         * Ticket generation occurs before payment confirmation, while the parent
         * Booking remains in PENDING state.
         */
        ticketService.generateTicketsForBooking(
                booking
        );

        log.debug(
                "Tickets generated bookingId={} bookingReference={} passengerCount={}",
                booking.getId(),
                booking.getBookingReference(),
                passengers.size()
        );

        /*
         * Calculate the fare component of the Booking.
         *
         * The pricing service returns the fare amount for one Passenger.
         * Therefore:
         *
         *     fareTotal = farePerPassenger × passengerCount
         */
        int passengerCount =
                passengers.size();

        double farePerPassenger =
                pricingIntegrationService.calculateFareTotal(
                        booking.getFareId()
                );

        double fareTotal =
                farePerPassenger * passengerCount;

        log.debug(
                "Fare price calculated bookingId={} fareId={} farePerPassenger={} passengerCount={} fareTotal={}",
                booking.getId(),
                booking.getFareId(),
                farePerPassenger,
                passengerCount,
                fareTotal
        );

        /*
         * Calculate the total price of selected SeatInstances.
         *
         * The remote Seat Service call is skipped when no seats were selected.
         * This prevents unnecessary network calls and avoids requests containing
         * an empty ID collection.
         */
        double seatPrice =
                booking.getSeatInstanceIds() == null
                        || booking.getSeatInstanceIds().isEmpty()
                        ? 0.0
                        : seatClient.calculateSeatPrice(
                        booking.getSeatInstanceIds()
                );

        /*
         * Calculate the total price of selected ancillary products.
         *
         * Examples include:
         *   - Additional baggage
         *   - Travel protection
         *   - Lounge-related services
         *
         * The remote call is skipped when no ancillary products were selected.
         */
        double ancillaryPrice =
                booking.getAncillaryIds() == null
                        || booking.getAncillaryIds().isEmpty()
                        ? 0.0
                        : ancillaryClient.calculateAncillariesPrice(
                        booking.getAncillaryIds()
                );

        /*
         * Calculate the total price of selected flight-specific Meal offerings.
         *
         * Meal IDs stored on Booking represent FlightMeal records because pricing
         * is configured at the flight-meal assignment level rather than directly
         * on the catalog Meal entity.
         */
        double mealPrice =
                booking.getMealIds() == null
                        || booking.getMealIds().isEmpty()
                        ? 0.0
                        : ancillaryClient.calculateMealPrice(
                        booking.getMealIds()
                );

        /*
         * Calculate the complete amount that must be paid for the Booking.
         *
         * Formula:
         *
         *     Total Amount
         *          =
         *     Fare Total
         *          +
         *     Seat Selection Total
         *          +
         *     Ancillary Total
         *          +
         *     Meal Total
         */
        double totalPrice =
                fareTotal
                        + seatPrice
                        + ancillaryPrice
                        + mealPrice;

        log.info(
                "Booking price calculated bookingId={} fareTotal={} seatTotal={} ancillaryTotal={} mealTotal={} totalAmount={}",
                booking.getId(),
                fareTotal,
                seatPrice,
                ancillaryPrice,
                mealPrice,
                totalPrice
        );

        /*
         * Build the payment initialization request.
         *
         * Booking Service sends only the information required by Payment Service:
         *
         *   - authenticated user
         *   - local Booking ID
         *   - payable amount
         *   - selected payment gateway
         *   - payment description
         *
         * The Booking remains PENDING while payment is in progress.
         */
        PaymentInitiateRequest paymentRequest =
                PaymentInitiateRequest.builder()
                        .userId(userId)
                        .bookingId(booking.getId())
                        .amount(totalPrice)
                        .gateway(PaymentGateway.RAZORPAY)
                        .description(
                                "Booking: "
                                        + booking.getBookingReference()
                        )
                        .build();

        log.info(
                "Initiating payment bookingId={} bookingReference={} gateway={} amount={}",
                booking.getId(),
                booking.getBookingReference(),
                PaymentGateway.RAZORPAY,
                totalPrice
        );

        /*
         * Call Payment Service to create the payment transaction and initialize
         * the external payment gateway checkout flow.
         *
         * Payment confirmation itself is not handled synchronously here.
         * The final Booking status transition is driven by the payment completion
         * event received through the asynchronous event flow.
         */
        PaymentInitiateResponse paymentResponse =
                paymentClient.initiatePayment(
                        paymentRequest,
                        userId
                );

        /*
         * Protect the API from returning a successful booking-creation response
         * when Payment Service did not return a usable initialization result.
         */
        if (paymentResponse == null) {

            log.error(
                    "Payment initialization returned null bookingId={} bookingReference={}",
                    booking.getId(),
                    booking.getBookingReference()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment service is temporarily unavailable. Please retry."
            );
        }

        log.info(
                "Booking creation flow completed bookingId={} bookingReference={} userId={} totalAmount={}",
                booking.getId(),
                booking.getBookingReference(),
                userId,
                totalPrice
        );

        return paymentResponse;
    }


    /**
     * Updates an existing Booking and its passenger association.
     */
    @Override
    @Transactional
    public BookingResponse updateBooking(
            Long id,
            BookingRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Updating booking bookingId={}",
                id
        );

        Booking booking =
                bookingRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot update booking because record was not found bookingId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Booking not found with ID: " + id
                            );
                        });

        Set<Passenger> passengers =
                new HashSet<>();

        for (PassengerRequest passengerRequest : request.getPassengers()) {

            Passenger passenger =
                    passengerService.findOrCreatePassengerEntity(
                            passengerRequest,
                            booking.getUserId()
                    );

            passengers.add(passenger);
        }

        BookingMapper.updateEntityFromRequest(
                request,
                booking,
                passengers
        );

        Booking updated =
                bookingRepository.save(booking);

        log.info(
                "Booking updated successfully bookingId={} bookingReference={}",
                updated.getId(),
                updated.getBookingReference()
        );

        return convertBookingResponse(updated);
    }


    /**
     * Retrieves a single Booking with its locally persisted relationships and
     * enriches the response with downstream service data.
     */
    @Override
    public BookingResponse getBookingById(
            Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Fetching booking bookingId={}",
                id
        );

        Booking booking =
                bookingRepository.findByIdWithDetails(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Booking not found bookingId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Booking not found with ID: " + id
                            );
                        });

        BookingResponse response =
                convertBookingResponse(booking);

        log.debug(
                "Booking fetched successfully bookingId={} bookingReference={}",
                booking.getId(),
                booking.getBookingReference()
        );

        return response;
    }


    /**
     * Retrieves bookings belonging to the airline owned by the authenticated
     * user and applies optional search, status, flight-instance, and sorting
     * filters.
     *
     * Results are batch-enriched to reduce downstream service calls.
     */
    @Override
    public List<BookingResponse> getBookingsByAirline(
            Long userId,
            String searchQuery,
            BookingStatus status,
            Long flightInstanceId,
            String sortDirection
    ) {

        log.debug(
                "Fetching airline bookings userId={} status={} flightInstanceId={} sortDirection={} searchProvided={}",
                userId,
                status,
                flightInstanceId,
                sortDirection,
                searchQuery != null && !searchQuery.isBlank()
        );

        AirlineResponse airlineResponse =
                airlineClient.getAirlineByOwner(userId);

        Long airlineId =
                airlineResponse.getId();

        Sort.Direction direction =
                "asc".equalsIgnoreCase(sortDirection)
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Sort sort =
                Sort.by(direction, "bookingDate");

        List<Booking> bookings =
                bookingRepository.findByAirlineWithFilters(
                        airlineId,
                        searchQuery,
                        status,
                        flightInstanceId,
                        sort
                );

        log.debug(
                "Airline bookings retrieved from database airlineId={} count={}",
                airlineId,
                bookings.size()
        );

        List<BookingResponse> responses =
                enrichBatch(bookings);

        log.debug(
                "Airline bookings enriched successfully airlineId={} resultCount={}",
                airlineId,
                responses.size()
        );

        return responses;
    }


    /**
     * Retrieves the booking history of a user.
     */
    @Override
    public List<BookingResponse> getBookingsByUser(
            Long userId
    ) {

        log.debug(
                "Fetching booking history userId={}",
                userId
        );

        List<Booking> bookings =
                bookingRepository.findByUserId(userId);

        log.debug(
                "User bookings retrieved userId={} count={}",
                userId,
                bookings.size()
        );

        List<BookingResponse> responses =
                bookings.stream()
                        .map(this::convertBookingResponse)
                        .toList();

        log.debug(
                "Booking history enriched successfully userId={} resultCount={}",
                userId,
                responses.size()
        );

        return responses;
    }


    /**
     * Changes the local Booking status to CANCELLED.
     *
     * This method currently performs only the local status transition.
     * Refund processing, seat release, and cancellation event publication
     * must be handled separately if required by the cancellation workflow.
     */
    @Override
    @Transactional
    public BookingResponse cancelBooking(
            Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Cancelling booking bookingId={}",
                id
        );

        Booking booking =
                bookingRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot cancel booking because record was not found bookingId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Booking not found with ID: " + id
                            );
                        });

        if (BookingStatus.CANCELLED.equals(booking.getStatus())) {

            log.warn(
                    "Booking is already cancelled bookingId={} bookingReference={}",
                    booking.getId(),
                    booking.getBookingReference()
            );

            return convertBookingResponse(booking);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setLastModified(LocalDateTime.now());

        Booking updated =
                bookingRepository.save(booking);

        log.info(
                "Booking cancelled successfully bookingId={} bookingReference={}",
                updated.getId(),
                updated.getBookingReference()
        );

        return convertBookingResponse(updated);
    }


    /**
     * Permanently deletes a Booking record.
     *
     * Hard deletion should be restricted to valid operational use cases
     * because completed booking records are commonly retained for audit,
     * reconciliation, and reporting.
     */
    @Override
    @Transactional
    public void deleteBooking(
            Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Deleting booking bookingId={}",
                id
        );

        Booking booking =
                bookingRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cannot delete booking because record was not found bookingId={}",
                                    id
                            );

                            return new ResourceNotFoundException(
                                    "Booking not found with ID: " + id
                            );
                        });

        bookingRepository.delete(booking);

        log.info(
                "Booking deleted successfully bookingId={} bookingReference={}",
                booking.getId(),
                booking.getBookingReference()
        );
    }


    /**
     * Checks whether a Booking exists for the supplied identifier.
     */
    @Override
    public boolean existsById(Long id) {

        log.debug(
                "Checking booking existence bookingId={}",
                id
        );

        return bookingRepository.existsById(id);
    }


    /**
     * Returns the total number of Booking records.
     */
    @Override
    public long count() {

        log.debug(
                "Counting all bookings"
        );

        long count =
                bookingRepository.count();

        log.debug(
                "Booking count completed count={}",
                count
        );

        return count;
    }


    /**
     * Returns the number of bookings associated with a FlightInstance.
     *
     * Note that the repository method currently counts by flightInstanceId,
     * even though the service method parameter is named flightId.
     */
    @Override
    public long countByFlightId(
            Long flightId
    ) {

        log.debug(
                "Counting bookings for flight instance identifier={}",
                flightId
        );

        long count =
                bookingRepository.countByFlightInstanceId(flightId);

        log.debug(
                "Booking count completed flightInstanceId={} count={}",
                flightId,
                count
        );

        return count;
    }


    /**
     * Builds booking statistics for an airline.
     *
     * The current implementation contains placeholder revenue and trend data.
     * Repository-level aggregate queries should replace the simplified values
     * when production analytics are implemented.
     */
    @Override
    public BookingStatisticsResponse getBookingStatisticsForAirline(
            Long airlineId
    ) {

        log.debug(
                "Calculating booking statistics airlineId={}",
                airlineId
        );

        LocalDateTime now =
                LocalDateTime.now();

        LocalDateTime startOfDay =
                now.with(LocalTime.MIN);

        LocalDateTime endOfDay =
                now.with(LocalTime.MAX);

        YearMonth currentMonth =
                YearMonth.now();

        LocalDateTime startOfMonth =
                currentMonth.atDay(1).atStartOfDay();

        LocalDateTime endOfMonth =
                currentMonth
                        .atEndOfMonth()
                        .atTime(LocalTime.MAX);

        /*
         * Temporary aggregate implementation.
         *
         * These values currently use global Booking counts and placeholder
         * revenue values. Replace them with airline-scoped aggregate queries
         * before using this endpoint for production analytics.
         */
        Long todayBookings =
                bookingRepository.count();

        Double todayRevenue =
                0.0;

        Long monthBookings =
                bookingRepository.count();

        Double monthRevenue =
                0.0;

        /*
         * Build the daily trend structure for the previous 30 days.
         * Counts and revenue are currently placeholders.
         */
        List<BookingStatisticsResponse.DailyBookingData> dailyTrend =
                new ArrayList<>();

        DateTimeFormatter dateFormatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 29; i >= 0; i--) {

            LocalDate date =
                    LocalDate.now().minusDays(i);

            dailyTrend.add(
                    BookingStatisticsResponse.DailyBookingData.builder()
                            .date(date.format(dateFormatter))
                            .bookingCount(0L)
                            .revenue(0.0)
                            .build()
            );
        }

        /*
         * Build the monthly trend structure for the previous 12 months.
         * Counts and revenue are currently placeholders.
         */
        List<BookingStatisticsResponse.MonthlyData> monthlyData =
                new ArrayList<>();

        DateTimeFormatter monthFormatter =
                DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 11; i >= 0; i--) {

            YearMonth month =
                    YearMonth.now().minusMonths(i);

            monthlyData.add(
                    BookingStatisticsResponse.MonthlyData.builder()
                            .month(month.format(monthFormatter))
                            .bookingCount(0L)
                            .revenue(0.0)
                            .build()
            );
        }

        BookingStatisticsResponse response =
                BookingStatisticsResponse.builder()
                        .totalBookingsToday(todayBookings)
                        .revenueToday(todayRevenue)
                        .totalBookingsThisMonth(monthBookings)
                        .revenueThisMonth(monthRevenue)
                        .dailyTrend(dailyTrend)
                        .monthlyData(monthlyData)
                        .build();

        log.debug(
                "Booking statistics calculated airlineId={} todayBookings={} monthBookings={}",
                airlineId,
                todayBookings,
                monthBookings
        );

        return response;
    }


    /**
     * Generates a customer-facing reference for a Booking.
     *
     * Format:
     *
     *     BK + 16 random hexadecimal characters
     *
     * Example:
     *
     *     BK7A3F91C285AD4EA1
     *
     * Generation flow:
     *
     * UUID:
     *
     *     7a3f91c2-85ad-4ea1-a937-14f08a4de922
     *
     * Remove separators:
     *
     *     7a3f91c285ad4ea1a93714f08a4de922
     *
     * Keep the first 16 hexadecimal characters:
     *
     *     7a3f91c285ad4ea1
     *
     * Convert to uppercase:
     *
     *     7A3F91C285AD4EA1
     *
     * Add the Booking prefix:
     *
     *     BK7A3F91C285AD4EA1
     *
     * No database existence query is performed here. The generated value has
     * 64 bits of random space, making accidental collisions extremely unlikely
     * at ordinary booking volumes.
     *
     * The database UNIQUE constraint on booking_reference remains the final
     * concurrency-safe guarantee that duplicate references cannot be stored.
     */
    private String generateBookingReference() {

        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);

        return "BK" + randomPart;
    }


    /**
     * Batch-enriches multiple Booking records using one downstream request
     * per service where supported.
     *
     * This avoids making payment, pricing, flight, seat, ancillary, and meal
     * requests separately for every Booking in an airline dashboard result.
     */
    private List<BookingResponse> enrichBatch(
            List<Booking> bookings
    ) {

        if (bookings.isEmpty()) {

            log.debug(
                    "Skipping booking batch enrichment because input is empty"
            );

            return Collections.emptyList();
        }

        log.debug(
                "Starting batch booking enrichment bookingCount={}",
                bookings.size()
        );

        List<Long> bookingIds =
                bookings.stream()
                        .map(Booking::getId)
                        .toList();

        List<Long> fareIds =
                bookings.stream()
                        .map(Booking::getFareId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        List<Long> flightIds =
                bookings.stream()
                        .map(Booking::getFlightId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        List<Long> flightInstanceIds =
                bookings.stream()
                        .map(Booking::getFlightInstanceId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        List<Long> allSeatIds =
                bookings.stream()
                        .flatMap(booking ->
                                booking.getSeatInstanceIds() != null
                                        ? booking.getSeatInstanceIds().stream()
                                        : Stream.empty()
                        )
                        .distinct()
                        .toList();

        List<Long> allAncillaryIds =
                bookings.stream()
                        .flatMap(booking ->
                                booking.getAncillaryIds() != null
                                        ? booking.getAncillaryIds().stream()
                                        : Stream.empty()
                        )
                        .distinct()
                        .toList();

        List<Long> allMealIds =
                bookings.stream()
                        .flatMap(booking ->
                                booking.getMealIds() != null
                                        ? booking.getMealIds().stream()
                                        : Stream.empty()
                        )
                        .distinct()
                        .toList();

        /*
         * Fetch shared downstream data in batches instead of making remote
         * calls once per Booking.
         */
        Map<Long, FareResponse> fareMap =
                fareIds.isEmpty()
                        ? Collections.emptyMap()
                        : pricingClient.getFaresByIds(fareIds);

        Map<Long, FlightResponse> flightMap =
                flightIds.isEmpty()
                        ? Collections.emptyMap()
                        : flightClient.getFlightsByIds(flightIds);

        Map<Long, FlightInstanceResponse> flightInstanceMap =
                flightInstanceIds.isEmpty()
                        ? Collections.emptyMap()
                        : flightClient.getFlightInstancesByIds(
                        flightInstanceIds
                );

        Map<Long, PaymentDTO> paymentMap =
                bookingIds.isEmpty()
                        ? Collections.emptyMap()
                        : paymentClient.getPaymentsByBookingIds(
                        bookingIds
                );

        Map<Long, SeatInstanceResponse> seatMap =
                allSeatIds.isEmpty()
                        ? Collections.emptyMap()
                        : seatClient.getAllByIds(allSeatIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SeatInstanceResponse::getId,
                                        seat -> seat
                                )
                        );

        Map<Long, FlightCabinAncillaryResponse> ancillaryMap =
                allAncillaryIds.isEmpty()
                        ? Collections.emptyMap()
                        : ancillaryClient.getAllByIds(allAncillaryIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        FlightCabinAncillaryResponse::getId,
                                        ancillary -> ancillary
                                )
                        );

        Map<Long, FlightMealResponse> mealMap =
                allMealIds.isEmpty()
                        ? Collections.emptyMap()
                        : ancillaryClient.getMealsByIds(allMealIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        FlightMealResponse::getId,
                                        meal -> meal
                                )
                        );

        List<BookingResponse> responses =
                bookings.stream()
                        .map(booking -> {

                            List<SeatInstanceResponse> seats =
                                    booking.getSeatInstanceIds() != null
                                            ? booking.getSeatInstanceIds()
                                            .stream()
                                            .map(seatMap::get)
                                            .filter(Objects::nonNull)
                                            .toList()
                                            : Collections.emptyList();

                            List<FlightCabinAncillaryResponse> ancillaries =
                                    booking.getAncillaryIds() != null
                                            ? booking.getAncillaryIds()
                                            .stream()
                                            .map(ancillaryMap::get)
                                            .filter(Objects::nonNull)
                                            .toList()
                                            : Collections.emptyList();

                            List<FlightMealResponse> meals =
                                    booking.getMealIds() != null
                                            ? booking.getMealIds()
                                            .stream()
                                            .map(mealMap::get)
                                            .filter(Objects::nonNull)
                                            .toList()
                                            : Collections.emptyList();

                            return BookingMapper.toResponse(
                                    booking,
                                    paymentMap.get(booking.getId()),
                                    fareMap.get(booking.getFareId()),
                                    flightMap.get(booking.getFlightId()),
                                    flightInstanceMap.get(
                                            booking.getFlightInstanceId()
                                    ),
                                    ancillaries,
                                    meals,
                                    seats
                            );
                        })
                        .toList();

        log.debug(
                "Batch booking enrichment completed bookingCount={} responseCount={}",
                bookings.size(),
                responses.size()
        );

        return responses;
    }


    /**
     * Enriches a single Booking with data owned by downstream services.
     *
     * Used for single-booking reads and mutation responses. Airline dashboard
     * queries use enrichBatch() instead to avoid N+1 remote calls.
     */
    private BookingResponse convertBookingResponse(
            Booking booking
    ) {

        log.debug(
                "Starting single booking enrichment bookingId={} bookingReference={}",
                booking.getId(),
                booking.getBookingReference()
        );

        List<FlightCabinAncillaryResponse> ancillaryResponses =
                booking.getAncillaryIds() == null
                        || booking.getAncillaryIds().isEmpty()
                        ? Collections.emptyList()
                        : ancillaryClient.getAllByIds(
                        booking.getAncillaryIds()
                );

        List<FlightMealResponse> mealResponses =
                booking.getMealIds() == null
                        || booking.getMealIds().isEmpty()
                        ? Collections.emptyList()
                        : ancillaryClient.getMealsByIds(
                        booking.getMealIds()
                );

        PaymentDTO paymentDTO =
                paymentClient.getPaymentByBookingId(
                        booking.getId()
                );

        FareResponse fareResponse =
                pricingClient.getFareById(
                        booking.getFareId()
                );

        FlightResponse flightResponse =
                flightClient.getFlightById(
                        booking.getFlightId()
                );

        List<SeatInstanceResponse> seatInstanceResponses =
                booking.getSeatInstanceIds() == null
                        || booking.getSeatInstanceIds().isEmpty()
                        ? Collections.emptyList()
                        : seatClient.getAllByIds(
                        booking.getSeatInstanceIds()
                );

        FlightInstanceResponse flightInstanceResponse =
                flightClient.getFlightInstanceResponse(
                        booking.getFlightInstanceId()
                );

        log.debug(
                "Single booking enrichment completed bookingId={} seats={} ancillaries={} meals={}",
                booking.getId(),
                seatInstanceResponses.size(),
                ancillaryResponses.size(),
                mealResponses.size()
        );

        return BookingMapper.toResponse(
                booking,
                paymentDTO,
                fareResponse,
                flightResponse,
                flightInstanceResponse,
                ancillaryResponses,
                mealResponses,
                seatInstanceResponses
        );
    }
}