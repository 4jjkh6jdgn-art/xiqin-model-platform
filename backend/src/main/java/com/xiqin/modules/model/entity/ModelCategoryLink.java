package com.xiqin.modules.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "model_category_links", uniqueConstraints =
        @UniqueConstraint(name = "uk_model_category_link", columnNames = {"model_id", "category_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ModelCategoryLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;
}
