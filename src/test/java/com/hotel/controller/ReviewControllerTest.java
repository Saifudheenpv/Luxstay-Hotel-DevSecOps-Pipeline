package com.hotel.controller;

import com.hotel.model.Review;
import com.hotel.model.User;
import com.hotel.model.Booking;
import com.hotel.service.BookingService;
import com.hotel.service.ReviewService;
import com.hotel.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    @Mock
    private UserService userService;

    @Mock
    private BookingService bookingService;

    @Mock
    private RedirectAttributes redirectAttributes;

    @Mock
    private HttpSession session;

    @InjectMocks
    private ReviewController reviewController;

    private User user;
    private Booking booking;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        booking = new Booking();
        booking.setId(1L);
    }

    @Test
    void createReview_WithValidData_ShouldCreateReviewSuccessfully() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        when(reviewService.hasUserReviewedBooking(1L)).thenReturn(false);
        when(bookingService.getBookingById(1L)).thenReturn(Optional.of(booking));
        when(reviewService.createReview(any(Review.class))).thenReturn(new Review());

        String result = reviewController.createReview(1L, 1L, 5, "Great stay!", session, redirectAttributes);

        assertEquals("redirect:/bookings/my-bookings", result);
        verify(reviewService).createReview(any(Review.class));
        verify(redirectAttributes).addFlashAttribute("success", "Thank you for your review!");
    }

    @Test
    void createReview_WithoutUser_ShouldRedirectToLogin() {
        when(session.getAttribute("userId")).thenReturn(null);

        String result = reviewController.createReview(1L, 1L, 5, "Great stay!", session, redirectAttributes);

        assertEquals("redirect:/auth/login", result);
        verify(reviewService, never()).createReview(any(Review.class));
    }

    @Test
    void createReview_WithExistingReview_ShouldReturnError() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        when(reviewService.hasUserReviewedBooking(1L)).thenReturn(true);

        String result = reviewController.createReview(1L, 1L, 5, "Great stay!", session, redirectAttributes);

        assertEquals("redirect:/bookings/my-bookings", result);
        verify(redirectAttributes).addFlashAttribute("error", "You have already reviewed this booking");
        verify(reviewService, never()).createReview(any(Review.class));
    }

    @Test
    void createReview_WithException_ShouldReturnError() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        when(reviewService.hasUserReviewedBooking(1L)).thenReturn(false);
        when(bookingService.getBookingById(1L)).thenReturn(Optional.of(booking));
        when(reviewService.createReview(any(Review.class))).thenThrow(new RuntimeException("Database error"));

        String result = reviewController.createReview(1L, 1L, 5, "Great stay!", session, redirectAttributes);

        assertEquals("redirect:/bookings/my-bookings", result);
        verify(redirectAttributes).addFlashAttribute("error", "Error submitting review: Database error");
    }

    @Test
    void createReview_WithNonExistentUser_ShouldHandleGracefully() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.empty());

        String result = reviewController.createReview(1L, 1L, 5, "Great stay!", session, redirectAttributes);

        assertEquals("redirect:/bookings/my-bookings", result);
        verify(reviewService, never()).createReview(any(Review.class));
    }
}
