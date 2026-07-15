# Payment Service

## Purpose

`payment-service` owns payment records, payment initiation, gateway verification, and payment lifecycle Kafka events.

## Architecture

- `PaymentController` exposes payment initiation, verification, batch lookup, and listing APIs.
- `PaymentServiceImpl` persists `PENDING` payments, creates Razorpay payment links, verifies gateway payment details, and updates status.
- `RazorpayService` integrates with Razorpay.
- Feign `UserClient` fetches payer details from `user-service`.
- `PaymentEventProducer` publishes payment outcome events.

## APIs

- `POST /api/payments/initiate`
- `POST /api/payments/verify`
- `POST /api/payments/batch/bookings`
- `GET /api/payments`

## Database

MySQL database: `airline_payment_db`. Entity: `Payment`, table `payments`.

## Dependencies

Spring Web MVC, Spring Data JPA, MySQL, Eureka client, OpenFeign, Kafka, Razorpay SDK, Resilience4j, validation, Actuator, Prometheus, Zipkin, Loki, datasource and Feign instrumentation, `common-lib`.

## Kafka Usage

Producers:

- `payment.completed`: emitted after successful gateway verification.
- `payment.failed`: emitted after failed gateway verification.

Consumers: none.

## Redis Usage

No Redis cache is configured in this service.

## Configuration

- Port: `5009`
- `spring.application.name=payment-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Kafka: `KAFKA_BOOTSTRAP_SERVERS`
- Razorpay: key/secret and payment link settings from service YAML/environment.
- Stripe configuration exists, but the service code contains placeholder Stripe behavior rather than a production integration.

## Observability

Actuator health/info/metrics/Prometheus, Loki logs, Zipkin/Tempo traces, datasource and Feign observations are configured.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-payment:1.0.0`. Requires MySQL, Kafka, Eureka, user-service, and payment provider credentials.
