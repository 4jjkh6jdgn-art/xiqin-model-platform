package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.Model;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ModelRepository extends JpaRepository<Model, Long> {

    @Query("SELECT m FROM Model m WHERE " +
           "(:pattern IS NULL OR LOWER(m.name) LIKE :pattern " +
           "OR LOWER(COALESCE(m.description, '')) LIKE :pattern) " +
           "AND (:categoryId IS NULL OR m.categoryId = :categoryId) " +
           "AND (:projectId IS NULL OR m.projectId = :projectId) " +
           "AND (:projectCategoryId IS NULL OR m.projectId IN " +
           "(SELECT p.id FROM Project p WHERE p.categoryId = :projectCategoryId)) " +
           "AND (:status IS NULL OR m.status = :status)")
    Page<Model> searchModels(@Param("pattern") String pattern,
                             @Param("categoryId") Long categoryId,
                             @Param("projectId") Long projectId,
                             @Param("projectCategoryId") Long projectCategoryId,
                             @Param("status") String status,
                             Pageable pageable);

    @Query("SELECT m FROM Model m WHERE m.id IN :modelIds " +
           "AND (:pattern IS NULL OR LOWER(m.name) LIKE :pattern OR LOWER(COALESCE(m.description, '')) LIKE :pattern) " +
           "AND (:status IS NULL OR m.status = :status)")
    Page<Model> searchModelsByIds(@Param("pattern") String pattern,
                                  @Param("status") String status,
                                  @Param("modelIds") Collection<Long> modelIds,
                                  Pageable pageable);

    @Query("SELECT m.categoryId, COUNT(m) FROM Model m WHERE m.categoryId IS NOT NULL GROUP BY m.categoryId")
    List<Object[]> countByModelCategory();

    @Query(value = "SELECT p.category_id, COUNT(m.id) FROM models m JOIN projects p ON p.id = m.project_id " +
            "WHERE p.category_id IS NOT NULL GROUP BY p.category_id", nativeQuery = true)
    List<Object[]> countByProjectCategory();

    @Query("SELECT m FROM Model m WHERE m.categoryId = :categoryId ORDER BY m.createdAt DESC")
    List<Model> findByCategoryId(@Param("categoryId") Long categoryId);

    @Query("SELECT m FROM Model m WHERE m.projectId = :projectId ORDER BY m.createdAt DESC")
    List<Model> findByProjectId(@Param("projectId") Long projectId);
}
