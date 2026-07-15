# Location Service

## Purpose

`location-service` owns city and airport reference data used by flight search and airport-aware workflows.

## Architecture

- Controllers: `CityController`, `AirportController`.
- Services: `CityServiceImpl`, `AirportServiceImpl`.
- Entities keep airport-to-city relationships inside the service boundary.
- Redis-backed Spring Cache accelerates common city and airport reads.

## APIs

- `POST /api/cities`
- `POST /api/cities/bulk`
- `GET /api/cities/{id}`
- `GET /api/cities`
- `PUT /api/cities/{id}`
- `DELETE /api/cities/{id}`
- `GET /api/cities/search`
- `GET /api/cities/country/{countryCode}`
- `GET /api/cities/exists/{cityCode}`
- `POST /api/airports`
- `POST /api/airports/bulk`
- `GET /api/airports/{id}`
- `GET /api/airports`
- `GET /api/airports/city/{cityId}`
- `PUT /api/airports/{id}`
- `DELETE /api/airports/{id}`

## Database

MySQL database: `airline_location_db`. Entities: `City`, `Airport`. `Airport` has a many-to-one relationship with `City`.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, Redis, Spring Cache, validation, Lombok, Actuator, Prometheus, Zipkin, datasource Micrometer instrumentation, Loki, `common-lib`.

## Kafka Usage

No Kafka producers or consumers.

## Redis Usage

Spring Cache stores city and airport lookups. Mutating operations use cache put/evict annotations to keep cached data coherent.

## Configuration

- Port: `5004`
- `spring.application.name=location-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Redis: `REDIS_HOST`, `REDIS_PORT`
- Eureka and observability endpoints are environment-driven.

## Observability

Actuator exposes health, info, metrics, and Prometheus. Logs and traces are configured for Loki and Tempo/Zipkin.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-location:1.0.0`. Requires MySQL, Redis, and Eureka. Access through the gateway.
