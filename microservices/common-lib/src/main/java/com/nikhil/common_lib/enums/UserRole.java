package com.nikhil.common_lib.enums;

/**
 * Application roles embedded in JWT claims and enforced by the API Gateway.
 *
 * SYSTEM_ADMIN — platform ops; AIRLINE_OWNER — airline management APIs;
 * CUSTOMER — standard booking and profile access.
 */
public enum UserRole {
    ROLE_SYSTEM_ADMIN,
    ROLE_AIRLINE_OWNER,
    ROLE_CUSTOMER
}
