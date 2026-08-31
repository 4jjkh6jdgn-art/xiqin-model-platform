package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {
    List<ProjectFile> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<ProjectFile> findByProjectIdAndTaskId(Long projectId, Long taskId);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM ProjectFile f")
    Long sumTotalFileSize();
}
