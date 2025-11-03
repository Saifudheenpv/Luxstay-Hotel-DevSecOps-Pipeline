package com.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class SimpleTest {

    @Test
    void contextLoads() {
        // Basic context loading test
        assertTrue(true, "Application context should load");
    }

    @Test
    void basicMathTest() {
        int result = 2 + 2;
        assertTrue(result == 4, "Basic math should work");
    }

    @Test
    void stringTest() {
        String message = "Hello, Hotel Booking System!";
        assertTrue(message.contains("Hotel"), "String should contain expected text");
    }
}