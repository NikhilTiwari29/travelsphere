# Notification Service

## Purpose

`notification-service` sends customer notifications after booking confirmation. It consumes enriched booking events and sends HTML email plus optional SMS.

## Architecture

- `BookingNotificationListener` consumes `booking.confirmed`.
- `EmailService` renders `templates/email/booking-confirmation.html` with Thymeleaf and sends through Spring Mail.
- `SmsService` sends compact SMS messages through Twilio when enabled.
- The service does not own booking data and does not persist records.

## APIs

No domain REST controllers are defined. The service exposes Actuator endpoints and reacts to Kafka messages.

## Database

No database.

## Dependencies

Spring Web, Spring Kafka, Spring Mail, Thymeleaf, Twilio SDK, Eureka client, Actuator, Prometheus, Zipkin, Loki, `common-lib`.

## Kafka Usage

Consumer:

- `booking.confirmed`, group `notification-service-group`, payload `BookingConfirmedEvent`.

Producer: none.

## Redis Usage

No Redis usage.

## Configuration

- Port: `8094`
- `spring.application.name=notification-service`
- Kafka: bootstrap servers and consumer settings.
- Mail: SMTP host, port, username, password, TLS options.
- Twilio: `account-sid`, `auth-token`, `from-number`, `enabled`.
- Notification sender: `notification.from-email`, `notification.from-name`.

## Observability

Actuator health/info/metrics/Prometheus, Loki logging, and Zipkin/Tempo tracing are configured.

## Deployment

Docker image configured by Jib: `nikhiltiwarip29/gds-notification:1.0.0`. Requires Kafka, Eureka, SMTP credentials, and Twilio credentials if SMS is enabled. The main Compose file currently does not include this service, so add it to the deployment when notifications are required.
