package com.xiqin.modules.model.repository;

import com.xiqin.modules.model.entity.ModelFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ModelFileRepository extends JpaRepository<ModelFile, Long> {
    List<ModelFile> findByModelId(Long modelId);
    List<ModelFile> findByModelIdAndFileType(Long modelId, String fileType);
    List<ModelFile> findByModelIdOrderBySortOrder(Long modelId);
    List<ModelFile> findByModelIdAndVersionNumOrderBySortOrder(Long modelId, Integer versionNum);
    Optional<ModelFile> findByModelIdAndVersionNumAndS3Key(Long modelId, Integer versionNum, String s3Key);
    long countByS3BucketAndS3Key(String s3Bucket, String s3Key);
    boolean existsByModelIdAndVersionNumAndFileNameIgnoreCaseAndIdNot(
            Long modelId, Integer versionNum, String fileName, Long id);

    @Query("SELECT f FROM ModelFile f JOIN Model m ON m.id = f.modelId " +
           "WHERE f.modelId IN :modelIds AND f.versionNum = m.version AND f.fileType <> 'thumbnail' " +
           "ORDER BY f.modelId, f.sortOrder")
    List<ModelFile> findLibraryFilesByModelIds(@Param("modelIds") Collection<Long> modelIds);

    @Transactional
    void deleteByModelId(Long modelId);

    @Query("SELECT f.modelId, COALESCE(SUM(f.fileSize), 0) FROM ModelFile f JOIN Model m ON m.id = f.modelId " +
           "WHERE f.modelId IN :modelIds AND f.versionNum = m.version AND f.fileType <> 'thumbnail' GROUP BY f.modelId")
    List<Object[]> sumFileSizeByModelIds(@Param("modelIds") Collection<Long> modelIds);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM ModelFile f")
    Long sumTotalFileSize();

    @Query("SELECT f FROM ModelFile f WHERE " +
           "LOWER(f.fileName) LIKE :pattern OR " +
           "LOWER(f.fileFormat) LIKE :pattern")
    List<ModelFile> searchFiles(@Param("pattern") String pattern);

    @Query("SELECT f FROM ModelFile f WHERE f.fileFormat = :format")
    List<ModelFile> findByFileFormat(@Param("format") String format);
}
