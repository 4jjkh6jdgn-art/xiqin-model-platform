package com.xiqin.modules.project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "model_id")
    private Long modelId;

    @Column(nullable = false)
    private String name;

    @Column(name = "asset_type", nullable = false)
    private String assetType; // image, video

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
