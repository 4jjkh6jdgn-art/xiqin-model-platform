package com.xiqin.modules.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(nullable = false)
    @Builder.Default
    private String severity = "info";

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name")
    private String createdByName;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
