package com.xiqin.modules.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelVO {
    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private Long projectId;
    private String projectName;
    private List<Long> categoryIds;
    private List<Long> projectIds;
    private List<Map<String, Object>> categories;
    private List<Map<String, Object>> projects;
    private String description;
    private String status;
    private Integer version;
    private Integer latestVersion;
    private Integer defaultVersion;
    private String thumbnailUrl;
    private String cameraView;
    private String lighting;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ModelFileVO> files;
    private List<ModelFileVO> thumbnailCandidates;
    private Long thumbnailCandidateId;
    private String displayFileUrl; // URL for 3D viewer
    private List<String> textureUrls;
    private Long fileSize; // 文件总大小（字节）
}
