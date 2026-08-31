package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.ModelProjectLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ModelProjectLinkRepository extends JpaRepository<ModelProjectLink, Long> {
    List<ModelProjectLink> findByModelId(Long modelId);
    List<ModelProjectLink> findByModelIdIn(Collection<Long> modelIds);
    List<ModelProjectLink> findByProjectId(Long projectId);
    List<ModelProjectLink> findByProjectIdIn(Collection<Long> projectIds);
    boolean existsByModelIdAndProjectId(Long modelId, Long projectId);
    @Transactional void deleteByModelId(Long modelId);
    @Transactional void deleteByModelIdAndProjectId(Long modelId, Long projectId);
}
