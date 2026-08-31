package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.UploadRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRecordRepository extends JpaRepository<UploadRecord, Long> {
    Page<UploadRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<UploadRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
