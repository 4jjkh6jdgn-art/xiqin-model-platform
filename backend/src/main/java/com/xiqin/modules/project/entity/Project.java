package com.xiqin.modules.project.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String status = "planning"; // planning, in_progress, completed, archived

    @Builder.Default
    private Integer priority = 1;

    @Column(name = "created_by")
    private Long createdBy;

    @JsonIgnore
    @Column(name = "cover_s3_key")
    private String coverS3Key;

    @JsonIgnore
    @Column(name = "cover_mime_type")
    private String coverMimeType;

    @JsonIgnore
    @Column(name = "cover_file_name")
    private String coverFileName;

    @Transient
    private String coverUrl;

    @Column(name = "current_version", nullable = false)
    @Builder.Default
    private Integer currentVersion = 1;

    @Column(name = "default_version", nullable = false)
    @Builder.Default
    private Integer defaultVersion = 1;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
