package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.ProjectFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFolderRepository extends JpaRepository<ProjectFolder, Long> {
    List<ProjectFolder> findByProjectIdOrderByNameAsc(Long projectId);
    boolean existsByProjectIdAndParentIdAndNameIgnoreCase(Long projectId, Long parentId, String name);
    Optional<ProjectFolder> findByProjectIdAndParentIdAndNameIgnoreCase(Long projectId, Long parentId, String name);
}
