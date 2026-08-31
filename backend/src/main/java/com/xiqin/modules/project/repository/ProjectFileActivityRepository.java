package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.ProjectFileActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectFileActivityRepository extends JpaRepository<ProjectFileActivity, Long> {
    @Query("SELECT a FROM ProjectFileActivity a WHERE a.fileId = :fileId " +
           "AND (:keyword IS NULL OR LOWER(COALESCE(a.userName, '')) LIKE :keyword " +
           "OR LOWER(COALESCE(a.detail, '')) LIKE :keyword OR LOWER(a.action) LIKE :keyword) " +
           "ORDER BY a.createdAt DESC")
    List<ProjectFileActivity> searchByFileId(@Param("fileId") Long fileId, @Param("keyword") String keyword);
}
