package com.xiqin.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitation_codes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvitationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "used_by")
    private Long usedBy;

    @Column(nullable = false)
    @Builder.Default
    private Integer status = 0; // 0=unused, 1=used, 2=expired, 3=revoked

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
