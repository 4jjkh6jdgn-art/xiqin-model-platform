package com.xiqin.modules.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "download_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DownloadRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
