package com.xiqin.modules.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "model_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "version_num", nullable = false)
    @Builder.Default
    private Integer versionNum = 1;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_type", nullable = false)
    private String fileType; // display, texture, other, thumbnail

    @Column(name = "file_format")
    private String fileFormat; // fbx, obj, gltf, glb, png, jpg, etc.

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "s3_bucket")
    private String s3Bucket;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
