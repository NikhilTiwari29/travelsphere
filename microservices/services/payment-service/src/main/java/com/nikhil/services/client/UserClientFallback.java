package com.nikhil.services.client;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import org.springframework.stereotype.Component;

/*
 * Feign fallback when user-service is unavailable.
 * Returns null so PaymentServiceImpl can proceed without payer enrichment
 * rather than failing the entire payment initiation.
 */
@Component
public class UserClientFallback implements UserClient {

    @Override
    public UserDTO getUserById(Long userId) throws UserException {
        return null;
    }
}
