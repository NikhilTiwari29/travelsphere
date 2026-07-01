# Airline GDS — Microservices Platform

A production-grade **Airline Global Distribution System (GDS)** built with Spring Boot microservices, event-driven architecture, and cloud-native patterns. The platform covers the complete airline booking lifecycle — from flight search and seat selection through payment processing and passenger notifications.

---

## Table of Contents

1. [Features](#features)
2. [Architecture Overview](#architecture-overview)
3. [Services](#services)
4. [Data Flow](#data-flow)
5. [Technology Stack](#technology-stack)
6. [Design Patterns](#design-patterns)
7. [Security](#security)
8. [API Reference](#api-reference)
9. [Running the Project](#running-the-project)
10. [Configuration](#configuration)
11. [Known Limitations](#known-limitations)

---

## Features

### For Passengers (Customer-Facing)
- **Flight Search** — Multi-criteria search with filters: route, date, cabin class, airline, alliance, price range, departure/arrival time window, max duration, and sorting (price / departure / duration)
- **Seat Selection** — Interactive cabin-class seat map with availability status (window, aisle, middle, extra legroom)
- **Ancillary Services** — Add meals, insurance, and other add-ons at booking
- **Booking Management** — View, modify, and cancel bookings; track status in real time
- **Payment Processing** — Razorpay and Stripe integration with secure payment verification
- **Booking Confirmation Email** — Production-grade HTML email with flight details, passenger list, ticket numbers, fare breakdown, baggage allowance, fare benefits, and payment receipt
- **SMS Notification** — Instant booking confirmation via Twilio
- **Ticket Management** — E-ticket generation with unique ticket numbers per passenger
- **Booking History** — Full history of past and upcoming flights

### For Airline Administrators
- **Airline Management** — Register, approve, suspend, and ban airlines; manage status lifecycle
- **Aircraft Management** — Define aircraft types with seat capacities
- **Flight Management** — Create and manage master flights and recurring flight schedules
- **Flight Instance Management** — Manage specific departures with terminal, gate, and real-time status
- **Pricing & Fares** — Define fares per cabin class with full benefit matrix (refund, date change, lounge, meals, priority boarding)
- **Baggage Policies** — Cabin and check-in baggage rules linked to fares
- **Seat Maps** — Create seat map templates and assign per aircraft/cabin
- **Meal Management** — Create meal offerings and assign to specific flights
- **Insurance Products** — Define insurance coverage options
- **Ancillary Catalogue** — Manage ancillary service catalogue per airline
- **Booking Analytics** — Daily and monthly booking statistics with revenue tracking

### For System Administrators
- **Multi-Airline Support** — Isolated data per airline; RBAC protects cross-airline access
- **City & Airport Data** — Manage global location data with IATA codes, timezones, and geolocation
- **Centralized Configuration** — All service config driven from a Git-backed Config Server
- **Service Discovery** — Eureka-based dynamic service registration
- **Fault Tolerance** — Circuit breakers protect all inter-service calls with configurable thresholds

---

## Architecture Overview

```
                                    ┌─────────────────────────────────────────────────────┐
                                    │                  CLIENT (Browser / Mobile)          │
                                    └───────────────────────────┬─────────────────────────┘
                                                                │ HTTPS :5000
                                    ┌───────────────────────────▼─────────────────────────┐
                                    │                    API GATEWAY                      │
                                    │         JWT Auth · Routing · Circuit Breaker        │
                                    └──┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬────────┘
                                       │   │   │   │   │   │   │   │   │   │   │
          ┌────────────────────────────┘   │   │   │   │   │   │   │   │   │   └──────────────────┐
          │         ┌──────────────────────┘   │   │   │   │   │   │   │   └─────────────┐        │
          ▼         ▼          ▼               ▼   ▼   ▼   ▼   ▼   ▼   ▼                ▼        ▼
   ┌────────────┐ ┌──────────┐ ┌────────────┐ ...                           ┌───────────┐ ┌──────────────┐
   │    User    │ │ Airline  │ │ Flight-Ops │                               │  Booking  │ │   Payment    │
   │  Service   │ │  Core   │ │  Service  │                               │  Service  │ │   Service    │
   └────────────┘ └──────────┘ └─────┬──────┘                               └─────┬─────┘ └──────┬───────┘
                                     │ Feign                                       │ Feign         │
                                     ├──► Pricing Service                          │               │
                                     ├──► Seat Service                             │               │
                                     └──► Airline Core                             │               │
                                                                                   ▼               ▼
                                    ┌──────────────────────────────────────────────────────────────────────┐
                                    │                         APACHE KAFKA                                  │
                                    │   booking.confirmed  │  payment.completed  │  payment.failed          │
                                    └──────┬───────────────────────────────────────────────────────────────┘
                                           │
                                    ┌──────▼──────────────────────────────────────────────────────────────┐
                                    │   Seat Service (mark seats BOOKED)  │  Notification Service (email/SMS) │
                                    └─────────────────────────────────────────────────────────────────────┘

                     ┌─────────────────────────────────────────────────────────────────────────────────┐
                     │                         INFRASTRUCTURE                                          │
                     │    Eureka (Service Registry) ·  Config Server (Git-backed) · MySQL per service  │
                     └─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Services

### Infrastructure Services

| Service | Port | Role |
|---|---|---|
| **API Gateway** | 5000 (external) | Single entry point — JWT validation, routing, CORS, circuit breaker fallbacks |
| **Config Server** | 8888 | Centralized config pulled from Git for all services |
| **Service Registry** | 8761 | Eureka — service registration and discovery |
| **Kafka** | 9092 | Event streaming (KRaft mode — no Zookeeper) |
| **MySQL** | varies | Dedicated database per business service |

### Business Services

| Service | Port | Database | Kafka | Responsibility |
|---|---|---|---|---|
| **user-service** | dynamic | `airline_user` | — | Auth (signup/login), JWT, user profiles, RBAC |
| **airline-core-service** | dynamic | `airline_core_db` | — | Airline + aircraft management, alliance lookup |
| **location-service** | dynamic | `airline_location_db` | — | Cities, airports, IATA codes, geolocation |
| **flight-ops-service** | dynamic | `airline_flight_db` | Producer | Flights, schedules, instances, advanced search |
| **seat-service** | dynamic | `airline_seat_db` | Consumer + Producer | Seat maps, cabin config, availability, pricing |
| **pricing-service** | dynamic | `airline_pricing_db` | — | Fares, baggage policies, fare rules |
| **ancillary-service** | dynamic | `airline_ancillary_db` | — | Meals, insurance, ancillary catalogue |
| **booking-service** | dynamic | `airline_booking_db` | Consumer | Bookings, passengers, tickets, statistics |
| **payment-service** | dynamic | `airline_payment_db` | Producer | Razorpay/Stripe integration, verification, refunds |
| **notification-service** | 8094 | — | Consumer | Email (Gmail SMTP / Thymeleaf) + SMS (Twilio) |
| **subscription-service** | dynamic | — | — | Airline subscription and membership management |

---

## Data Flow

### Flight Search

```
Client → API Gateway → flight-ops-service
  │
  ├─ 1. Resolve airline filter
  │       └─[Feign]→ airline-core-service  (by IATA code or alliance name)
  │
  ├─ 2. Query DB via JPA Specification (Criteria API)
  │       Filters: airports · departure date · passenger count
  │                cabin class · duration · departure/arrival time window
  │       (No price JOIN — kept in pricing-service for clean separation)
  │
  ├─ 3. Batch-fetch lowest fares for result page
  │       └─[Feign POST]→ pricing-service  /api/fares/search?cabinClass=ECONOMY
  │                        Body: [flightId1, flightId2, ...]
  │                        Returns: Map<flightId, cheapestFare>
  │
  ├─ 4. Apply price range filter (post-fetch)
  │       Remove flights outside min/max price
  │
  └─ 5. Return Page<FlightInstanceResponse> with enriched fare data
```

### Booking & Payment

```
Client → API Gateway → booking-service
  │
  ├─ 1.  Validate flight      [Feign] → flight-ops-service
  ├─ 2.  Validate seats        [Feign] → seat-service
  ├─ 3.  Validate ancillaries  [Feign] → ancillary-service
  ├─ 4.  Fetch fare            [Feign] → pricing-service
  ├─ 5.  Create Booking (status: PENDING)
  ├─ 6.  Initiate payment      [Feign] → payment-service → Razorpay/Stripe
  └─ 7.  Return payment URL to client
         │
         │  [Client completes payment on gateway]
         │
  ┌──────▼──────────────────────────────────────────────────────────┐
  │  payment-service receives callback from Razorpay / Stripe       │
  │  → Verify signature                                             │
  │  → Publish PaymentCompletedEvent ─────────────► Kafka           │
  └──────────────────────────────────────────────────────────────────┘
         │
         ▼  [Kafka: payment.completed]
  ┌──────────────────────────────────────────────────────────────────┐
  │  booking-service (PaymentEventListener)                         │
  │  → Update Booking status to CONFIRMED                           │
  │  → Fetch FlightInstance, Fare, User details [Feign]             │
  │  → Publish BookingConfirmedEvent ──────────────► Kafka          │
  └──────────────────────────────────────────────────────────────────┘
         │
         ▼  [Kafka: booking.confirmed]  (two parallel consumers)
  ┌──────────────────┐       ┌──────────────────────────────────────┐
  │   seat-service   │       │        notification-service          │
  │  Mark seats as   │       │  Send confirmation email (HTML)      │
  │  BOOKED          │       │  Send SMS via Twilio                 │
  └──────────────────┘       └──────────────────────────────────────┘
```

### Booking Confirmation Email Content

The HTML email sent after a confirmed booking includes:

- **Header** — Airline logo, "Booking Confirmed ✓" badge, personalised greeting
- **Booking Reference** — Large, prominent, monospaced reference number
- **Flight Route Card** — Departure/arrival IATA codes, city names, times, date, flight number, duration, terminal, gate, cabin class, aircraft model
- **Passenger Table** — Per passenger: name, adult/child type, ticket number, passport number, nationality, and any special requirements (wheelchair, dietary)
- **Baggage Allowance** — Cabin and check-in allowance from the fare's baggage policy
- **Fare Benefits** — Priority boarding, lounge access, complimentary meals, date change, refund entitlement — shown as ✓ / ✗
- **Payment Summary** — Base fare (× passengers), taxes, seat fees, ancillaries, meals, **total paid**, transaction ID, provider reference, payment gateway, payment date
- **Web Check-In CTA** — Button linking to the check-in portal
- **Important Information** — Check-in open window, airport deadline, ID requirements, refund/change policy
- **Footer** — Support email, phone, legal notice

---

## Technology Stack

### Backend Framework

| Technology | Version | Purpose |
|---|---|---|
| Spring Boot | 4.0.2 | Base application framework |
| Spring Cloud | 2025.1.0 | Microservices toolkit (Config, Eureka, Gateway, Feign) |
| Spring Data JPA | managed | ORM with Criteria API support |
| Spring Security | managed | Authentication and RBAC |
| Spring Kafka | managed | Kafka producer/consumer integration |
| Spring Mail | managed | JavaMailSender (email sending) |
| Thymeleaf | managed | HTML email template rendering |

### Infrastructure

| Technology | Version | Purpose |
|---|---|---|
| Apache Kafka | 4.1.1 | Async event streaming (KRaft — no Zookeeper) |
| MySQL | 8.0 | Primary relational database (one DB per service) |
| Eureka Server | Spring Cloud | Service discovery and registration |
| Spring Cloud Config | Spring Cloud | Git-backed centralised configuration |
| Spring Cloud Gateway | Spring Cloud | API gateway with WebMVC routing |

### Resilience

| Technology | Purpose |
|---|---|
| Resilience4j | Circuit breaker, retry, rate limiter |
| Spring Cloud Circuit Breaker | Integration layer for Resilience4j |
| OpenFeign | Declarative HTTP client with fallback support |

### Security

| Technology | Purpose |
|---|---|
| JJWT (io.jsonwebtoken) | JWT creation, parsing, and validation |
| BCrypt | Password hashing |
| Spring Security | Method-level and URL-based authorization |

### Payment Gateways

| Gateway | Purpose |
|---|---|
| Razorpay | Primary payment gateway (India) |
| Stripe | Alternative payment gateway (global) |

### Notifications

| Technology | Purpose |
|---|---|
| Gmail SMTP (587/TLS) | Email delivery |
| Twilio | SMS delivery |
| Thymeleaf (HTML) | Responsive HTML email templates with inline CSS |

### Build & Deployment

| Tool | Purpose |
|---|---|
| Maven (multi-module) | Build tool with parent POM for version management |
| Google Jib | Build Docker images via Maven without a Dockerfile |
| Docker Compose | Local and production container orchestration |
| Spring Boot DevTools | Live reload during development |
| Lombok | Compile-time boilerplate generation |

---

## Design Patterns

### Microservices Patterns

| Pattern | Where Used |
|---|---|
| **API Gateway** | Single entry point — all traffic routed through `api-gateway` |
| **Service Registry** | Eureka — services register themselves; gateway resolves URLs dynamically |
| **Circuit Breaker** | All Feign clients protected with Resilience4j; custom thresholds per client |
| **Fallback** | Every Feign client has a `*ClientFallback.java` returning safe default values |
| **Config Server** | All service configs in a Git repo; services pull on startup |
| **Saga (choreography)** | Booking → Payment → Seat Reservation → Notification via Kafka events |

### Data Patterns

| Pattern | Where Used |
|---|---|
| **Repository Pattern** | `JpaRepository` for all CRUD; `JpaSpecificationExecutor` for dynamic queries |
| **Specification Pattern** | `FlightInstanceSpecification` — Criteria API for complex flight search filters |
| **DTO / Mapper** | Request/Response DTOs in `common-lib`; Mapper classes per service |
| **Denormalization** | `Fare.cabinClass` stored on Fare (avoids join with seat-service during search) |
| **Bulk Endpoints** | `POST /bulk` on all resource controllers — reduces network round-trips |
| **Per-Request Cache** | `HashMap` caches inside a single request — avoids N+1 in enrichment loops |

### Event-Driven Patterns

| Pattern | Where Used |
|---|---|
| **Event Sourcing** | Kafka topics carry state-change events (`BookingConfirmedEvent`, `PaymentCompletedEvent`) |
| **Pub/Sub** | `booking.confirmed` consumed by both `seat-service` and `notification-service` |
| **Event Enrichment** | `BookingEventProducer` enriches event with user, flight, fare, baggage data before publishing |
| **Consumer Groups** | Each service has its own group ID — each gets its own copy of every message |

### Cross-Cutting Concerns

| Concern | Approach |
|---|---|
| Authentication | JWT validated at gateway; userId/roles forwarded as headers to backend |
| Authorisation | Role checked at gateway (URL-level) and service (method-level) |
| Error handling | `ResourceNotFoundException`, `PaymentException`, `UserException` in common-lib |
| Observability | `/actuator/health`, `/actuator/circuitbreakers` on all services |
| Logging | Slf4j + `@Slf4j` (Lombok) throughout |

---

## Security

### Roles

| Role | Permissions |
|---|---|
| `ROLE_SYSTEM_ADMIN` | Create/manage airlines, airports, cities; approve/suspend/ban airlines |
| `ROLE_AIRLINE_OWNER` | Manage own airline's flights, schedules, fares, seats, ancillaries |
| `ROLE_CUSTOMER` | Search flights, make bookings, view profile, manage own bookings |

### JWT Authentication Flow

```
1. POST /auth/login  { email, password }
        │
        ▼
   user-service validates credentials
        │
        ▼
   Returns JWT token (payload: userId, email, role, expiry)
        │
        ▼
   Client sends: Authorization: Bearer <token>
        │
        ▼
   API Gateway validates signature + expiry
   Extracts claims → forwards as headers:
     X-User-Id, X-User-Email, X-User-Roles
        │
        ▼
   Backend services trust these headers
   (JWT already verified at gateway)
```

### Endpoint Protection

| Path | Access |
|---|---|
| `POST /auth/signup`, `POST /auth/login` | Public |
| `POST /api/airlines`, `POST /api/airports`, `POST /api/cities` | ROLE_SYSTEM_ADMIN |
| `GET /api/flights/search`, all GET endpoints | Any authenticated user |
| `POST /api/bookings`, `GET /api/bookings/user/**` | ROLE_CUSTOMER |
| `GET /api/bookings/airline/**` | ROLE_AIRLINE_OWNER / ROLE_SYSTEM_ADMIN |

---

## API Reference

### Auth — `POST /auth/signup`
```json
{
  "fullName": "John Smith",
  "email": "john@example.com",
  "password": "secret123",
  "phone": "+919876543210",
  "role": "ROLE_CUSTOMER"
}
```

### Flight Search — `GET /api/flights/search`
```
?departureAirportId=1
&arrivalAirportId=2
&departureDate=2026-03-15
&passengers=2
&cabinClass=ECONOMY
&airlines=AI,UK             (optional)
&alliance=star              (optional)
&minPrice=1000              (optional)
&maxPrice=10000             (optional)
&departureTimeRange=MORNING (optional: MORNING/AFTERNOON/EVENING/NIGHT)
&maxDuration=180            (optional, minutes)
&sortBy=price               (optional: departure/arrival/duration/price)
&sortOrder=ASC
&page=0&size=20
```

### Create Booking — `POST /api/bookings`
```json
{
  "flightInstanceId": 101,
  "flightId": 10,
  "fareId": 5,
  "cabinClass": "ECONOMY",
  "tripType": "ONE_WAY",
  "ancillaryIds": [1, 2],
  "mealIds": [3],
  "contactInfo": {
    "email": "john@example.com",
    "phone": "+919876543210"
  },
  "passengers": [
    {
      "firstName": "John",
      "lastName": "Smith",
      "email": "john@example.com",
      "phone": "+919876543210",
      "dateOfBirth": "1990-05-15",
      "gender": "MALE",
      "seatInstanceId": 201,
      "passportNumber": "AB1234567",
      "nationality": "Indian"
    }
  ]
}
```
**Returns:** `PaymentInitiateResponse` with `checkoutUrl` for Razorpay/Stripe

### Verify Payment — `POST /api/payments/verify`
```json
{
  "razorpayPaymentId": "pay_xyz",
  "razorpayOrderId": "order_abc",
  "razorpaySignature": "sig_123"
}
```
**On success:** triggers the event chain → booking confirmed → seats reserved → email + SMS sent

### Common Response Shapes

**Paginated list:**
```json
{
  "content": [ ... ],
  "totalElements": 120,
  "totalPages": 6,
  "pageNumber": 0,
  "pageSize": 20
}
```

**Error:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Booking not found with ID: 99"
}
```

**Circuit breaker open:**
```json
{
  "status": 503,
  "message": "Service temporarily unavailable. Please retry."
}
```

---

## Running the Project

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker + Docker Compose
- Git

### Quick Start (Docker Compose)

```bash
# 1. Clone the repository
git clone <repo-url>
cd microservices

# 2. Set required environment variables (copy and fill in)
cp docker-compose/.env.example docker-compose/.env

# 3. Build all service JARs
mvn clean package -DskipTests

# 4. Start all services
docker compose -f docker-compose/docker-compose.yml up -d

# 5. Check health
curl http://localhost:5000/actuator/health
```

The API is available at `http://localhost:5000`.

### Local Development (individual services)

```bash
# Start infrastructure first
docker compose -f docker-compose/docker-compose.yml up -d kafka mysql eureka config-server

# Then start individual services from their directory
cd services/booking-service
mvn spring-boot:run
```

### Environment Variables

```env
# Email (Gmail SMTP)
MAIL_USERNAME=your-email@gmail.com
MAIL_APP_PASSWORD=xxxx-xxxx-xxxx-xxxx   # Gmail App Password (not regular password)

# SMS (Twilio)
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_FROM_NUMBER=+1xxxxxxxxxx
TWILIO_ENABLED=true

# Payment — Razorpay
RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxxxxxx

# Payment — Stripe
STRIPE_API_KEY=YOUR_STRIPE_SECRET_KEY

# JWT
JWT_SECRET=your-256-bit-secret-key
```

---

## Configuration

All service configuration is managed by the **Config Server** which pulls from:
```
https://github.com/NikhilTiwari29/nikhil-air-config
```

Each service bootstraps with:
```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

`optional:` means if the Config Server is unavailable, the service falls back to its local `application.yaml`.

### Circuit Breaker Thresholds

| Client | Failure Threshold | Wait (open state) | Notes |
|---|---|---|---|
| Default | 50% | 30s | All clients inherit this |
| FlightClient | 40% | 45s | Stricter — search must be reliable |
| PaymentClient | 30% | 60s | Very strict — payment is critical |
| AncillaryClient | 50% | 30s | Default |
| PricingClient | 50% | 30s | Default |
| SeatClient | 50% | 30s | Default |
| UserClient | 50% | 30s | Default |

---

## Known Limitations

| Limitation | Impact | Workaround |
|---|---|---|
| `totalElements` overcounts on price filter | Pagination shows higher count than results | Post-filter page normalisation (planned) |
| Seat availability not validated during search | Race condition possible at high concurrency | Optimistic lock on `SeatInstance.version` at booking time |
| No distributed transactions (2PC) | Partial failures require compensating actions | Saga choreography via Kafka; status-based compensation |
| Price filter reduces page size | Page may have fewer results than `pageSize` | Documented; client should handle sparse pages |
| Config Server is a single point of failure | All services need it on first start | `optional:` config import; local YAML fallback |
| Kafka is single broker | No replication in Docker Compose | Increase replicas in Kubernetes deployment |
| Notification failure is silent | Email/SMS may not be delivered | Dead-letter queue (DLQ) planned for Kafka |

---

## Project Structure

```
microservices/
├── pom.xml                          # Root parent POM — version management
├── common-lib/                      # Shared DTOs, enums, events, exceptions
│   └── src/main/java/com/nikhil/common_lib/
│       ├── dto/                     # Shared data transfer objects
│       ├── embeddable/              # JPA embeddables (ContactInfo, Address, GeoCode)
│       ├── enums/                   # All shared enums (BookingStatus, CabinClassType, etc.)
│       ├── event/                   # Kafka event objects (BookingConfirmedEvent, etc.)
│       ├── exception/               # Custom exceptions
│       └── payload/
│           ├── request/             # Inbound request DTOs
│           └── response/            # Outbound response DTOs
│
├── cloud/                           # Infrastructure services
│   ├── api-gateway/                 # Spring Cloud Gateway — routing + JWT
│   ├── config-server/               # Spring Cloud Config Server
│   └── service-registry/            # Eureka Server
│
├── services/                        # Business microservices
│   ├── user-service/
│   ├── airline-core-service/
│   ├── location-service/
│   ├── flight-ops-service/
│   ├── seat-service/
│   ├── pricing-service/
│   ├── ancillary-service/
│   ├── booking-service/
│   ├── payment-service/
│   ├── notification-service/
│   └── subscription-service/
│
└── docker-compose/
    └── docker-compose.yml           # Full stack deployment
```

---

*Built with Spring Boot 4.0.2 · Spring Cloud 2025.1.0 · Apache Kafka 4.1.1 · MySQL 8.0*
