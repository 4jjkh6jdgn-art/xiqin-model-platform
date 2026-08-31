package com.xiqin.modules.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "models")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Model {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String status = "draft"; // draft, processing, ready, archived

    @Builder.Default
    private Integer version = 1;

    @Column(name = "default_version", nullable = false)
    @Builder.Default
    private Integer defaultVersion = 1;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    /** 初始视角（相机位置/朝向 JSON），用于恢复 3D 预览视角 */
    @Column(name = "camera_view", columnDefinition = "TEXT")
    private String cameraView;

    /** 灯光配置 JSON（环境光强度、平行光强度、背景色等） */
    @Column(name = "lighting", columnDefinition = "TEXT")
    private String lighting;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** 文件总大小（字节），列表查询时聚合填充，非持久化字段 */
    @Transient
    private Long fileSize;

    @Transient
    private Integer fileCount;

    @Transient
    private List<String> fileFormats;

    @Transient
    private String categoryName;

    /** 列表页展示的所有分类；categoryId 保留为主分类，兼容旧接口。 */
    @Transient
    private List<Long> categoryIds;

    @Transient
    private List<String> categoryNames;

    @Transient
    private String projectName;

    /** 列表页展示的所有关联项目；projectId 保留为主项目，兼容旧接口。 */
    @Transient
    private List<Long> projectIds;

    @Transient
    private List<String> projectNames;

    @Transient
    private String createdByName;

    @Transient
    private Long downloadCount;
}
