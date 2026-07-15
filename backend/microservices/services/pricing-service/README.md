# Pricing Service

## Purpose

`pricing-service` owns fare products, fare rules, and baggage policies.

## Architecture

- Controllers: `FareController`, `FareRulesController`, `BaggagePolicyController`.
- Services manage fare lifecycle, baggage policy associations, fare rules, and batch lookup/search operations.
- Redis-backed Spring Cache is used for frequently accessed fare data.

## APIs

- `POST /api/fares`, `POST /api/fares/bulk`, `GET /api/fares`, `GET /api/fares/{id}`, `GET /api/fares/flight/{flightId}/cabin-class/{cabinClassId}`, `PUT /api/fares/{id}`, `DELETE /api/fares/{id}`, `POST /api/fares/batch-by-ids`, `POST /api/fares/search`, `GET /api/fares/lowest/flight/{flightId}/cabin-class/{cabinClassId}`
- `POST /api/fare-rules`, `GET /api/fare-rules/{id}`, `GET /api/fare-rules/fare/{fareId}`, `GET /api/fare-rules/airline/{airlineId}`, `PUT /api/fare-rules/{id}`, `DELETE /api/fare-rules/{id}`
- `POST /api/baggage-policies`, `POST /api/baggage-policies/bulk`, `GET /api/baggage-policies/{id}`, `GET /api/baggage-policies/fare/{fareId}`, `GET /api/baggage-policies/airline/{airlineId}`, `PUT /api/baggage-policies/{id}`, `DELETE /api/baggage-policies/{id}`

## Database

MySQL database: `airline_pricing_db`. Entities: `Fare`, `FareRules`, `BaggagePolicy`. `Fare` references external flight/cabin identifiers and has one-to-one fare rules and baggage policy relationships.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, Redis/Spring Cache, validation, Actuator, Prometheus, Zipkin, Loki, datasource instrumentation, `common-lib`.

## Kafka Usage

No Kafka producers or consumers.

## Redis Usage

Spring Cache stores fare lookups. Fare mutations evict affected fare caches.

## Configuration

- Port: `5006`
- `spring.application.name=pricing-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Redis: `REDIS_HOST`, `REDIS_PORT`
- Eureka and observability endpoints are environment-driven.

## Observability

Actuator metrics and Prometheus endpoint, Loki logging, Zipkin/Tempo tracing, and datasource observation are configured.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-pricing:1.0.0`. Requires MySQL, Redis, and Eureka.
