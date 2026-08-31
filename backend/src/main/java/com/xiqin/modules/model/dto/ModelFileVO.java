package com.xiqin.modules.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelFileVO {
    private Long id;
    private String fileName;
    private String filePath;
    private String fileType;
    private String fileFormat;
    private Long fileSize;
    private String mimeType;
    private String url;
    private Integer sortOrder;
}
