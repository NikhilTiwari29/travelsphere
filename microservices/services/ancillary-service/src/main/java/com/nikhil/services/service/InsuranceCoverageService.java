package com.nikhil.services.service;

import com.nikhil.common_lib.exception.ResourceNotFoundException;
import com.nikhil.common_lib.payload.request.InsuranceCoverageRequest;
import com.nikhil.common_lib.payload.response.InsuranceCoverageResponse;

import java.util.List;

public interface InsuranceCoverageService {

    InsuranceCoverageResponse createCoverage(InsuranceCoverageRequest request) throws ResourceNotFoundException;

    List<InsuranceCoverageResponse> createCoveragesBulk(List<InsuranceCoverageRequest> requests) throws ResourceNotFoundException;

    InsuranceCoverageResponse updateCoverage(Long id, InsuranceCoverageRequest request) throws ResourceNotFoundException;

    void deleteCoverage(Long id) throws ResourceNotFoundException;

    InsuranceCoverageResponse getCoverageById(Long id) throws ResourceNotFoundException;

    List<InsuranceCoverageResponse> getCoveragesByAncillaryId(
            Long ancillaryId);

    List<InsuranceCoverageResponse> getActiveCoveragesByAncillaryId(
            Long ancillaryId);

    List<InsuranceCoverageResponse> getAllCoverages();
}
