# User Service

## Purpose

`user-service` owns user identity data, registration, login, JWT issuing, and user lookup/profile APIs.

## Architecture

- `AuthController` exposes signup/login.
- `AuthServiceImpl` validates registration/login, rejects public `ROLE_SYSTEM_ADMIN` signup, BCrypt-hashes passwords, updates `lastLogin`, and creates JWTs.
- `JwtProvider` signs 24-hour JWTs with `email`, `userId`, and `authorities` claims.
- `UserController` exposes profile and user lookup APIs.
- HTTP security is stateless and permits requests; enforcement for protected routes is done at the API Gateway.

## APIs

- `GET /auth/`: simple auth home route.
- `POST /auth/signup`: register a user and return `AuthResponse`.
- `POST /auth/login`: validate credentials and return `AuthResponse`.
- `GET /api/users/profile`: current user profile, using gateway identity headers.
- `GET /api/users/{userId}`: user lookup.
- `GET /api/users`: user listing/search endpoint.

## Database

MySQL database: `airline_user`. Main entity: `User`, stored in `users`. Repository: `UserRepository`.

## Dependencies

Spring Web MVC, Spring Security, Spring Data JPA, MySQL, Eureka client, JJWT, Lombok, validation, Actuator, Prometheus, Zipkin tracing, datasource Micrometer instrumentation, Loki Logback appender, `common-lib`.

## Kafka Usage

No Kafka producers or consumers.

## Redis Usage

No direct Redis usage.

## Configuration

- Port: `${SERVER_PORT:5001}`
- `spring.application.name=user-service`
- Database: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Eureka: `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE`
- Observability: `ZIPKIN_ENDPOINT`, `LOKI_URL`

## Observability

Exposes health, info, metrics, and Prometheus through Actuator. Logs include trace/span correlation and can be shipped to Loki.

## Deployment

Docker image in Compose: `nikhiltiwarip29/gds-user:1.0.0`. Requires MySQL and Eureka. It is internal in `docker-compose.yml`; clients access it through the gateway.
