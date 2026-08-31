package com.xiqin.modules.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistrationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    private Integer status = 0; // 0=pending, 1=approved, 2=rejected

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
