package com.nikhil.services.controller;

import com.nikhil.common_lib.enums.AncillaryType;
import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.FlightCabinAncillaryRequest;
import com.nikhil.common_lib.payload.response.FlightCabinAncillaryResponse;
import com.nikhil.services.service.FlightCabinAncillaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing ancillary offerings configured for a flight and cabin class.
 *
 * A FlightCabinAncillary associates an airline ancillary product with a specific
 * flight and cabin class while maintaining offering-level attributes such as
 * price and quantity limits.
 *
 * Domain relationship:
 *
 * Flight
 *    +
 * CabinClass
 *    +
 * Ancillary
 *       ↓
 * FlightCabinAncillary
 *
 * The API is consumed by booking flows to retrieve ancillary offerings available
 * for a selected flight and cabin class and to calculate ancillary charges during
 * checkout.
 *
 * Gateway route:
 *   /api/flight-cabin-ancillaries/**
 */
@Slf4j
@RestController
@RequestMapping("/api/flight-cabin-ancillaries")
@RequiredArgsConstructor
public class FlightCabinAncillaryController {

    private final FlightCabinAncillaryService service;


    /**
     * Creates an ancillary offering for a flight and cabin class.
     */
    @PostMapping
    public ResponseEntity<FlightCabinAncillaryResponse> create(
            @Valid @RequestBody FlightCabinAncillaryRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to create flight cabin ancillary flightId={} cabinClassId={} ancillaryId={}",
                request.getFlightId(),
                request.getCabinClassId(),
                request.getAncillaryId()
        );

        FlightCabinAncillaryResponse response =
                service.create(request);

        log.info(
                "Flight cabin ancillary created successfully id={} flightId={} cabinClassId={} ancillaryId={}",
                response.getId(),
                request.getFlightId(),
                request.getCabinClassId(),
                request.getAncillaryId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    /**
     * Creates multiple flight-cabin ancillary offerings in a single request.
     *
     * Validation, duplicate handling, and transaction boundaries are delegated
     * to the service layer.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<FlightCabinAncillaryResponse>> bulkCreate(
            @Valid @RequestBody List<FlightCabinAncillaryRequest> requests
    ) throws ResourceNotFoundException {

        log.info(
                "Received bulk flight cabin ancillary creation request requestedCount={}",
                requests.size()
        );

        List<FlightCabinAncillaryResponse> responses =
                service.bulkCreate(requests);

        log.info(
                "Bulk flight cabin ancillary creation completed requestedCount={} createdCount={}",
                requests.size(),
                responses.size()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responses);
    }


    /**
     * Retrieves a flight-cabin ancillary offering by its unique identifier.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FlightCabinAncillaryResponse> getById(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.debug(
                "Received request to fetch flight cabin ancillary id={}",
                id
        );

        FlightCabinAncillaryResponse response =
                service.getById(id);

        log.debug(
                "Flight cabin ancillary fetched successfully id={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves multiple flight-cabin ancillary offerings by their identifiers.
     *
     * This endpoint supports downstream services that need to resolve multiple
     * selected ancillary line items without issuing one request per record.
     */
    @GetMapping("/all")
    public ResponseEntity<List<FlightCabinAncillaryResponse>> getAllByIds(
            @RequestParam List<Long> ids
    ) {

        log.debug(
                "Received request to fetch flight cabin ancillaries by IDs requestedCount={}",
                ids.size()
        );

        List<FlightCabinAncillaryResponse> responses =
                service.getAllByIds(ids);

        log.debug(
                "Flight cabin ancillaries fetched by IDs requestedCount={} resultCount={}",
                ids.size(),
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Retrieves all ancillary offerings configured for a flight and cabin class.
     *
     * Used by booking flows to present the complete ancillary catalog available
     * for the selected flight and cabin combination.
     */
    @GetMapping("/flight/{flightId}/cabin/{cabinClassId}")
    public ResponseEntity<List<FlightCabinAncillaryResponse>>
    getAllByFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId
    ) {

        log.debug(
                "Received request to fetch flight cabin ancillaries flightId={} cabinClassId={}",
                flightId,
                cabinClassId
        );

        List<FlightCabinAncillaryResponse> responses =
                service.getAllByFlightAndCabinClass(
                        flightId,
                        cabinClassId
                );

        log.debug(
                "Flight cabin ancillaries fetched successfully flightId={} cabinClassId={} count={}",
                flightId,
                cabinClassId,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Retrieves a single ancillary offering by flight, cabin class, and
     * ancillary type.
     *
     * This endpoint is appropriate only when the domain guarantees that a
     * flight and cabin combination has at most one offering for a given type.
     */
    @GetMapping(
            "/flight/{flightId}/cabin/{cabinClassId}/type/{type}"
    )
    public ResponseEntity<FlightCabinAncillaryResponse>
    getByFlightAndCabinClassAndType(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId,
            @PathVariable AncillaryType type
    ) throws ResourceNotFoundException {

        log.debug(
                "Received request to fetch flight cabin ancillary flightId={} cabinClassId={} type={}",
                flightId,
                cabinClassId,
                type
        );

        FlightCabinAncillaryResponse response =
                service.getByFlightIdAndCabinClassAndType(
                        flightId,
                        cabinClassId,
                        type
                );

        log.debug(
                "Flight cabin ancillary fetched successfully flightId={} cabinClassId={} type={}",
                flightId,
                cabinClassId,
                type
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Retrieves all ancillary offerings of a specific type configured for a
     * flight and cabin class.
     *
     * This endpoint supports ancillary types that can contain multiple products,
     * such as multiple baggage or travel-protection offerings.
     */
    @GetMapping(
            "/flight/{flightId}/cabin/{cabinClassId}/type/{type}/all"
    )
    public ResponseEntity<List<FlightCabinAncillaryResponse>>
    getAllByFlightAndCabinClassAndType(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId,
            @PathVariable AncillaryType type
    ) throws ResourceNotFoundException {

        log.debug(
                "Received request to fetch all flight cabin ancillaries by type flightId={} cabinClassId={} type={}",
                flightId,
                cabinClassId,
                type
        );

        List<FlightCabinAncillaryResponse> responses =
                service.getAllByFlightIdAndCabinClassAndType(
                        flightId,
                        cabinClassId,
                        type
                );

        log.debug(
                "Flight cabin ancillaries fetched by type flightId={} cabinClassId={} type={} count={}",
                flightId,
                cabinClassId,
                type,
                responses.size()
        );

        return ResponseEntity.ok(responses);
    }


    /**
     * Updates an existing flight-cabin ancillary offering.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FlightCabinAncillaryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody FlightCabinAncillaryRequest request
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to update flight cabin ancillary id={} flightId={} cabinClassId={} ancillaryId={}",
                id,
                request.getFlightId(),
                request.getCabinClassId(),
                request.getAncillaryId()
        );

        FlightCabinAncillaryResponse response =
                service.update(id, request);

        log.info(
                "Flight cabin ancillary updated successfully id={}",
                id
        );

        return ResponseEntity.ok(response);
    }


    /**
     * Deletes an existing flight-cabin ancillary offering.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) throws ResourceNotFoundException {

        log.info(
                "Received request to delete flight cabin ancillary id={}",
                id
        );

        service.delete(id);

        log.info(
                "Flight cabin ancillary deleted successfully id={}",
                id
        );

        return ResponseEntity.noContent().build();
    }


    /**
     * Calculates the aggregate price of selected flight-cabin ancillary
     * offerings.
     *
     * The request contains FlightCabinAncillary identifiers because pricing
     * is maintained at the flight and cabin offering level rather than on the
     * reusable ancillary catalog definition.
     *
     * Used by booking-service during checkout price calculation.
     */
    @PostMapping("/price/total")
    public ResponseEntity<Double> calculateAncillariesPrice(
            @RequestBody List<Long> flightCabinAncillaryIds
    ) {

        log.debug(
                "Received ancillary price calculation request requestedCount={}",
                flightCabinAncillaryIds.size()
        );

        Double totalPrice =
                service.calculateAncillaryPrice(
                        flightCabinAncillaryIds
                );

        log.debug(
                "Ancillary price calculation completed requestedCount={} totalPrice={}",
                flightCabinAncillaryIds.size(),
                totalPrice
        );

        return ResponseEntity.ok(totalPrice);
    }
}