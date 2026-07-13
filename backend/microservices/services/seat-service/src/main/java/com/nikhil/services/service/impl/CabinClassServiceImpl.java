package com.nikhil.services.service.impl;

import com.nikhil.common_lib.enums.CabinClassType;
import com.nikhil.common_lib.payload.request.CabinClassRequest;
import com.nikhil.common_lib.payload.response.CabinClassResponse;
import com.nikhil.services.mapper.CabinClassMapper;
import com.nikhil.services.model.CabinClass;
import com.nikhil.services.repository.CabinClassRepository;
import com.nikhil.services.service.CabinClassService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation for aircraft cabin-class configuration.
 *
 * Manages cabin-class creation, bulk creation, retrieval, update, and deletion.
 * Cabin classes are scoped to an aircraft and represent travel classes such as
 * ECONOMY, PREMIUM_ECONOMY, BUSINESS, and FIRST_CLASS.
 *
 * Flight Operations Service resolves cabin classes by aircraft ID and
 * cabin-class type during flight search and downstream pricing operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CabinClassServiceImpl implements CabinClassService {

    private final CabinClassRepository cabinClassRepository;


    // ==================== Create Operations ====================

    /**
     * Creates a cabin-class definition for an aircraft.
     *
     * Cabin-class codes must be unique within the same aircraft configuration.
     */
    @Override
    @Transactional
    public CabinClassResponse createCabinClass(
            CabinClassRequest request
    ) {

        String cabinClassCode =
                request.getCode().toUpperCase();

        log.info(
                "Creating cabin class aircraftId={} cabinClassName={} cabinClassCode={}",
                request.getAircraftId(),
                request.getName(),
                cabinClassCode
        );

        if (cabinClassRepository.existsByCodeAndAircraftId(
                cabinClassCode,
                request.getAircraftId()
        )) {

            log.warn(
                    "Cabin class creation rejected: duplicate cabinClassCode={} aircraftId={}",
                    cabinClassCode,
                    request.getAircraftId()
            );

            throw new IllegalArgumentException(
                    "Cabin class with code '"
                            + request.getCode()
                            + "' already exists for this aircraft"
            );
        }

        CabinClass cabinClass =
                CabinClassMapper.toEntity(request);

        cabinClass.setAircraftId(
                request.getAircraftId()
        );

        CabinClass saved =
                cabinClassRepository.save(cabinClass);

        log.info(
                "Cabin class created successfully cabinClassId={} aircraftId={} cabinClassName={} cabinClassCode={}",
                saved.getId(),
                saved.getAircraftId(),
                saved.getName(),
                saved.getCode()
        );

        return CabinClassMapper.toResponse(
                saved,
                null
        );
    }


    /**
     * Creates multiple cabin classes in a single transaction.
     *
     * Cabin classes whose codes already exist for the target aircraft
     * are skipped.
     */
    @Override
    @Transactional
    public List<CabinClassResponse> createCabinClasses(
            List<CabinClassRequest> requests
    ) {

        log.info(
                "Bulk cabin class creation started requestedCount={}",
                requests.size()
        );

        List<CabinClass> toSave = requests.stream()
                .filter(request -> {

                    String cabinClassCode =
                            request.getCode().toUpperCase();

                    boolean exists =
                            cabinClassRepository.existsByCodeAndAircraftId(
                                    cabinClassCode,
                                    request.getAircraftId()
                            );

                    if (exists) {
                        log.warn(
                                "Skipping duplicate cabin class code={} aircraftId={}",
                                cabinClassCode,
                                request.getAircraftId()
                        );
                    }

                    return !exists;
                })
                .map(request -> {

                    CabinClass cabinClass =
                            CabinClassMapper.toEntity(request);

                    cabinClass.setAircraftId(
                            request.getAircraftId()
                    );

                    return cabinClass;
                })
                .toList();

        List<CabinClassResponse> responses =
                cabinClassRepository.saveAll(toSave)
                        .stream()
                        .map(cabinClass ->
                                CabinClassMapper.toResponse(
                                        cabinClass,
                                        null
                                )
                        )
                        .toList();

        log.info(
                "Bulk cabin class creation completed requestedCount={} createdCount={} skippedCount={}",
                requests.size(),
                responses.size(),
                requests.size() - responses.size()
        );

        return responses;
    }


    // ==================== Read Operations ====================

    /**
     * Returns a cabin class by its unique database ID.
     */
    @Override
    public CabinClassResponse getCabinClassById(Long id) {

        log.debug(
                "Fetching cabin class cabinClassId={}",
                id
        );

        CabinClass cabinClass =
                cabinClassRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cabin class lookup failed: cabinClassId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Cabin class not found with id: " + id
                            );
                        });

        return CabinClassMapper.toResponse(
                cabinClass,
                cabinClass.getSeatMap()
        );
    }


    /**
     * Returns all cabin classes configured for an aircraft.
     */
    @Override
    public List<CabinClassResponse> getCabinClassesByAircraftId(
            Long aircraftId
    ) {

        log.debug(
                "Fetching cabin classes for aircraftId={}",
                aircraftId
        );

        List<CabinClassResponse> responses =
                cabinClassRepository.findByAircraftId(aircraftId)
                        .stream()
                        .map(cabinClass ->
                                CabinClassMapper.toResponse(
                                        cabinClass,
                                        cabinClass.getSeatMap()
                                )
                        )
                        .toList();

        log.debug(
                "Cabin classes retrieved aircraftId={} count={}",
                aircraftId,
                responses.size()
        );

        return responses;
    }


    /**
     * Resolves a cabin class using aircraft ID and cabin-class type.
     *
     * This is the primary lookup used by Flight Operations Service
     * during flight search processing.
     */
    @Override
    public CabinClassResponse getByAircraftIdAndName(
            Long aircraftId,
            CabinClassType name
    ) {

        log.debug(
                "Resolving cabin class aircraftId={} cabinClass={}",
                aircraftId,
                name
        );

        CabinClass cabinClass =
                cabinClassRepository.findByAircraftIdAndName(
                        aircraftId,
                        name
                );

        if (cabinClass == null) {

            log.warn(
                    "Cabin class lookup failed aircraftId={} cabinClass={}",
                    aircraftId,
                    name
            );

            throw new EntityNotFoundException(
                    "Cabin class not found for aircraftId="
                            + aircraftId
                            + " and cabinClass="
                            + name
            );
        }

        return CabinClassMapper.toResponse(
                cabinClass,
                null
        );
    }


    // ==================== Update Operations ====================

    /**
     * Updates an existing cabin-class configuration.
     *
     * The cabin-class code must remain unique within the aircraft.
     */
    @Override
    @Transactional
    public CabinClassResponse updateCabinClass(
            Long id,
            CabinClassRequest request
    ) {

        log.info(
                "Updating cabin class cabinClassId={}",
                id
        );

        CabinClass existing =
                cabinClassRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cabin class update failed: cabinClassId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Cabin class not found with id: " + id
                            );
                        });

        String cabinClassCode =
                request.getCode().toUpperCase();

        /*
         * Exclude the current cabin-class record from the uniqueness check
         * so an unchanged code does not conflict with itself.
         */
        if (cabinClassRepository.existsByCodeAndAircraftIdAndIdNot(
                cabinClassCode,
                existing.getAircraftId(),
                id
        )) {

            log.warn(
                    "Cabin class update rejected: duplicate code={} aircraftId={} cabinClassId={}",
                    cabinClassCode,
                    existing.getAircraftId(),
                    id
            );

            throw new IllegalArgumentException(
                    "Cabin class with code '"
                            + request.getCode()
                            + "' already exists for this aircraft"
            );
        }

        CabinClassMapper.updateEntity(
                request,
                existing
        );

        CabinClass saved =
                cabinClassRepository.save(existing);

        log.info(
                "Cabin class updated successfully cabinClassId={} aircraftId={} code={}",
                saved.getId(),
                saved.getAircraftId(),
                saved.getCode()
        );

        return CabinClassMapper.toResponse(
                saved,
                saved.getSeatMap()
        );
    }


    // ==================== Delete Operations ====================

    /**
     * Deletes a cabin-class definition by its unique ID.
     */
    @Override
    @Transactional
    public void deleteCabinClass(Long id) {

        log.info(
                "Deleting cabin class cabinClassId={}",
                id
        );

        CabinClass cabinClass =
                cabinClassRepository.findById(id)
                        .orElseThrow(() -> {

                            log.warn(
                                    "Cabin class deletion failed: cabinClassId={} not found",
                                    id
                            );

                            return new EntityNotFoundException(
                                    "Cabin class not found with id: " + id
                            );
                        });

        cabinClassRepository.delete(cabinClass);

        log.info(
                "Cabin class deleted successfully cabinClassId={} aircraftId={} code={}",
                id,
                cabinClass.getAircraftId(),
                cabinClass.getCode()
        );
    }
}