package com.hotel.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class AllModelsTest {

    @Test
    public void testUserModel() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        assertEquals(1L, user.getId());
        assertEquals("testuser", user.getUsername());
    }

    @Test
    public void testHotelModel() {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        hotel.setName("Test Hotel");
        assertEquals(1L, hotel.getId());
        assertEquals("Test Hotel", hotel.getName());
    }

    @Test
    public void testBookingModel() {
        Booking booking = new Booking();
        booking.setId(1L);
        assertEquals(1L, booking.getId());
    }

    @Test
    public void testRoomModel() {
        Room room = new Room();
        room.setId(1L);
        room.setRoomNumber("101");
        assertEquals(1L, room.getId());
        assertEquals("101", room.getRoomNumber());
    }

    @Test
    public void testReviewModel() {
        Review review = new Review();
        review.setId(1L);
        review.setRating(5);
        assertEquals(1L, review.getId());
        assertEquals(5, review.getRating());
    }
}