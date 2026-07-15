# Ancillary Service

## Purpose

`ancillary-service` owns optional travel products: ancillary catalog items, meals, insurance coverages, flight meal assignments, and cabin-specific flight ancillaries.

## Architecture

- Controllers: `AncillaryController`, `MealController`, `InsuranceCoverageController`, `FlightMealController`, `FlightCabinAncillaryController`.
- Services manage catalog data and flight/cabin availability/pricing.
- Feign clients call airline, location, and seat services for related domain data.
- Uses JPA specifications for filtering meal and flight meal queries.

## APIs

- `POST /api/ancillaries`, `GET /api/ancillaries/{id}`, `GET /api/ancillaries`, `PUT /api/ancillaries/{id}`, `DELETE /api/ancillaries/{id}`
- `POST /api/meals`, `POST /api/meals/bulk`, `GET /api/meals/{id}`, `GET /api/meals/airline`, `PUT /api/meals/{id}`, `PATCH /api/meals/{id}/availability`, `DELETE /api/meals/{id}`
- `POST /api/insurance-coverages`, `POST /api/insurance-coverages/bulk`, `GET /api/insurance-coverages/{id}`, `GET /api/insurance-coverages`, `GET /api/insurance-coverages/ancillary/{ancillaryId}`, `GET /api/insurance-coverages/ancillary/{ancillaryId}/active`, `PUT /api/insurance-coverages/{id}`, `DELETE /api/insurance-coverages/{id}`
- `POST /api/flight-meals`, `POST /api/flight-meals/bulk`, `POST /api/flight-meals/price/total`, `GET /api/flight-meals/{id}`, `GET /api/flight-meals/flight/{flightId}`, `GET /api/flight-meals/all`, `PUT /api/flight-meals/{id}`, `PATCH /api/flight-meals/{id}/availability`, `DELETE /api/flight-meals/{id}`
- `POST /api/flight-cabin-ancillaries`, `POST /api/flight-cabin-ancillaries/bulk`, `GET /api/flight-cabin-ancillaries/{id}`, `GET /api/flight-cabin-ancillaries/all`, `GET /api/flight-cabin-ancillaries/flight/{flightId}/cabin/{cabinClassId}`, `PUT /api/flight-cabin-ancillaries/{id}`, `DELETE /api/flight-cabin-ancillaries/{id}`, `POST /api/flight-cabin-ancillaries/price/total`

## Database

MySQL database: `airline_ancillary_db`. Entities: `Ancillary`, `Meal`, `InsuranceCoverage`, `FlightMeal`, `FlightCabinAncillary`.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, OpenFeign, Resilience4j, validation, Actuator, Prometheus, Zipkin, Loki, datasource and Feign instrumentation, `common-lib`.

## Kafka Usage

No Kafka producers or consumers.

## Redis Usage

No Redis cache is configured in this service.

## Configuration

- Port: `5007`
- `spring.application.name=ancillary-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Eureka and observability endpoints are environment-driven.

## Observability

Actuator, Prometheus metrics, Loki logs, Zipkin/Tempo traces, datasource spans, and Feign instrumentation are configured.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-ancillary:1.0.0`. Requires MySQL and Eureka. Some workflows depend on airline, location, and seat services.
