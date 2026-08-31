package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.ModelCategoryLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ModelCategoryLinkRepository extends JpaRepository<ModelCategoryLink, Long> {
    List<ModelCategoryLink> findByModelId(Long modelId);
    List<ModelCategoryLink> findByModelIdIn(Collection<Long> modelIds);
    List<ModelCategoryLink> findByCategoryId(Long categoryId);
    @Query("select l.categoryId, count(distinct l.modelId) from ModelCategoryLink l group by l.categoryId")
    List<Object[]> countModelsByCategory();
    @Transactional void deleteByModelId(Long modelId);
}
