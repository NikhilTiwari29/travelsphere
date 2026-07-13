package com.nikhil.services.client;

import com.nikhil.common_lib.dto.UserDTO;
import com.nikhil.common_lib.exception.UserException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/*
 * Feign client used by Payment Service to validate or enrich payment flows
 * with user information from User Service.
 */
@FeignClient(name = "user-service", fallback = UserClientFallback.class)
public interface UserClient {

    /** Loads payer name/email/phone for Razorpay payment-link customer block. */
    @GetMapping("/api/users/{userId}")
    UserDTO getUserById(
            @PathVariable Long userId) throws UserException;
}
