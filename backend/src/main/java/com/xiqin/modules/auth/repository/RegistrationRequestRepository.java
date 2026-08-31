package com.xiqin.modules.auth.repository;

import com.xiqin.modules.auth.entity.RegistrationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, Long> {
    List<RegistrationRequest> findByStatus(Integer status);
    Page<RegistrationRequest> findByStatus(Integer status, Pageable pageable);
}
