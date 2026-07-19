package com.nikhil.services.service;

import com.nikhil.common_lib.payload.request.FareRulesRequest;
import com.nikhil.common_lib.payload.response.FareRulesResponse;

import java.util.List;

public interface FareRulesService {

    FareRulesResponse createFareRules(FareRulesRequest request);
    List<FareRulesResponse> createFareRules(List<FareRulesRequest> requests);
    FareRulesResponse getFareRulesById(Long id);
    FareRulesResponse getFareRulesByFareId(Long fareId);
    List<FareRulesResponse> getFareRulesByAirlineId(Long airlineId);
    FareRulesResponse updateFareRules(Long id, FareRulesRequest request);
    void deleteFareRules(Long id);
}
