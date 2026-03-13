package com.secureuser.secureuserapi.domain.repository;

import com.secureuser.secureuserapi.domain.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    User save(User user);
}
