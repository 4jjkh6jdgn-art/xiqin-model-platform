package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.ModificationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModificationRecordRepository extends JpaRepository<ModificationRecord, Long> {
    Page<ModificationRecord> findByModelIdOrderByCreatedAtDesc(Long modelId, Pageable pageable);
}
