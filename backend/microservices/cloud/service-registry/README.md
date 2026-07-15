# Service Registry

## Purpose

`service-registry` is the Eureka server used by the gateway and business services for service discovery.

## Architecture

The module is a Spring Boot Eureka server. Services register under their `spring.application.name`, and the API Gateway uses Eureka-backed load balancing to route to service instances.

## APIs

- Eureka dashboard and registry API on port `8761`.
- Actuator health endpoint is used by Docker Compose health checks.

## Database

No database.

## Dependencies

Spring Boot, Spring Cloud Netflix Eureka Server, Actuator.

## Kafka Usage

None.

## Redis Usage

None.

## Configuration

- Port: `8761`
- Application name: `service-registry`
- The service registry does not register itself as an ordinary client in typical Eureka-server configuration.

## Observability

Actuator health is used by Compose. Prometheus scraping is configured in the development observability stack at `host.docker.internal:8761/actuator/prometheus`.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-service-registry:1.0.0`. Start this before gateway and business services.
