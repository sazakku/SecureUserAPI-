package com.secureuser.secureuserapi.application.service;

import com.secureuser.secureuserapi.application.dto.RegisterRequest;
import com.secureuser.secureuserapi.application.dto.UserResponse;
import com.secureuser.secureuserapi.application.exception.DuplicateResourceException;
import com.secureuser.secureuserapi.application.mapper.UserMapper;
import com.secureuser.secureuserapi.domain.model.Role;
import com.secureuser.secureuserapi.domain.model.RoleName;
import com.secureuser.secureuserapi.domain.model.User;
import com.secureuser.secureuserapi.domain.repository.RoleRepository;
import com.secureuser.secureuserapi.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username is already taken");
        }

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already in use");
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        Role roleUser = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role ROLE_USER not found. Ensure roles are seeded."));

        User user = User.builder()
                .username(username)
                .email(email)
                .password(encodedPassword)
                .roles(Set.of(roleUser))
                .build();

        User savedUser = userRepository.save(user);

        return userMapper.toResponse(savedUser);
    }
}
