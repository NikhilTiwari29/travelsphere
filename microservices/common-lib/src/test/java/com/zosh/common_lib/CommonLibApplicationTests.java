package com.nikhil.common_lib;

import org.junit.jupiter.api.Test;

/**
 * Test class for common-lib.
 *
 * NOTE: Do NOT use @SpringBootTest here — this is a library module, not a Spring Boot app.
 * @SpringBootTest tries to start a full Spring application context, which requires
 * @SpringBootApplication on a main class. Since we removed that (libraries shouldn't have it),
 * the test would fail with "Unable to find a @SpringBootConfiguration".
 *
 * Use plain JUnit 5 (@Test) for unit testing library code.
 */
class CommonLibApplicationTests {

    @Test
    void placeholder() {
        // Replace with real tests as you add shared DTOs, enums, and utilities
    }

}
