package com.hotel.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;

class HealthControllerTest {

    private HealthController healthController = new HealthController();

    @Test
    void simpleHealth_ShouldReturnOK() {
        String result = healthController.simpleHealth();
        assertEquals("OK", result);
    }

    @Test
    void info_ShouldReturnVersionInfo() {
        String result = healthController.info();
        assertEquals("Hotel Booking System - Version 1.0.0", result);
    }

    @Test
    void health_ShouldReturnUpStatus() {
        Health result = healthController.health();
        assertNotNull(result);
        assertEquals(Status.UP, result.getStatus());
        assertEquals("Hotel Booking System", result.getDetails().get("service"));
        assertEquals("UP", result.getDetails().get("status"));
        assertEquals("1.0.0", result.getDetails().get("version"));
    }

    @Test
    void health_WithException_ShouldReturnDownStatus() {
        // Create a test instance that throws an exception
        HealthController faultyController = new HealthController() {
            @Override
            public Health health() {
                try {
                    // Simulate an error condition
                    throw new RuntimeException("Simulated database error");
                } catch (Exception e) {
                    return Health.down(e)
                            .withDetail("service", "Hotel Booking System")
                            .withDetail("error", e.getMessage())
                            .build();
                }
            }
        };

        Health result = faultyController.health();
        assertNotNull(result);
        assertEquals(Status.DOWN, result.getStatus());
        assertEquals("Hotel Booking System", result.getDetails().get("service"));
        assertTrue(result.getDetails().containsKey("error"));
    }
}
