package com.hotel.controller;

import com.hotel.dto.ProfileUpdateDTO;
import com.hotel.dto.UserDTO;
import com.hotel.mapper.UserMapper;
import com.hotel.model.User;
import com.hotel.service.BookingService;
import com.hotel.service.HotelService;
import com.hotel.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private BookingService bookingService;

    @Mock
    private HotelService hotelService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttributes;

    @Mock
    private HttpSession session;

    @Mock
    private BindingResult bindingResult;

    @InjectMocks
    private ProfileController profileController;

    private User user;
    private UserDTO userDTO;
    private ProfileUpdateDTO profileUpdateDTO;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPhone("1234567890");
        user.setAddress("Test Address");

        userDTO = new UserDTO();
        userDTO.setId(1L);
        userDTO.setUsername("testuser");
        userDTO.setEmail("test@example.com");

        profileUpdateDTO = new ProfileUpdateDTO();
        profileUpdateDTO.setFirstName("John");
        profileUpdateDTO.setLastName("Doe");
        profileUpdateDTO.setEmail("test@example.com");
        profileUpdateDTO.setPhone("1234567890");
        profileUpdateDTO.setAddress("Test Address");
    }

    @Test
    void viewProfile_WithValidUser_ShouldReturnProfileView() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDTO(user)).thenReturn(userDTO);

        String result = profileController.viewProfile(model, session);

        assertEquals("profile", result);
        verify(model).addAttribute("user", userDTO);
        verify(model).addAttribute("currentUser", userDTO);
        verify(model).addAttribute(eq("profileUpdateDTO"), any(ProfileUpdateDTO.class));
        verify(bookingService).getBookingsByUserId(1L);
        verify(hotelService).getAllHotels();
    }

    @Test
    void viewProfile_WithoutUser_ShouldRedirectToLogin() {
        when(session.getAttribute("userId")).thenReturn(null);

        String result = profileController.viewProfile(model, session);

        assertEquals("redirect:/auth/login", result);
        verify(userService, never()).findById(anyLong());
    }

    @Test
    void viewProfile_WithNonExistentUser_ShouldRedirectToLogin() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(userService.findById(1L)).thenReturn(Optional.empty());

        String result = profileController.viewProfile(model, session);

        assertEquals("redirect:/auth/login", result);
    }

    @Test
    void updateProfile_WithValidData_ShouldUpdateSuccessfully() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(bindingResult.hasErrors()).thenReturn(false);
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        when(userService.updateUser(any(User.class))).thenReturn(user);
        when(userMapper.toDTO(user)).thenReturn(userDTO);

        String result = profileController.updateProfile(profileUpdateDTO, bindingResult, session, redirectAttributes);

        assertEquals("redirect:/profile", result);
        verify(userService).updateUser(user);
        verify(session).setAttribute("user", userDTO);
        verify(redirectAttributes).addFlashAttribute("success", "Profile updated successfully!");
    }

    @Test
    void updateProfile_WithValidationErrors_ShouldRedirectWithErrors() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(bindingResult.hasErrors()).thenReturn(true);

        String result = profileController.updateProfile(profileUpdateDTO, bindingResult, session, redirectAttributes);

        assertEquals("redirect:/profile", result);
        verify(redirectAttributes).addFlashAttribute("org.springframework.validation.BindingResult.profileUpdateDTO", bindingResult);
        verify(redirectAttributes).addFlashAttribute("profileUpdateDTO", profileUpdateDTO);
        verify(userService, never()).updateUser(any(User.class));
    }

    @Test
    void updateProfile_WithoutUser_ShouldRedirectToLogin() {
        when(session.getAttribute("userId")).thenReturn(null);

        String result = profileController.updateProfile(profileUpdateDTO, bindingResult, session, redirectAttributes);

        assertEquals("redirect:/auth/login", result);
        verify(userService, never()).updateUser(any(User.class));
    }

    @Test
    void updateProfile_WithException_ShouldShowError() {
        when(session.getAttribute("userId")).thenReturn(1L);
        when(bindingResult.hasErrors()).thenReturn(false);
        when(userService.findById(1L)).thenReturn(Optional.of(user));
        when(userService.updateUser(any(User.class))).thenThrow(new RuntimeException("Database error"));

        String result = profileController.updateProfile(profileUpdateDTO, bindingResult, session, redirectAttributes);

        assertEquals("redirect:/profile", result);
        verify(redirectAttributes).addFlashAttribute("error", "Error updating profile: Database error");
    }
}
