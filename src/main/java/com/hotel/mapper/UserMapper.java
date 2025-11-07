package com.hotel.mapper;

import com.hotel.dto.UserDTO;
import com.hotel.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    
    public UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }
        
        return new UserDTO(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getAddress(),
            user.getCreatedAt()
        );
    }
    
    // If you have other mapping methods, keep them here
}