package com.xiqin.modules.storage.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data @Builder
public class StorageLocationVO {
    private Long id;
    private String name;
    private String type;
    private String address;
    private String mountPath;
    private String username;
    private Boolean hasCredential;
    private String status;
    private Boolean current;
    private Boolean protectedLocation;
    private Long assetCount;
    private Long usedBytes;
    private LocalDateTime lastScanAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
