package com.xiqin.modules.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UploadRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "file_count")
    @Builder.Default
    private Integer fileCount = 0;

    @Column(name = "total_size")
    @Builder.Default
    private Long totalSize = 0L;

    @Column(nullable = false)
    @Builder.Default
    private String status = "success";

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
