package com.xiqin.modules.auth.repository;

import com.xiqin.modules.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long countByStatus(Integer status);

    @Query("SELECT u FROM User u WHERE " +
           "(:pattern IS NULL OR LOWER(u.username) LIKE :pattern " +
           "OR LOWER(u.email) LIKE :pattern " +
           "OR LOWER(COALESCE(u.phone, '')) LIKE :pattern) " +
           "AND (:status IS NULL OR u.status = :status)")
    Page<User> searchUsers(@Param("pattern") String pattern, @Param("status") Integer status, Pageable pageable);
}
