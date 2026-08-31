package com.xiqin.modules.auth.repository;

import com.xiqin.modules.auth.entity.InvitationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InvitationCodeRepository extends JpaRepository<InvitationCode, Long> {
    Optional<InvitationCode> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvitationCode i where i.code = :code")
    Optional<InvitationCode> findByCodeForUpdate(@Param("code") String code);

    Page<InvitationCode> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<InvitationCode> findByStatusOrderByCreatedAtDesc(Integer status, Pageable pageable);

    long countByStatus(Integer status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InvitationCode i set i.status = 2 where i.status = 0 and i.expiresAt is not null and i.expiresAt <= :now")
    int expireOverdue(@Param("now") LocalDateTime now);
}
