# API Gateway

## Purpose

`api-gateway` is the only externally exposed application service. It routes `/auth/**` and `/api/**` requests to backend services, validates JWTs for protected routes, checks logout token revocation in Redis, applies selected role checks, and protects downstream calls with Resilience4j circuit breakers.

## Architecture

- Spring Cloud Gateway MVC route functions in `RouteConfig`.
- Eureka client and Spring Cloud LoadBalancer route requests by service name.
- Public auth route: `/auth/** -> user-service`.
- Protected routes: `/api/users/**`, `/api/airlines/**`, `/api/aircrafts/**`, `/api/cities/**`, `/api/airports/**`, `/api/flights/**`, `/api/flight-instances/**`, `/api/flight-schedules/**`, `/api/cabin-classes/**`, `/api/seat-maps/**`, `/api/seats/**`, `/api/seat-instances/**`, `/api/flight-instance-cabins/**`, `/api/fares/**`, `/api/fare-rules/**`, `/api/baggage-policies/**`, `/api/meals/**`, `/api/ancillaries/**`, `/api/insurance-coverages/**`, `/api/flight-meals/**`, `/api/flight-cabin-ancillaries/**`, `/api/bookings/**`, `/api/payments/**`.
- Admin-only route checks in gateway:
  - `POST /api/cities/**` and `POST /api/airports/**` require `ROLE_SYSTEM_ADMIN`.
  - `GET /api/airlines` requires `ROLE_SYSTEM_ADMIN`.

## APIs

- `POST /auth/logout`: blacklists the current JWT in Redis for its remaining lifetime.
- `/fallback`: generic gateway fallback endpoint.
- All other API paths are routed to downstream services.

## Database

No SQL database. Redis is used for JWT blacklist storage.

## Dependencies

Spring Boot Web MVC, Spring Cloud Gateway MVC, Eureka client, LoadBalancer, Resilience4j, Redis, Actuator, Micrometer Prometheus, Zipkin tracing, Loki Logback appender.

## Kafka Usage

No Kafka producers or consumers.

## Redis Usage

`TokenBlacklistService` stores revoked tokens using key format `jwt:blacklist:<token>` and TTL equal to remaining JWT validity. Gateway authentication rejects blacklisted tokens. Redis failures are fail-open in the current implementation.

## Configuration

- Port: `5000`
- Redis: `REDIS_HOST`, `REDIS_PORT`
- Eureka: configured through `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` in deployment.
- Observability: `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`, circuit breaker endpoints, Zipkin endpoint, Loki URL.

## Observability

Actuator exposes health, info, metrics, Prometheus, circuit breakers, and circuit breaker events. Logs include trace/span correlation and are sent to Loki when configured. Traces are exported through Zipkin protocol.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-api-gateway:1.0.0`. In `docker-compose.yml`, it is the only externally published application port: `5000:5000`.
