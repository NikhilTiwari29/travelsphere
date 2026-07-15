# Seat Service

## Purpose

`seat-service` owns aircraft cabin-class configuration, seat maps, template seats, runtime flight-instance cabins, and seat instance availability.

## Architecture

- Controllers: `CabinClassController`, `SeatMapController`, `SeatController`, `FlightInstanceCabinController`, `SeatInstanceController`.
- Static hierarchy: `CabinClass -> SeatMap -> Seat`.
- Runtime hierarchy: `FlightInstanceCabin -> SeatInstance`.
- Feign client calls `airline-core-service` for aircraft/airline context.
- Kafka consumers create seat inventory and mark seats booked.

## APIs

- `POST /api/cabin-classes`, `POST /api/cabin-classes/create/bulk`, `GET /api/cabin-classes/{id}`, `GET /api/cabin-classes/aircraft/{id}/name/{cabinClass}`, `GET /api/cabin-classes/aircraft/{aircraftId}`, `PUT /api/cabin-classes/{id}`, `DELETE /api/cabin-classes/{id}`
- `POST /api/seat-maps`, `POST /api/seat-maps/create/bulk`, `GET /api/seat-maps/{id}`, `GET /api/seat-maps/cabin-class/{cabinClassId}`, `PUT /api/seat-maps/{id}`, `DELETE /api/seat-maps/{id}`
- `GET /api/seats`, `GET /api/seats/{id}`, `PUT /api/seats/{id}`
- `POST /api/flight-instance-cabins`, `GET /api/flight-instance-cabins/{id}`, `GET /api/flight-instance-cabins/flight-instance/{flightInstanceId}`, `PUT /api/flight-instance-cabins/{id}`, `DELETE /api/flight-instance-cabins/{id}`
- `POST /api/seat-instances`, `GET /api/seat-instances/{id}`, `POST /api/seat-instances/price/total`, `GET /api/seat-instances/flight/{flightId}`, `GET /api/seat-instances/all`, `GET /api/seat-instances/flight/{flightId}/available`, `GET /api/seat-instances/flight/{flightId}/available/count`, `PATCH /api/seat-instances/{id}/status`

## Database

MySQL database: `airline_seat_db`. Entities: `CabinClass`, `SeatMap`, `Seat`, `FlightInstanceCabin`, `SeatInstance`.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, OpenFeign, Kafka, Resilience4j, validation, Actuator, Prometheus, Zipkin, Loki, datasource and Feign Micrometer instrumentation, `common-lib`.

## Kafka Usage

Consumers:

- `flight-instance-created`, group `seat-service-group`: creates `FlightInstanceCabin` and `SeatInstance` rows with `AVAILABLE` status.
- `booking.confirmed`, group `seat-service-group`: marks selected seat instances `BOOKED`.

## Redis Usage

No Redis cache is configured in this service.

## Configuration

- Port: `5005`
- `spring.application.name=seat-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Kafka: `KAFKA_BOOTSTRAP_SERVERS`
- Eureka and observability endpoints are environment-driven.

## Observability

Actuator health/info/metrics/Prometheus, Loki logging, Zipkin/Tempo tracing, and datasource instrumentation are configured.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-seat:1.0.0`. Requires MySQL, Kafka, Eureka, and airline data for cabin configuration workflows.
