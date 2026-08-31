package com.xiqin.modules.project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "folder_id")
    private Long folderId;

    @Column(name = "relative_path")
    private String relativePath;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "s3_bucket")
    private String s3Bucket;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Builder.Default
    private Integer version = 1;

    @Column(nullable = false)
    @Builder.Default
    private String status = "available";

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
