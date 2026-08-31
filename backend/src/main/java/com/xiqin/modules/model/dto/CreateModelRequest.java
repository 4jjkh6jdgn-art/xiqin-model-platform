package com.xiqin.modules.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateModelRequest {
    @NotBlank(message = "模型名称不能为空")
    private String name;
    private Long categoryId;
    private Long projectId;
    private List<Long> categoryIds;
    private List<Long> projectIds;
    private String description;
}
