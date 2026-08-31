package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.ProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTaskRepository extends JpaRepository<ProjectTask, Long> {
    List<ProjectTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ProjectTask> findByAssigneeIdAndStatus(Long assigneeId, String status);
}
