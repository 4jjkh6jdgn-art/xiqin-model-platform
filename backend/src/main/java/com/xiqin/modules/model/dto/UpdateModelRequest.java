package com.xiqin.modules.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateModelRequest {
    private String name;
    private Long categoryId;
    private Long projectId;
    private List<Long> categoryIds;
    private List<Long> projectIds;
    private String description;
    private String status;
}
