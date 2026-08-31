package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.ModelCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModelCategoryRepository extends JpaRepository<ModelCategory, Long> {
    List<ModelCategory> findByParentId(Long parentId);
    List<ModelCategory> findByParentIdIsNullOrderBySortOrder();
    List<ModelCategory> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
