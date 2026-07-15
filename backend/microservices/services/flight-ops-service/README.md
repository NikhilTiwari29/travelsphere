# Flight Ops Service

## Purpose

`flight-ops-service` owns flight definitions, schedules, flight instances, and flight search. It also publishes flight instance creation events so seat inventory can be provisioned asynchronously.

## Architecture

- Controllers: `FlightController`, `FlightScheduleController`, `FlightInstanceController`, `FlightSearchController`.
- Services handle flight, schedule, instance, and search logic.
- Feign clients call `airline-core-service`, `location-service`, `seat-service`, and `pricing-service`.
- `FlightInstanceEventProducer` publishes `FlightInstanceCreatedEvent`.
- Redis-backed Spring Cache is used for flight instance reads.

## APIs

- `GET /api/flights/search`
- `POST /api/flights`
- `POST /api/flights/bulk`
- `POST /api/flights/batch`
- `GET /api/flights/{id}`
- `GET /api/flights/number/{flightNumber}`
- `GET /api/flights/airline`
- `PUT /api/flights/{id}`
- `PATCH /api/flights/{id}/status`
- `DELETE /api/flights/{id}`
- `POST /api/flight-schedules`
- `GET /api/flight-schedules/{id}`
- `GET /api/flight-schedules`
- `PUT /api/flight-schedules/{id}`
- `DELETE /api/flight-schedules/{id}`
- `POST /api/flight-instances`
- `POST /api/flight-instances/batch`
- `GET /api/flight-instances/{id}`
- `GET /api/flight-instances/list`
- `GET /api/flight-instances`
- `PUT /api/flight-instances/{id}`
- `DELETE /api/flight-instances/{id}`

## Database

MySQL database: `airline_flight_db`. Entities: `Flight`, `FlightSchedule`, `FlightInstance`. Flight instances belong to flights; external references such as airline, aircraft, airports, fares, and seats are service IDs enriched through Feign.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, OpenFeign, Kafka, Redis/Spring Cache, Resilience4j, validation, Actuator, Prometheus, Zipkin, Loki, datasource and Feign Micrometer instrumentation, `common-lib`.

## Kafka Usage

Producer:

- Topic `flight-instance-created`
- Payload `FlightInstanceCreatedEvent`
- Consumer: `seat-service`

## Redis Usage

Spring Cache backs selected flight instance reads. Cache entries are evicted on instance mutations.

## Configuration

- Port: `5002`
- `spring.application.name=flight-ops-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Kafka: `KAFKA_BOOTSTRAP_SERVERS`
- Redis: `REDIS_HOST`, `REDIS_PORT`
- Eureka and observability endpoints are environment-driven.

## Observability

Actuator, Prometheus metrics, Loki logging, Zipkin/Tempo tracing, datasource spans, and Feign call metrics/traces are configured.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-flight:1.0.0`. Requires MySQL, Kafka, Redis, Eureka, and downstream services for enriched operations.
