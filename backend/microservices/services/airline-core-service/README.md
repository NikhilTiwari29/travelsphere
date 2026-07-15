# Airline Core Service

## Purpose

`airline-core-service` owns airline and aircraft master data.

## Architecture

- Controllers: `AirlineController`, `AircraftController`.
- Services: `AirlineServiceImpl`, `AircraftServiceImpl`.
- Mappers convert entities to shared response DTOs.
- Redis-backed Spring Cache is used for frequent airline and aircraft reads.

## APIs

- `POST /api/airlines`
- `GET /api/airlines/admin`
- `GET /api/airlines/{id}`
- `GET /api/airlines`
- `GET /api/airlines/dropdown`
- `PUT /api/airlines`
- `DELETE /api/airlines/{id}`
- `POST /api/airlines/{id}/approve`
- `POST /api/airlines/{id}/suspend`
- `POST /api/airlines/{id}/ban`
- `POST /api/aircrafts`
- `GET /api/aircrafts/{id}`
- `GET /api/aircrafts`
- `PUT /api/aircrafts/{id}`
- `DELETE /api/aircrafts/{id}`

## Database

MySQL database: `airline_core_db`. Entities: `Airline`, `Aircraft`. Tables include `airlines` and `aircrafts`. `Aircraft` has a many-to-one relation to `Airline`.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, Redis, Spring Cache, Lombok, validation, Actuator, Prometheus, Zipkin, datasource Micrometer instrumentation, Loki, `common-lib`.

## Kafka Usage

No Kafka producers or consumers.

## Redis Usage

Configured as Spring Cache backend. Cache annotations are used in airline and aircraft service implementations, including cacheable airline lookups and dropdown data plus cache eviction on mutations.

## Configuration

- Port: `5003`
- `spring.application.name=airline-core-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Redis: `REDIS_HOST`, `REDIS_PORT`
- Eureka and observability through environment-configured endpoints.

## Observability

Actuator exposes health, info, metrics, and Prometheus. Logs go to Loki when `LOKI_URL` is configured. Traces export through Zipkin protocol.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-airline:1.0.0`. Requires MySQL, Redis, and Eureka. Access through the API Gateway.
