package com.xiqin.modules.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_versions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "version_num", nullable = false)
    private Integer versionNum;

    @Column(name = "change_log", columnDefinition = "TEXT")
    private String changeLog;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
