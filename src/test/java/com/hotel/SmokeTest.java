package com.hotel;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SmokeTest {

    @Test
    public void contextLoads() {
        assertTrue(true);
    }

    @Test
    public void basicAssertion() {
        assertEquals(2, 1 + 1);
    }

    @Test
    public void applicationStarts() {
        assertDoesNotThrow(() -> HotelBookingApplication.main(new String[]{}));
    }
}
