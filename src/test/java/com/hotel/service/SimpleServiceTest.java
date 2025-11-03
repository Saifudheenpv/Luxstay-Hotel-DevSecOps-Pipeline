package com.hotel.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SimpleServiceTest {

    @Test
    void basicAssertionTest() {
        assertTrue(true, "Basic assertion should work");
    }

    @Test
    void mathOperationTest() {
        int result = 10 * 2;
        assertEquals(20, result, "Math operations should work correctly");
    }

    @Test
    void stringOperationTest() {
        String greeting = "Hello, World!";
        assertNotNull(greeting);
        assertTrue(greeting.contains("Hello"));
    }
}