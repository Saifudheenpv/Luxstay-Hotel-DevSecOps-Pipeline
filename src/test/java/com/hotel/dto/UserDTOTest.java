package com.hotel.dto;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class UserDTOTest {

    @Test
    void userDTO_ShouldHaveCorrectGettersAndSetters() {
        UserDTO dto = new UserDTO();
        LocalDateTime now = LocalDateTime.now();
        
        dto.setId(1L);
        dto.setUsername("testuser");
        dto.setEmail("test@example.com");
        dto.setFirstName("John");
        dto.setLastName("Doe");
        dto.setPhone("1234567890");
        dto.setAddress("Test Address");
        dto.setCreatedAt(now);
        
        assertEquals(1L, dto.getId());
        assertEquals("testuser", dto.getUsername());
        assertEquals("test@example.com", dto.getEmail());
        assertEquals("John", dto.getFirstName());
        assertEquals("Doe", dto.getLastName());
        assertEquals("1234567890", dto.getPhone());
        assertEquals("Test Address", dto.getAddress());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void userDTO_Constructor_ShouldSetAllFields() {
        LocalDateTime now = LocalDateTime.now();
        UserDTO dto = new UserDTO(1L, "testuser", "test@example.com", 
                                 "John", "Doe", "1234567890", "Test Address", now);
        
        assertEquals(1L, dto.getId());
        assertEquals("testuser", dto.getUsername());
        assertEquals("test@example.com", dto.getEmail());
        assertEquals("John", dto.getFirstName());
        assertEquals("Doe", dto.getLastName());
        assertEquals("1234567890", dto.getPhone());
        assertEquals("Test Address", dto.getAddress());
        assertEquals(now, dto.getCreatedAt());
    }

    @Test
    void userDTO_ShouldHandleNullCreatedAt() {
        UserDTO dto = new UserDTO(1L, "testuser", "test@example.com", 
                                 "John", "Doe", "1234567890", "Test Address", null);
        
        assertNull(dto.getCreatedAt());
        assertEquals("testuser", dto.getUsername());
    }
}