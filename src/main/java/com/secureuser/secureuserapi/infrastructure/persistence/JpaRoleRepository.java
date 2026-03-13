package com.secureuser.secureuserapi.infrastructure.persistence;

import com.secureuser.secureuserapi.domain.model.Role;
import com.secureuser.secureuserapi.domain.model.RoleName;
import com.secureuser.secureuserapi.domain.repository.RoleRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaRoleRepository extends JpaRepository<Role, UUID>, RoleRepository {

    @Override
    Optional<Role> findByName(RoleName name);
}
