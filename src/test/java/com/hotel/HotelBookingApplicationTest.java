package com.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class HotelBookingApplicationTest {

    @Test
    void contextLoads() {
        // Test that the application context loads successfully
        // This is a simple smoke test
    }
}