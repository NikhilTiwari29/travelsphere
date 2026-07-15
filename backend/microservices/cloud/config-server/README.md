# Config Server

## Purpose

`config-server` is the Spring Cloud Config Server module for centralized configuration support.

## Architecture

The module registers with Eureka and exposes Spring Cloud Config endpoints on port `8888`. Current service YAML files in the repository still carry most runtime configuration directly, so this module is infrastructure-ready rather than the only active source of configuration.

## APIs

- Spring Cloud Config endpoints on `:8888`.
- Actuator health/info endpoints.

## Database

No database.

## Dependencies

Spring Boot, Spring Cloud Config Server, Eureka client, Actuator.

## Kafka Usage

None.

## Redis Usage

None.

## Configuration

- Port: `8888`
- Application name: `config-server`
- Eureka default zone defaults to `http://localhost:8761/eureka/`.

## Observability

Actuator endpoints are exposed. Prometheus development config scrapes `host.docker.internal:8888/actuator/prometheus`.

## Deployment

Run after `service-registry` when central config is part of the target environment. Keep service secrets outside Git and provide them through environment variables or a secrets manager.
