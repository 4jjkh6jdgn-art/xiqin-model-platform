package com.xiqin.modules.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "model_projects", uniqueConstraints =
        @UniqueConstraint(name = "uk_model_project", columnNames = {"model_id", "project_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelProjectLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;
}
