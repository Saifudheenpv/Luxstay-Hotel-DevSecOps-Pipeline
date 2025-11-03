package com.hotel.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HotelModelTest {

    @Test
    public void testHotelCreation() {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        hotel.setName("Test Hotel");
        hotel.setCity("Test City");
        hotel.setCountry("Test Country");
        hotel.setAddress("Test Address");
        hotel.setRating(4.5);
        hotel.setStarRating(5);
        hotel.setDescription("Test Description");
        hotel.setEmail("hotel@test.com");
        hotel.setPhone("1234567890");

        assertEquals(1L, hotel.getId());
        assertEquals("Test Hotel", hotel.getName());
        assertEquals("Test City", hotel.getCity());
        assertEquals("Test Country", hotel.getCountry());
        assertEquals("Test Address", hotel.getAddress());
        assertEquals(4.5, hotel.getRating());
        assertEquals(5, hotel.getStarRating());
        assertEquals("Test Description", hotel.getDescription());
        assertEquals("hotel@test.com", hotel.getEmail());
        assertEquals("1234567890", hotel.getPhone());
    }
}
