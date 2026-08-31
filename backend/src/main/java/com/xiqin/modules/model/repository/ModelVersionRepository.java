package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, Long> {
    List<ModelVersion> findByModelIdOrderByVersionNumDesc(Long modelId);
    boolean existsByModelIdAndVersionNum(Long modelId, Integer versionNum);
}
