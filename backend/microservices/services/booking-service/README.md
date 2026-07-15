# Booking Service

## Purpose

`booking-service` orchestrates customer bookings. It owns booking, passenger, and ticket records; coordinates flight validation, pricing, seats, ancillaries, meals, and payment initiation; and reacts to payment events.

## Architecture

- Controllers: `BookingController`, `TicketController`, `PassengerController`.
- `BookingServiceImpl` creates `PENDING` bookings, resolves passengers, generates tickets, calculates totals, and initiates payment.
- Feign clients call user, airline, flight, seat, pricing, ancillary, and payment services.
- `PaymentEventListener` consumes payment result events.
- `BookingEventProducer` publishes enriched booking confirmation events.

## APIs

- `POST /api/bookings`
- `PUT /api/bookings/{id}`
- `GET /api/bookings/{id}`
- `GET /api/bookings/airline`
- `GET /api/bookings/user/history`
- `PATCH /api/bookings/{id}/cancel`
- `DELETE /api/bookings/{id}`
- `GET /api/bookings/count/flight/{flightId}`
- `GET /api/bookings/statistics/airline`
- `GET /api/tickets/{ticketNumber}`
- `GET /api/tickets/booking/{bookingId}`
- `GET /api/tickets/passenger/{passengerId}`
- `PUT /api/tickets/{ticketId}/cancel`
- `PUT /api/tickets/{ticketId}/use`
- `PUT /api/tickets/{ticketId}/refund`

## Database

MySQL database: `airline_booking_db`. Entities: `Booking`, `Passenger`, `Ticket`. `Booking` has one-to-many relationships to passengers and tickets; external service references are stored as IDs.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, OpenFeign, Kafka, Resilience4j, validation, Actuator, Prometheus, Zipkin, Loki, datasource and Feign instrumentation, `common-lib`.

## Kafka Usage

Consumers:

- `payment.completed`, group `booking-service-group`: marks booking `CONFIRMED`, stores `paymentId`, enriches with Feign data, publishes `booking.confirmed`.
- `payment.failed`, group `booking-service-group`: marks booking `CANCELLED`.

Producer:

- `booking.confirmed`: consumed by `seat-service` and `notification-service`.

## Redis Usage

No Redis cache is configured in this service.

## Configuration

- Port: `5008`
- `spring.application.name=booking-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Kafka: `KAFKA_BOOTSTRAP_SERVERS`
- Eureka and observability endpoints are environment-driven.

## Observability

Actuator health/info/metrics/Prometheus, Loki logs, Zipkin/Tempo traces, datasource and Feign observations are configured.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-booking:1.0.0`. Requires MySQL, Kafka, Eureka, and all pricing/flight/seat/ancillary/payment dependencies for full booking creation.

Note: the airline statistics implementation currently includes placeholder revenue and trend values in code; do not use it for production financial reporting without completing aggregate queries.
