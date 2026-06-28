package com.nikhil.services.service;

import com.nikhil.common_lib.payload.request.BaggagePolicyRequest;
import com.nikhil.common_lib.payload.response.BaggagePolicyResponse;

import java.util.List;

public interface BaggagePolicyService {

    BaggagePolicyResponse createBaggagePolicy(BaggagePolicyRequest request);
    List<BaggagePolicyResponse> createBaggagePolicies(List<BaggagePolicyRequest> requests);
    BaggagePolicyResponse getBaggagePolicyById(Long id);
    BaggagePolicyResponse getBaggagePolicyByFareId(Long fareId);
    List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(Long airlineId);
    BaggagePolicyResponse updateBaggagePolicy(Long id, BaggagePolicyRequest request);
    void deleteBaggagePolicy(Long id);
}
