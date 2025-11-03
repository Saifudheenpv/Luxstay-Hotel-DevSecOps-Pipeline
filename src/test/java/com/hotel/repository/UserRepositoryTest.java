package com.hotel.repository;

import com.hotel.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testSaveUser() {
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("password");

        User saved = userRepository.save(user);
        
        assertNotNull(saved.getId());
        assertEquals("testuser", saved.getUsername());
        assertEquals("test@example.com", saved.getEmail());
    }

    @Test
    public void testFindUserById() {
        User user = new User();
        user.setUsername("testuser");
        user.setEmail("test@example.com");
        user.setPassword("password");

        User saved = userRepository.save(user);
        Optional<User> found = userRepository.findById(saved.getId());
        
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
    }
}
