# TravelSphere Architecture

This document describes the architecture implemented by the source code, Maven modules, Spring configuration, Kafka consumers/producers, Redis usage, and Docker Compose files in this repository.

## Overall Microservice Architecture

```mermaid
flowchart TB
  Client[Client applications] --> Gateway[API Gateway<br/>:5000]
  Gateway --> Eureka[Service Registry<br/>Eureka :8761]
  Config[Config Server<br/>:8888] --> Eureka

  Gateway --> User[user-service<br/>:5001]
  Gateway --> Airline[airline-core-service<br/>:5003]
  Gateway --> Location[location-service<br/>:5004]
  Gateway --> Flight[flight-ops-service<br/>:5002]
  Gateway --> Seat[seat-service<br/>:5005]
  Gateway --> Pricing[pricing-service<br/>:5006]
  Gateway --> Ancillary[ancillary-service<br/>:5007]
  Gateway --> Booking[booking-service<br/>:5008]
  Gateway --> Payment[payment-service<br/>:5009]

  User --> UserDB[(airline_user)]
  Airline --> AirlineDB[(airline_core_db)]
  Location --> LocationDB[(airline_location_db)]
  Flight --> FlightDB[(airline_flight_db)]
  Seat --> SeatDB[(airline_seat_db)]
  Pricing --> PricingDB[(airline_pricing_db)]
  Ancillary --> AncillaryDB[(airline_ancillary_db)]
  Booking --> BookingDB[(airline_booking_db)]
  Payment --> PaymentDB[(airline_payment_db)]

  Gateway --> Redis[(Redis)]
  Airline --> Redis
  Location --> Redis
  Flight --> Redis
  Pricing --> Redis

  Flight --> Kafka[(Kafka)]
  Payment --> Kafka
  Booking --> Kafka
  Kafka --> Seat
  Kafka --> Booking
  Kafka --> Notification[notification-service<br/>:8094]

  Prometheus[Prometheus] --> Gateway
  Prometheus --> User
  Prometheus --> Flight
  Grafana[Grafana] --> Prometheus
  Grafana --> Loki[Loki]
  Grafana --> Tempo[Tempo]
```

## Service Responsibilities

- `api-gateway`: public entry point, JWT validation, Redis token blacklist lookup, route-level role checks for selected admin routes, Resilience4j circuit breakers, Eureka load balancing.
- `user-service`: signup, login, BCrypt password checks, JWT generation, user lookup and profile APIs.
- `airline-core-service`: airline and aircraft lifecycle APIs, Redis-backed cache for airline and aircraft reads.
- `location-service`: city and airport APIs, Redis-backed cache for frequently read location data.
- `flight-ops-service`: flights, schedules, flight instances, flight search, Feign calls to airline/location/seat/pricing, Kafka producer for flight instance creation, Redis cache for flight instance reads.
- `seat-service`: cabin classes, seat maps, template seats, runtime seat instances, Kafka consumers for flight instance creation and booking confirmation.
- `pricing-service`: fares, fare rules, baggage policies, Redis-backed fare cache.
- `ancillary-service`: ancillaries, meals, insurance coverage, flight meals, flight cabin ancillaries, Feign calls to airline/location/seat where required.
- `booking-service`: booking aggregate, passenger/ticket records, price orchestration, payment initiation, payment event consumption, booking confirmation event publishing.
- `payment-service`: payment record lifecycle, Razorpay link creation/verification, user lookup by Feign, payment event publishing.
- `notification-service`: consumes `booking.confirmed`, renders Thymeleaf email, sends SMTP email and optional Twilio SMS.
- `subscription-service`: Spring Boot module scaffold with web, JPA, Eureka, Actuator, and observability dependencies.

## Request Flow

1. A client sends requests to `api-gateway` on port `5000`.
2. Public `/auth/**` routes go to `user-service` without JWT checks.
3. Protected `/api/**` routes pass through the gateway JWT filter.
4. The gateway validates token signature/expiration, checks Redis for revocation, and forwards identity headers.
5. Gateway route functions apply circuit breakers and use Eureka-backed load balancing.
6. Domain services perform local persistence and call other services through OpenFeign where needed.

## Authentication Flow

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant G as API Gateway
  participant U as user-service
  participant DB as airline_user DB
  participant R as Redis

  C->>G: POST /auth/signup or /auth/login
  G->>U: Route public /auth/**
  U->>DB: Create user or validate email/password
  U-->>G: AuthResponse with JWT
  G-->>C: JWT
  C->>G: Protected /api/** with Bearer JWT
  G->>G: Validate signature, expiry, userId, email, authorities
  G->>R: Check jwt:blacklist:<token>
  G->>G: Add X-User-Id, X-User-Email, X-User-Roles
  G->>U: Forward protected request by route
```

Logout is handled by `POST /auth/logout` in the gateway. The gateway extracts the token, calculates remaining validity, and stores it in Redis with that TTL.

## Flight Booking Sequence

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant G as API Gateway
  participant B as booking-service
  participant F as flight-ops-service
  participant P as pricing-service
  participant S as seat-service
  participant A as ancillary-service
  participant Pay as payment-service
  participant Rzp as Razorpay
  participant K as Kafka
  participant N as notification-service

  C->>G: POST /api/bookings
  G->>B: Forward with X-User-Id
  B->>F: Validate flight and airline
  B->>B: Create PENDING booking, passengers, tickets
  B->>P: Calculate fare
  B->>S: Calculate selected seat total
  B->>A: Calculate ancillary and meal totals
  B->>Pay: POST /api/payments/initiate
  Pay->>Rzp: Create payment link
  Pay-->>B: PaymentInitiateResponse
  B-->>C: Checkout information
  C->>G: POST /api/payments/verify
  G->>Pay: Verify payment
  Pay->>Rzp: Fetch payment details
  Pay->>K: payment.completed or payment.failed
  K->>B: Consume payment event
  B->>B: CONFIRMED or CANCELLED
  B->>K: booking.confirmed on success
  K->>S: Mark selected seats BOOKED
  K->>N: Send email/SMS
```

## Kafka Event Flow

```mermaid
flowchart LR
  Flight[flight-ops-service] -- flight-instance-created --> Kafka[(Kafka)]
  Kafka -- flight-instance-created --> Seat[seat-service]
  Payment[payment-service] -- payment.completed --> Kafka
  Payment -- payment.failed --> Kafka
  Kafka -- payment.completed/payment.failed --> Booking[booking-service]
  Booking -- booking.confirmed --> Kafka
  Kafka -- booking.confirmed --> Seat
  Kafka -- booking.confirmed --> Notification[notification-service]
```

Topic details:

- `flight-instance-created`: payload `FlightInstanceCreatedEvent`; creates runtime seat inventory.
- `payment.completed`: payload `PaymentCompletedEvent`; confirms booking.
- `payment.failed`: payload `PaymentFailedEvent`; cancels pending booking.
- `booking.confirmed`: payload `BookingConfirmedEvent`; updates seat status and sends notifications.

## Seat Generation Flow

```mermaid
sequenceDiagram
  autonumber
  participant F as flight-ops-service
  participant K as Kafka
  participant S as seat-service
  participant CC as CabinClassRepository
  participant SR as SeatRepository
  participant FIC as FlightInstanceCabinRepository
  participant SI as SeatInstanceRepository

  F->>F: Create FlightInstance
  F->>K: flight-instance-created
  K->>S: FlightInstanceCreatedEvent
  S->>CC: findByAircraftId(aircraftId)
  loop each CabinClass
    S->>SR: findBySeatMapId(seatMapId)
    S->>FIC: save FlightInstanceCabin
    S->>SI: save SeatInstance rows as AVAILABLE
  end
```

## Cache Flow

```mermaid
flowchart TD
  Request[Service method with @Cacheable] --> Cache{Redis cache hit?}
  Cache -- yes --> Cached[Return cached value]
  Cache -- no --> DB[(MySQL)]
  DB --> Store[Store result in Redis]
  Store --> Response[Return response]
  Mutating[Create/update/delete] --> Evict[@CacheEvict/@CachePut]
  Evict --> Redis[(Redis)]
```

Redis-backed Spring Cache is configured in airline, location, pricing, and flight operations services. The gateway uses Redis directly through `StringRedisTemplate` for token blacklisting.

## Observability Flow

```mermaid
flowchart LR
  Services[Spring Boot services] --> Actuator[/actuator/prometheus]
  Prometheus[Prometheus] --> Actuator
  Services --> Loki[Loki via Logback appender]
  Services --> Tempo[Tempo via Zipkin exporter]
  Grafana[Grafana] --> Prometheus
  Grafana --> Loki
  Grafana --> Tempo
```

The application configuration exposes health/info/metrics/prometheus endpoints on most services. Trace IDs and span IDs are included in logging patterns, and tracing sampling is configured at `1.0` in service YAML files.

## AWS Deployment Architecture

```mermaid
flowchart TB
  Users[Users] --> DNS[Route 53]
  DNS --> ALB[Public Application Load Balancer]
  ALB --> APIGW[API Gateway tasks<br/>ECS/Fargate or EKS]

  subgraph VPC[VPC]
    subgraph Public[Public subnets]
      ALB
      NAT[NAT Gateway]
    end
    subgraph Private[Private app subnets]
      APIGW
      Services[Business service tasks]
      Eureka[Eureka tasks]
      Config[Config Server tasks]
    end
    subgraph Data[Private data subnets]
      RDS[(Amazon RDS MySQL<br/>database per service)]
      Redis[(ElastiCache Redis)]
      MSK[(Amazon MSK Kafka)]
    end
    Observability[Amazon Managed Prometheus/Grafana<br/>or self-hosted Grafana stack]
  end

  APIGW --> Eureka
  APIGW --> Services
  Services --> RDS
  Services --> Redis
  Services --> MSK
  Services --> Observability
  NAT --> Internet[External APIs: Razorpay, SMTP, Twilio]
```

The AWS diagram is a production deployment target derived from the Compose topology: one public gateway tier, private service tier, private data tier, managed MySQL/Redis/Kafka replacements, and centralized observability.
