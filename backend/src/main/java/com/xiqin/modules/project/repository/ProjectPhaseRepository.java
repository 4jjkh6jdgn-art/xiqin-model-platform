package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.ProjectPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, Long> {
    List<ProjectPhase> findByProjectIdOrderBySortOrder(Long projectId);
}
