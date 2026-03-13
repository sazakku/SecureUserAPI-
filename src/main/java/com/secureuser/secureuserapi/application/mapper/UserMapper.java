package com.secureuser.secureuserapi.application.mapper;

import com.secureuser.secureuserapi.application.dto.UserResponse;
import com.secureuser.secureuserapi.domain.model.Role;
import com.secureuser.secureuserapi.domain.model.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRoles().stream()
                        .map(Role::getName)
                        .map(Enum::name)
                        .collect(Collectors.toSet())
        );
    }
}
