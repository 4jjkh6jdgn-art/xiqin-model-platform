package com.xiqin.modules.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCategoryRequest {
    @NotBlank
    private String name;
    private Long parentId;
    private String code;
    private String description;
    private Integer sortOrder;
}
