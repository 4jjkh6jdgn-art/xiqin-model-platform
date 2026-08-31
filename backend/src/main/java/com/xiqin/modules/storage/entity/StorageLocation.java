package com.xiqin.modules.storage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "storage_locations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StorageLocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "mount_path", length = 500)
    private String mountPath;

    @Column(length = 120)
    private String username;

    @Column(name = "credential_secret", length = 500)
    private String credentialSecret;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "unknown";

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean current = false;

    @Column(name = "is_protected", nullable = false)
    @Builder.Default
    private Boolean protectedLocation = false;

    @Column(name = "asset_count", nullable = false)
    @Builder.Default
    private Long assetCount = 0L;

    @Column(name = "used_bytes", nullable = false)
    @Builder.Default
    private Long usedBytes = 0L;

    @Column(name = "last_scan_at")
    private LocalDateTime lastScanAt;

    @Column(name = "last_error", length = 800)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
