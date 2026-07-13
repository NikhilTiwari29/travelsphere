package com.nikhil.services.clients;

import com.nikhil.common_lib.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/*
 * Feign client used by Booking Service to fetch passenger-owner details from
 * User Service for booking summaries and notifications.
 */
@FeignClient(name = "user-service", fallback = UserClientFallback.class)
public interface UserClient {

    /** Resolves booker profile for notification enrichment after payment.completed. */
    @GetMapping("/api/users/{userId}")
    UserDTO getUserById(@PathVariable Long userId);
}
