package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.DownloadRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DownloadRecordRepository extends JpaRepository<DownloadRecord, Long> {
    Page<DownloadRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<DownloadRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT d.modelId, COUNT(d) FROM DownloadRecord d WHERE d.modelId IN :modelIds GROUP BY d.modelId")
    List<Object[]> countByModelIds(@Param("modelIds") Collection<Long> modelIds);
}
