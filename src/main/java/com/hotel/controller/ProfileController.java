package com.hotel.controller;

import com.hotel.dto.ProfileUpdateDTO;
import com.hotel.dto.UserDTO;
import com.hotel.mapper.UserMapper;
import com.hotel.model.User;
import com.hotel.service.BookingService;
import com.hotel.service.HotelService;
import com.hotel.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.Optional;

@Controller
@RequestMapping("/profile")
public class ProfileController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private BookingService bookingService;
    
    @Autowired
    private HotelService hotelService;
    
    @Autowired
    private UserMapper userMapper;
    
    @GetMapping
    public String viewProfile(Model model, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/auth/login";
        }
        
        Optional<User> userOpt = userService.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            UserDTO userDTO = userMapper.toDTO(user);
            model.addAttribute("user", userDTO);
            model.addAttribute("currentUser", userDTO);
            
            // Add profile update form
            if (!model.containsAttribute("profileUpdateDTO")) {
                ProfileUpdateDTO profileUpdateDTO = new ProfileUpdateDTO();
                profileUpdateDTO.setFirstName(user.getFirstName());
                profileUpdateDTO.setLastName(user.getLastName());
                profileUpdateDTO.setEmail(user.getEmail());
                profileUpdateDTO.setPhone(user.getPhone());
                profileUpdateDTO.setAddress(user.getAddress());
                model.addAttribute("profileUpdateDTO", profileUpdateDTO);
            }
            
            // Add statistics
            model.addAttribute("bookings", bookingService.getBookingsByUserId(userId));
            model.addAttribute("hotels", hotelService.getAllHotels());
            
            return "profile";
        } else {
            return "redirect:/auth/login";
        }
    }
    
    @PostMapping("/update")
    public String updateProfile(@Valid @ModelAttribute ProfileUpdateDTO profileUpdateDTO,
                               BindingResult bindingResult,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return "redirect:/auth/login";
        }
        
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.profileUpdateDTO", bindingResult);
            redirectAttributes.addFlashAttribute("profileUpdateDTO", profileUpdateDTO);
            return "redirect:/profile";
        }
        
        try {
            Optional<User> existingUserOpt = userService.findById(userId);
            if (existingUserOpt.isPresent()) {
                User existingUser = existingUserOpt.get();
                
                // Update only allowed fields
                existingUser.setFirstName(profileUpdateDTO.getFirstName());
                existingUser.setLastName(profileUpdateDTO.getLastName());
                existingUser.setEmail(profileUpdateDTO.getEmail());
                existingUser.setPhone(profileUpdateDTO.getPhone());
                existingUser.setAddress(profileUpdateDTO.getAddress());
                
                userService.updateUser(existingUser);
                
                // Update session with new data - use currentUser for consistency
                UserDTO updatedUserDTO = userMapper.toDTO(existingUser);
                session.setAttribute("currentUser", updatedUserDTO);
                
                redirectAttributes.addFlashAttribute("success", "Profile updated successfully!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating profile: " + e.getMessage());
        }
        
        return "redirect:/profile";
    }
}