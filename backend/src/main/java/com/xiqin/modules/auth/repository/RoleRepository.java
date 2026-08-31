package com.xiqin.modules.auth.repository;

import com.xiqin.modules.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsByName(String name);
}
