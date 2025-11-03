package com.hotel.service;

import com.hotel.model.User;
import com.hotel.repository.UserRepository;
import com.hotel.util.SimplePasswordEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimplePasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    public void testFindUserById() {
        // Setup
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Execute
        Optional<User> result = userService.findById(1L);

        // Verify
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        assertEquals("test@example.com", result.get().getEmail());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    public void testUpdateUser() {
        // Setup
        User user = new User();
        user.setId(1L);
        user.setUsername("updateduser");
        user.setEmail("updated@example.com");
        
        when(userRepository.save(user)).thenReturn(user);

        // Execute
        User updated = userService.updateUser(user);

        // Verify
        assertNotNull(updated);
        assertEquals("updateduser", updated.getUsername());
        assertEquals("updated@example.com", updated.getEmail());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    public void testExistsByUsername() {
        // Setup
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // Execute
        boolean exists = userService.existsByUsername("existinguser");

        // Verify
        assertTrue(exists);
        verify(userRepository, times(1)).existsByUsername("existinguser");
    }
}
