package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByProjectId(Long projectId);
    List<Asset> findByAssetType(String assetType);
}
