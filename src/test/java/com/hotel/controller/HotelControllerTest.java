package com.hotel.controller;

import com.hotel.model.Hotel;
import com.hotel.model.User;
import com.hotel.service.HotelService;
import com.hotel.service.ReviewService;
import com.hotel.service.RoomService;
import com.hotel.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import javax.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelControllerTest {

    @Mock
    private HotelService hotelService;

    @Mock
    private RoomService roomService;

    @Mock
    private UserService userService;

    @Mock
    private ReviewService reviewService;

    @Mock
    private Model model;

    @Mock
    private HttpSession session;

    @InjectMocks
    private HotelController hotelController;

    @Test
    void getAllHotels_ShouldReturnHotelsView() {
        when(session.getAttribute("userId")).thenReturn(null);

        String result = hotelController.getAllHotels(model, session);

        assertEquals("hotels", result);
        verify(hotelService).getAllHotels();
        verify(hotelService).getAllCities();
        verify(model).addAttribute("selectedCity", "");
        verify(model).addAttribute("searchQuery", "");
    }

    @Test
    void getAllHotels_WithLoggedInUser_ShouldAddUserToModel() {
        User user = new User();
        user.setId(1L);
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.of(user));

        String result = hotelController.getAllHotels(model, session);

        assertEquals("hotels", result);
        verify(model).addAttribute(eq("currentUser"), any(User.class));
    }

    @Test
    void getHotelById_WithValidHotel_ShouldReturnHotelRoomsView() {
        Hotel hotel = new Hotel();
        hotel.setId(1L);
        hotel.setName("Test Hotel");

        when(hotelService.getHotelById(1L)).thenReturn(Optional.of(hotel));
        when(session.getAttribute("userId")).thenReturn(null);

        String result = hotelController.getHotelById(1L, model, session);

        assertEquals("hotel-rooms", result);
        verify(model).addAttribute("hotel", hotel);
        verify(roomService).getAvailableRoomsByHotelId(1L);
        verify(reviewService).getReviewsByHotelId(1L);
        verify(reviewService).getAverageRatingForHotel(1L);
        verify(reviewService).getReviewCountForHotel(1L);
    }

    @Test
    void getHotelById_WithNonExistentHotel_ShouldStillReturnView() {
        when(hotelService.getHotelById(1L)).thenReturn(Optional.empty());
        when(session.getAttribute("userId")).thenReturn(null);

        String result = hotelController.getHotelById(1L, model, session);

        assertEquals("hotel-rooms", result);
        // Should not throw exception even if hotel doesn't exist
    }

    @Test
    void searchHotels_WithQuery_ShouldReturnFilteredHotels() {
        when(session.getAttribute("userId")).thenReturn(null);
        when(hotelService.searchHotels("test")).thenReturn(Arrays.asList(new Hotel()));

        String result = hotelController.searchHotels("test", null, model, session);

        assertEquals("hotels", result);
        verify(hotelService).searchHotels("test");
        verify(model).addAttribute("searchQuery", "test");
        verify(model).addAttribute("selectedCity", "");
    }

    @Test
    void searchHotels_WithCity_ShouldReturnCityHotels() {
        when(session.getAttribute("userId")).thenReturn(null);
        when(hotelService.getHotelsByCity("New York")).thenReturn(Arrays.asList(new Hotel()));

        String result = hotelController.searchHotels(null, "New York", model, session);

        assertEquals("hotels", result);
        verify(hotelService).getHotelsByCity("New York");
        verify(model).addAttribute("searchQuery", "");
        verify(model).addAttribute("selectedCity", "New York");
    }

    @Test
    void searchHotels_WithEmptyParams_ShouldReturnAllHotels() {
        when(session.getAttribute("userId")).thenReturn(null);

        String result = hotelController.searchHotels("", "", model, session);

        assertEquals("hotels", result);
        verify(hotelService).getAllHotels();
    }

    @Test
    void getHotelsByCity_ShouldReturnCityHotels() {
        when(session.getAttribute("userId")).thenReturn(null);

        String result = hotelController.getHotelsByCity("New York", model, session);

        assertEquals("hotels", result);
        verify(hotelService).getHotelsByCity("New York");
        verify(model).addAttribute("selectedCity", "New York");
        verify(model).addAttribute("searchQuery", "");
    }

    @Test
    void getHotelsByCity_WithLoggedInUser_ShouldAddUserToModel() {
        User user = new User();
        user.setId(1L);
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.of(user));

        String result = hotelController.getHotelsByCity("New York", model, session);

        assertEquals("hotels", result);
        verify(model).addAttribute(eq("currentUser"), any(User.class));
    }
}
