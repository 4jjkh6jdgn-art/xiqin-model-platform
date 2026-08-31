package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.ProjectVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Long> {
    List<ProjectVersion> findByProjectIdOrderByVersionNumDesc(Long projectId);
    Optional<ProjectVersion> findByProjectIdAndVersionNum(Long projectId, Integer versionNum);
}
