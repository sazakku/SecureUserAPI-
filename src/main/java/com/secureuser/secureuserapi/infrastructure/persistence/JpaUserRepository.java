package com.secureuser.secureuserapi.infrastructure.persistence;

import com.secureuser.secureuserapi.domain.model.User;
import com.secureuser.secureuserapi.domain.repository.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaUserRepository extends JpaRepository<User, UUID>, UserRepository {

    @Override
    Optional<User> findByUsername(String username);

    @Override
    Optional<User> findByEmail(String email);

    @Override
    boolean existsByUsername(String username);

    @Override
    boolean existsByEmail(String email);
}
