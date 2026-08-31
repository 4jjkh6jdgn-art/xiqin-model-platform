package com.xiqin.modules.project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_file_activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectFileActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String action;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_name")
    private String userName;

    private String detail;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
