package com.xiqin.modules.storage.repository;

import com.xiqin.modules.storage.entity.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {
    List<StorageLocation> findAllByOrderByCurrentDescCreatedAtAsc();
    Optional<StorageLocation> findByCurrentTrue();
}
