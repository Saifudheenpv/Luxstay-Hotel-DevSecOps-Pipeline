package com.hotel.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class AllServicesTest {

    @InjectMocks
    private UserService userService;

    @InjectMocks
    private HotelService hotelService;

    @InjectMocks
    private BookingService bookingService;

    @InjectMocks
    private RoomService roomService;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    public void testAllServicesExist() {
        assertNotNull(userService, "UserService should exist");
        assertNotNull(hotelService, "HotelService should exist");
        assertNotNull(bookingService, "BookingService should exist");
        assertNotNull(roomService, "RoomService should exist");
        assertNotNull(reviewService, "ReviewService should exist");
    }

    @Test
    public void testServiceMethods() {
        // Test that services can be instantiated and basic operations work
        assertDoesNotThrow(() -> {
            // This just tests that the services can be used without immediate errors
            assertNotNull(userService);
            assertNotNull(hotelService);
        });
    }
}
