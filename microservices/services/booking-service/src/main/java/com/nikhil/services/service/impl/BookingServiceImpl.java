package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.BookingStatus;
import com.nikhil.common_lib.enums.PaymentGateway;
import com.nikhil.common_lib.exception.PaymentException;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
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


    @Override
    @Transactional
    public PaymentInitiateResponse createBooking(BookingRequest request, Long userId)
            throws ResourceNotFoundException, PaymentException {
        log.info("Creating booking for user: {}", userId);

        // Generate unique booking reference
        String bookingReference = generateBookingReference();

        // Create passenger entities
        Set<Passenger> passengers = new HashSet<>();
        for (PassengerRequest passengerRequest : request.getPassengers()) {
            Passenger passenger = passengerService
                    .findOrCreatePassengerEntity(passengerRequest, userId);
            passengers.add(passenger);
        }

        // check flightExist
        FlightResponse flightResponse = flightClient.getFlightById(request.getFlightId());

        // Create booking entity with cross-service IDs
        Booking booking = BookingMapper.toEntity(
                request, userId, passengers, bookingReference);
        booking.setStatus(BookingStatus.PENDING);
        booking.setAirlineId(flightResponse.getAirline().getId());

        // Set seat instance IDs from passenger requests
        List<Long> seatInstanceIds = request.getPassengers().stream()
                .map(PassengerRequest::getSeatInstanceId)
                .collect(Collectors.toList());
        booking.setSeatInstanceIds(seatInstanceIds);

        // Save booking
        booking = bookingRepository.save(booking);

        // Set booking reference on passengers
        for (Passenger passenger : passengers) {
            passenger.setBooking(booking);
        }

        // Generate tickets
        ticketService.generateTicketsForBooking(booking);

        // Calculate total amount

        int passengerCount = booking.getPassengers().size();
        Double fareTotal = pricingIntegrationService.calculateFareTotal(booking.getFareId()) * passengerCount;
        Double seatPrice= seatClient.calculateSeatPrice(booking.getSeatInstanceIds());
        Double ancillaryPrice=ancillaryClient.calculateAncillariesPrice(booking.getAncillaryIds());
        Double mealPrice=ancillaryClient.calculateMealPrice(booking.getMealIds());

        Double totalPrice=fareTotal+seatPrice+ancillaryPrice+mealPrice;


        // Initiate payment
        PaymentInitiateRequest paymentRequest = PaymentInitiateRequest.builder()
                .userId(userId)
                .bookingId(booking.getId())
                .amount(totalPrice)
                .gateway(PaymentGateway.RAZORPAY)
                .description("Booking: " + bookingReference)
                .build();

        System.out.println("Booking created successfully: {}   - "+paymentRequest);

        PaymentInitiateResponse paymentInit = paymentClient.initiatePayment(paymentRequest, userId);
        if (paymentInit == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment service is temporarily unavailable. Please retry.");
        }
        return paymentInit;
    }

    @Override
    @Transactional
    public BookingResponse updateBooking(Long id, BookingRequest request)
            throws ResourceNotFoundException {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found with ID: " + id));

        Set<Passenger> passengers = new HashSet<>();
        for (PassengerRequest passengerRequest : request.getPassengers()) {
            Passenger passenger = passengerService.findOrCreatePassengerEntity(
                    passengerRequest, booking.getUserId());
            passengers.add(passenger);
        }

        BookingMapper.updateEntityFromRequest(request, booking, passengers);
        Booking updated = bookingRepository.save(booking);
        return convertBookingResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long id) throws ResourceNotFoundException {
        Booking booking = bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found with ID: " + id));
        return convertBookingResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByAirline(
            Long userId,
            String searchQuery,
            BookingStatus status,
            Long flightInstanceId,
            String sortDirection
    ) {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);
        Long airlineId = airlineResponse.getId();

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, "bookingDate");

        List<Booking> bookings = bookingRepository.findByAirlineWithFilters(
                airlineId, searchQuery, status, flightInstanceId, sort);

        return enrichBatch(bookings);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserId(userId).stream()
                .map(this::convertBookingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long id) throws ResourceNotFoundException {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found with ID: " + id));

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setLastModified(LocalDateTime.now());
        Booking updated = bookingRepository.save(booking);

        log.info("Booking cancelled: {}", booking.getBookingReference());
        return convertBookingResponse(updated);
    }

    @Override
    @Transactional
    public void deleteBooking(Long id) throws ResourceNotFoundException {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found with ID: " + id));
        bookingRepository.delete(booking);
        log.info("Booking deleted: {}", booking.getBookingReference());
    }

    @Override
    public boolean existsById(Long id) {
        return bookingRepository.existsById(id);
    }

    @Override
    public long count() {
        return bookingRepository.count();
    }

    @Override
    public long countByFlightId(Long flightId) {
        return bookingRepository.countByFlightInstanceId(flightId);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingStatisticsResponse getBookingStatisticsForAirline(Long airlineId) {
        LocalDateTime now = LocalDateTime.now();

        // Today's statistics
        LocalDateTime startOfDay = now.with(LocalTime.MIN);
        LocalDateTime endOfDay = now.with(LocalTime.MAX);

        // This month's statistics
        YearMonth currentMonth = YearMonth.now();
        LocalDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay();
        LocalDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);

        // Note: In the microservice, airlineId -> flightId mapping is needed via inter-service call
        // For now, we provide basic statistics
        Long todayBookings = bookingRepository.count(); // Simplified
        Double todayRevenue = 0.0;
        Long monthBookings = bookingRepository.count();
        Double monthRevenue = 0.0;

        // Build daily trend (last 30 days)
        List<BookingStatisticsResponse.DailyBookingData> dailyTrend = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (int i = 29; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dailyTrend.add(BookingStatisticsResponse.DailyBookingData.builder()
                    .date(date.format(dateFormatter))
                    .bookingCount(0L)
                    .revenue(0.0)
                    .build());
        }

        // Build monthly data (last 12 months)
        List<BookingStatisticsResponse.MonthlyData> monthlyData = new ArrayList<>();
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 11; i >= 0; i--) {
            YearMonth month = YearMonth.now().minusMonths(i);
            monthlyData.add(BookingStatisticsResponse.MonthlyData.builder()
                    .month(month.format(monthFormatter))
                    .bookingCount(0L)
                    .revenue(0.0)
                    .build());
        }

        return BookingStatisticsResponse.builder()
                .totalBookingsToday(todayBookings)
                .revenueToday(todayRevenue)
                .totalBookingsThisMonth(monthBookings)
                .revenueThisMonth(monthRevenue)
                .dailyTrend(dailyTrend)
                .monthlyData(monthlyData)
                .build();
    }

    private String generateBookingReference() {
        String reference;
        do {
            reference = "BK" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase();
        } while (bookingRepository.findByBookingReference(reference).isPresent());
        return reference;
    }

    private List<BookingResponse> enrichBatch(List<Booking> bookings) {
        if (bookings.isEmpty()) return Collections.emptyList();

        List<Long> bookingIds = bookings.stream().map(Booking::getId).collect(Collectors.toList());

        List<Long> fareIds = bookings.stream().map(Booking::getFareId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> flightIds = bookings.stream().map(Booking::getFlightId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> flightInstanceIds = bookings.stream().map(Booking::getFlightInstanceId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> allSeatIds = bookings.stream()
                .flatMap(b -> b.getSeatInstanceIds() != null ? b.getSeatInstanceIds().stream() : Stream.empty())
                .distinct().collect(Collectors.toList());
        List<Long> allAncillaryIds = bookings.stream()
                .flatMap(b -> b.getAncillaryIds() != null ? b.getAncillaryIds().stream() : Stream.empty())
                .distinct().collect(Collectors.toList());
        List<Long> allMealIds = bookings.stream()
                .flatMap(b -> b.getMealIds() != null ? b.getMealIds().stream() : Stream.empty())
                .distinct().collect(Collectors.toList());

        // One call per service
        Map<Long, FareResponse> fareMap = pricingClient.getFaresByIds(fareIds);
        Map<Long, FlightResponse> flightMap = flightClient.getFlightsByIds(flightIds);
        Map<Long, FlightInstanceResponse> flightInstanceMap = flightClient.getFlightInstancesByIds(flightInstanceIds);
        Map<Long, PaymentDTO> paymentMap = paymentClient.getPaymentsByBookingIds(bookingIds);

        Map<Long, SeatInstanceResponse> seatMap = allSeatIds.isEmpty()
                ? Collections.emptyMap()
                : seatClient.getAllByIds(allSeatIds).stream()
                        .collect(Collectors.toMap(SeatInstanceResponse::getId, s -> s));
        Map<Long, FlightCabinAncillaryResponse> ancillaryMap = allAncillaryIds.isEmpty()
                ? Collections.emptyMap()
                : ancillaryClient.getAllByIds(allAncillaryIds).stream()
                        .collect(Collectors.toMap(FlightCabinAncillaryResponse::getId, a -> a));
        Map<Long, FlightMealResponse> mealMap = allMealIds.isEmpty()
                ? Collections.emptyMap()
                : ancillaryClient.getMealsByIds(allMealIds).stream()
                        .collect(Collectors.toMap(FlightMealResponse::getId, m -> m));

        return bookings.stream().map(booking -> {
            List<SeatInstanceResponse> seats = booking.getSeatInstanceIds() != null
                    ? booking.getSeatInstanceIds().stream()
                            .map(seatMap::get).filter(Objects::nonNull).collect(Collectors.toList())
                    : Collections.emptyList();
            List<FlightCabinAncillaryResponse> ancillaries = booking.getAncillaryIds() != null
                    ? booking.getAncillaryIds().stream()
                            .map(ancillaryMap::get).filter(Objects::nonNull).collect(Collectors.toList())
                    : Collections.emptyList();
            List<FlightMealResponse> meals = booking.getMealIds() != null
                    ? booking.getMealIds().stream()
                            .map(mealMap::get).filter(Objects::nonNull).collect(Collectors.toList())
                    : Collections.emptyList();

            return BookingMapper.toResponse(
                    booking,
                    paymentMap.get(booking.getId()),
                    fareMap.get(booking.getFareId()),
                    flightMap.get(booking.getFlightId()),
                    flightInstanceMap.get(booking.getFlightInstanceId()),
                    ancillaries,
                    meals,
                    seats
            );
        }).collect(Collectors.toList());
    }

    private BookingResponse convertBookingResponse(Booking booking)  {
        List<FlightCabinAncillaryResponse> ancillaryResponses=ancillaryClient.getAllByIds(
                booking.getAncillaryIds()
        );
        List<FlightMealResponse> mealResponses=ancillaryClient.getMealsByIds(booking.getMealIds());
        PaymentDTO paymentDTO=paymentClient.getPaymentByBookingId(booking.getId());
        FareResponse fareResponse=pricingClient.getFareById(booking.getFareId());
        FlightResponse flightResponse=flightClient.getFlightById(booking.getFlightId());

        List<SeatInstanceResponse> seatInstanceResponses=seatClient.getAllByIds(booking.getSeatInstanceIds());
        FlightInstanceResponse flightInstanceResponse=flightClient.getFlightInstanceResponse(booking.getFlightInstanceId());


        System.out.println("seat instances -------- "+seatInstanceResponses.size());

        return BookingMapper.toResponse(booking,
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
