package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT p FROM Project p WHERE " +
           "(:pattern IS NULL OR LOWER(p.name) LIKE :pattern " +
           "OR LOWER(COALESCE(p.description, '')) LIKE :pattern) " +
           "AND (:categoryId IS NULL OR p.categoryId = :categoryId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:scope IS NULL " +
           "OR (:scope = 'created' AND p.createdBy = :userId) " +
           "OR (:scope = 'participated' AND p.createdBy <> :userId AND EXISTS " +
           "(SELECT pm.id FROM ProjectMember pm WHERE pm.projectId = p.id AND pm.userId = :userId))) " +
           "ORDER BY p.createdAt DESC")
    Page<Project> searchProjects(@Param("pattern") String pattern,
                                 @Param("categoryId") Long categoryId,
                                 @Param("status") String status,
                                 @Param("scope") String scope,
                                 @Param("userId") Long userId,
                                 Pageable pageable);
}
