package com.secureuser.secureuserapi.domain.repository;

import com.secureuser.secureuserapi.domain.model.Role;
import com.secureuser.secureuserapi.domain.model.RoleName;

import java.util.Optional;

public interface RoleRepository {
    Optional<Role> findByName(RoleName name);
    Role save(Role role);
}
