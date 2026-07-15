# TravelSphere

TravelSphere is a Spring Boot microservice platform for airline travel search, inventory, booking, payment, and customer notification workflows. The repository currently contains the backend platform under `backend/microservices`, organized as a Maven multi-module project with shared DTO/event contracts, cloud infrastructure services, domain services, Docker Compose deployment files, and Grafana-based observability.

## What Is Included

- Java 21, Spring Boot 4.0.2, Spring Cloud 2025.1.0.
- Maven parent project: `backend/microservices/pom.xml`.
- Shared library: `common-lib` for request/response payloads, enums, embeddables, exceptions, and Kafka events.
- Cloud modules: API Gateway, Eureka service registry, and Config Server.
- Business services: user, airline core, location, flight operations, seat, pricing, ancillary, booking, payment, notification, and subscription.
- Infrastructure: MySQL per service, Redis, Kafka in KRaft mode, Prometheus, Grafana, Loki, and Tempo.

## Repository Layout

```text
backend/microservices/
  common-lib/                 Shared DTOs, enums, events, exceptions
  cloud/
    api-gateway/              Public entry point, JWT validation, routing
    config-server/            Spring Cloud Config Server
    service-registry/         Eureka server
  services/
    user-service/             Signup, login, JWT issuing, user profiles
    airline-core-service/     Airlines and aircraft
    location-service/         Cities and airports
    flight-ops-service/       Flights, schedules, searchable instances
    seat-service/             Cabin classes, seat maps, runtime seat inventory
    pricing-service/          Fares, baggage policies, fare rules
    ancillary-service/        Meals, insurance, flight/cabin ancillaries
    booking-service/          Booking orchestration and ticket records
    payment-service/          Payment lifecycle and gateway integration
    notification-service/     Booking confirmation email/SMS
    subscription-service/     Subscription module scaffold
  docker-compose/
    docker-compose.yml        Containerized platform deployment
    docker-compose.dev.yml    Local Redis/Kafka/observability stack
```

## Service Catalog

| Service | Port | Main responsibility | Database |
| --- | ---: | --- | --- |
| API Gateway | 5000 | Routes all external traffic, validates JWTs, checks Redis token blacklist | Redis only |
| user-service | 5001 | User registration, login, JWT issuing, profile/user lookup | `airline_user` |
| flight-ops-service | 5002 | Flights, flight schedules, flight instances, search | `airline_flight_db` |
| airline-core-service | 5003 | Airlines and aircraft | `airline_core_db` |
| location-service | 5004 | Cities and airports | `airline_location_db` |
| seat-service | 5005 | Cabin classes, seat maps, seats, seat instances | `airline_seat_db` |
| pricing-service | 5006 | Fares, fare rules, baggage policies | `airline_pricing_db` |
| ancillary-service | 5007 | Ancillaries, meals, insurance, flight/cabin add-ons | `airline_ancillary_db` |
| booking-service | 5008 | Booking creation, ticketing, booking status transitions | `airline_booking_db` |
| payment-service | 5009 | Payment initiation, verification, Kafka payment events | `airline_payment_db` |
| subscription-service | 5010 | Subscription service module scaffold | configured through `DB_URL` |
| notification-service | 8094 | Consumes booking confirmation events and sends email/SMS | none |
| service-registry | 8761 | Eureka registry | none |
| config-server | 8888 | Config server | none |

## Request Model

Clients call only the API Gateway on port `5000`. The gateway defines route functions for each `/auth/**` and `/api/**` domain path, applies Resilience4j circuit breakers, resolves service instances through Eureka, and forwards requests through Spring Cloud LoadBalancer.

Protected routes require `Authorization: Bearer <jwt>`. The gateway validates the token with the same signing secret used by `user-service`, rejects blacklisted tokens from Redis, and forwards trusted identity headers:

- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`

## Core Flows

Authentication:
1. `POST /auth/signup` or `POST /auth/login` is routed to `user-service`.
2. `user-service` validates credentials, stores or loads the user, and issues a 24-hour JWT.
3. Subsequent protected requests are validated at the gateway.
4. `POST /auth/logout` is handled by the gateway and stores the token in Redis until expiry.

Booking:
1. `booking-service` receives a booking request.
2. It resolves passengers, validates the flight through `flight-ops-service`, creates a `PENDING` booking, and generates tickets.
3. It prices fare, seats, ancillaries, and meals using `pricing-service`, `seat-service`, and `ancillary-service`.
4. It initiates payment through `payment-service`.
5. `payment-service` verifies the external payment and publishes `payment.completed` or `payment.failed`.
6. `booking-service` consumes payment events, confirms or cancels the booking, and publishes `booking.confirmed`.
7. `seat-service` marks selected seats booked and `notification-service` sends booking confirmation messages.

Seat provisioning:
1. `flight-ops-service` creates a flight instance and publishes `flight-instance-created`.
2. `seat-service` consumes the event, finds aircraft cabin classes and template seats, creates `FlightInstanceCabin` rows, and generates available `SeatInstance` rows.

## Messaging

Kafka topics used by the code:

- `flight-instance-created`: produced by `flight-ops-service`, consumed by `seat-service`.
- `payment.completed`: produced by `payment-service`, consumed by `booking-service`.
- `payment.failed`: produced by `payment-service`, consumed by `booking-service`.
- `booking.confirmed`: produced by `booking-service`, consumed by `seat-service` and `notification-service`.

## Caching

Redis is used in two ways:

- Gateway token blacklist: revoked JWTs are stored as `jwt:blacklist:<token>` with a TTL equal to remaining token lifetime.
- Spring Cache backend: airline, location, pricing, and flight operations services configure Redis-backed caching for frequently read domain data.

## Observability

Most services expose Actuator health, info, metrics, and Prometheus endpoints. Logback is configured with a Loki appender. Micrometer tracing exports Zipkin-format spans to Tempo. `docker-compose.dev.yml` starts Prometheus, Grafana, Loki, and Tempo; Prometheus scrapes service `/actuator/prometheus` endpoints through `host.docker.internal`.

## Local Development

Build all modules:

```bash
cd backend/microservices
./mvnw clean install
```

Start developer infrastructure:

```bash
cd backend/microservices/docker-compose
docker compose -f docker-compose.dev.yml up -d
```

Run services from an IDE or with Maven using the service-specific `.env.example` values as the source for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, Kafka, Redis, Loki, and tracing endpoints.

Run the containerized platform:

```bash
cd backend/microservices/docker-compose
docker compose up -d
```

Only the API Gateway is externally exposed in the production-style Compose file: `http://localhost:5000`.

## Documentation

- [Architecture](docs/architecture.md)
- [Deployment](docs/deployment.md)
- Service READMEs are located in each module directory under `backend/microservices/cloud/*` and `backend/microservices/services/*`.
