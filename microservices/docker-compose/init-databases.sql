-- =============================================================================
-- MySQL init script: creates one database per microservice.
-- Mounted into /docker-entrypoint-initdb.d/ so MySQL runs it on first startup.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS airline_user;
CREATE DATABASE IF NOT EXISTS airline_core_db;
CREATE DATABASE IF NOT EXISTS airline_flight_db;
CREATE DATABASE IF NOT EXISTS airline_location_db;
CREATE DATABASE IF NOT EXISTS airline_seat_db;
CREATE DATABASE IF NOT EXISTS airline_pricing_db;
CREATE DATABASE IF NOT EXISTS airline_ancillary_db;
CREATE DATABASE IF NOT EXISTS airline_booking_db;
CREATE DATABASE IF NOT EXISTS airline_payment_db;
