package com.nikhil.services.service.impl;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.PassengerRequest;
import com.nikhil.common_lib.payload.response.PassengerResponse;
import com.nikhil.services.mapper.PassengerMapper;
import com.nikhil.services.model.Passenger;
import com.nikhil.services.repository.PassengerRepository;
import com.nikhil.services.service.PassengerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service implementation responsible for local Passenger persistence
 * and passenger resolution during Booking creation.
 *
 * Passenger records are resolved using the following lookup strategy:
 *
 *     Passport number available?
 *              |
 *              +-- Yes --> Search by passport number
 *              |               |
 *              |               +-- Found --> Update existing Passenger
 *              |               |
 *              |               +-- Not found --> Continue fallback lookup
 *              |
 *              +-- No --------+
 *                              |
 *                              v
 *              Search by email + phone + date of birth
 *                              |
 *                    +---------+---------+
 *                    |                   |
 *                  Found              Not found
 *                    |                   |
 *                  Update              Create
 *                  existing            new Passenger
 *
 * Passenger data is stored locally in booking-service and is used while
 * constructing the Booking aggregate and generating passenger tickets.
 *
 * Sensitive passenger information such as passport number, email address,
 * phone number, and date of birth must not be written to application logs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PassengerServiceImpl implements PassengerService {

    private final PassengerRepository passengerRepository;


    /**
     * Creates a new Passenger record for the specified primary user.
     *
     * This method performs direct creation and does not execute the
     * find-or-create deduplication flow.
     *
     * @param request passenger information received from the booking request
     * @param userId authenticated user responsible for the Passenger record
     * @return persisted Passenger response
     * @throws ResourceNotFoundException retained from the service contract
     */
    @Override
    @Transactional
    public PassengerResponse createPassenger(
            PassengerRequest request,
            Long userId
    ) throws ResourceNotFoundException {

        log.debug(
                "Creating passenger record primaryUserId={}",
                userId
        );

        /*
         * Convert the incoming request into the local Passenger entity and
         * associate it with the authenticated user who owns the passenger
         * profile.
         */
        Passenger passenger =
                PassengerMapper.toEntity(request);

        passenger.setPrimaryUserId(userId);

        Passenger savedPassenger =
                passengerRepository.save(passenger);

        log.info(
                "Passenger created successfully passengerId={} primaryUserId={}",
                savedPassenger.getId(),
                userId
        );

        return PassengerMapper.toResponse(
                savedPassenger
        );
    }


    /**
     * Resolves a Passenger for the Booking creation workflow.
     *
     * If a matching Passenger already exists, the existing entity is updated
     * with the latest request data and returned.
     *
     * If no matching Passenger exists, a new Passenger record is created and
     * associated with the authenticated primary user.
     *
     * This method returns the managed entity because BookingServiceImpl needs
     * the Passenger entity when constructing the Booking aggregate.
     */
    @Override
    @Transactional
    public Passenger findOrCreatePassengerEntity(
            PassengerRequest request,
            Long userId
    ) {

        log.debug(
                "Resolving passenger for booking workflow primaryUserId={}",
                userId
        );

        /*
         * Attempt to identify an existing Passenger using the configured
         * lookup strategy.
         *
         * Passport number is checked first when supplied. If no match is
         * found, the lookup falls back to the email, phone, and date-of-birth
         * combination.
         */
        Optional<Passenger> existingPassenger =
                findExistingPassengerOptional(request);

        if (existingPassenger.isPresent()) {

            Passenger passenger =
                    existingPassenger.get();

            log.debug(
                    "Existing passenger resolved passengerId={} primaryUserId={}",
                    passenger.getId(),
                    userId
            );

            /*
             * Refresh the existing Passenger with the latest information from
             * the booking request.
             *
             * Because the entity is managed inside the current transaction,
             * JPA dirty checking can persist these changes automatically.
             */
            PassengerMapper.updateEntityFromRequest(
                    request,
                    passenger
            );

            log.info(
                    "Existing passenger updated passengerId={} primaryUserId={}",
                    passenger.getId(),
                    userId
            );

            return passenger;
        }

        /*
         * No matching Passenger was found. Create a new Passenger entity and
         * associate it with the authenticated user who initiated the Booking.
         */
        Passenger newPassenger =
                PassengerMapper.toEntity(request);

        newPassenger.setPrimaryUserId(userId);

        Passenger savedPassenger =
                passengerRepository.save(newPassenger);

        log.info(
                "New passenger created during booking workflow passengerId={} primaryUserId={}",
                savedPassenger.getId(),
                userId
        );

        return savedPassenger;
    }


    /**
     * Finds an existing Passenger using the configured identity lookup rules.
     *
     * Returns null when no matching Passenger exists because the current
     * PassengerService contract exposes a nullable return value.
     */
    @Override
    public Passenger findExistingPassenger(
            PassengerRequest request
    ) {

        log.debug(
                "Searching for existing passenger record"
        );

        Optional<Passenger> passenger =
                findExistingPassengerOptional(request);

        log.debug(
                "Passenger lookup completed found={}",
                passenger.isPresent()
        );

        return passenger.orElse(null);
    }


    /**
     * Checks whether a Passenger exists for the supplied local identifier.
     */
    @Override
    public boolean existsById(Long id) {

        log.debug(
                "Checking passenger existence passengerId={}",
                id
        );

        boolean exists =
                passengerRepository.existsById(id);

        log.debug(
                "Passenger existence check completed passengerId={} exists={}",
                id,
                exists
        );

        return exists;
    }


    /**
     * Returns the total number of Passenger records stored by Booking Service.
     */
    @Override
    public long count() {

        log.debug(
                "Counting passenger records"
        );

        long passengerCount =
                passengerRepository.count();

        log.debug(
                "Passenger count retrieved count={}",
                passengerCount
        );

        return passengerCount;
    }


    /**
     * Executes the internal Passenger deduplication lookup.
     *
     * Lookup priority:
     *
     *     1. Passport number, when provided
     *     2. Email + phone + date of birth
     *
     * Passport lookup is preferred because it represents a stronger identity
     * signal than contact information. The fallback composite lookup supports
     * passengers who do not provide passport information.
     *
     * No passenger PII is included in logs generated by this method.
     */
    private Optional<Passenger> findExistingPassengerOptional(
            PassengerRequest request
    ) {

        /*
         * Use passport number as the primary lookup key when available.
         */
        if (request.getPassportNumber() != null
                && !request.getPassportNumber().isBlank()) {

            log.debug(
                    "Attempting passenger lookup using passport identifier"
            );

            Optional<Passenger> passengerByPassport =
                    passengerRepository.findByPassportNumber(
                            request.getPassportNumber()
                    );

            if (passengerByPassport.isPresent()) {

                log.debug(
                        "Passenger resolved using passport identifier passengerId={}",
                        passengerByPassport.get().getId()
                );

                return passengerByPassport;
            }

            log.debug(
                    "No passenger found using passport identifier; applying fallback lookup"
            );
        }

        /*
         * Fall back to the composite identity lookup for passengers without
         * a matching passport-based record.
         */
        log.debug(
                "Attempting passenger lookup using composite contact and identity attributes"
        );

        Optional<Passenger> passenger =
                passengerRepository
                        .findByEmailAndPhoneAndDateOfBirth(
                                request.getEmail(),
                                request.getPhone(),
                                request.getDateOfBirth()
                        );

        log.debug(
                "Composite passenger lookup completed found={}",
                passenger.isPresent()
        );

        return passenger;
    }
}