# Subscription Service

## Purpose

`subscription-service` is intended to manage platform subscription plans and airline subscriptions. In the current source tree it is a scaffolded Spring Boot service with application configuration and dependencies, but no domain controllers, entities, repositories, or services are implemented yet.

## Architecture

- Application entry point: `SubscriptionServiceApplication`.
- Configured as a Eureka client.
- Includes Web MVC, JPA, MySQL, Actuator, and observability dependencies.

## APIs

No REST controllers are currently defined.

## Database

The module is configured for JPA/MySQL through `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, but no JPA entities are currently present in source.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, Actuator, Prometheus, Zipkin, datasource Micrometer instrumentation, Feign Micrometer dependency, Loki Logback appender.

## Kafka Usage

No Kafka producers or consumers.

## Redis Usage

No Redis usage.

## Configuration

- Port: `5010`
- `spring.application.name=subscription-service`
- Database variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Eureka and observability endpoints are environment-driven.

## Observability

Actuator health/info/metrics/Prometheus, Loki logging, and Zipkin/Tempo tracing are configured.

## Deployment

Docker image configured by Jib: `nikhiltiwarip29/gds-subscription:1.0.0`. The main Compose file currently does not include this service, so add service and database entries before deploying it as part of the full platform.
