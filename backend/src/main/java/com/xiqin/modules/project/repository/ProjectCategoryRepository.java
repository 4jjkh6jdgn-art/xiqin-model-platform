package com.xiqin.modules.project.repository;

import com.xiqin.modules.project.entity.ProjectCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectCategoryRepository extends JpaRepository<ProjectCategory, Long> {
    List<ProjectCategory> findByParentIdIsNullOrderBySortOrder();
    List<ProjectCategory> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
