package com.xiqin.modules.storage.service;

import io.minio.*;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class StorageService {

    private final MinioClient minioClient;

    public StorageService(@Qualifier("minioClient") MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Value("${app.minio.bucket-models}")
    private String bucketModels;

    @Value("${app.minio.bucket-thumbnails}")
    private String bucketThumbnails;

    @Value("${app.minio.bucket-avatars}")
    private String bucketAvatars;

    public String uploadFile(String bucket, String objectKey, MultipartFile file) {
        try {
            ensureBucket(bucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            log.info("Uploaded {} to {}/{}", file.getOriginalFilename(), bucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    public String uploadStream(String bucket, String objectKey, InputStream stream, long size, String contentType) {
        try {
            ensureBucket(bucket);
            // 已知大小用 -1 让 SDK 自动选择；未知大小（如 OnlyOffice 回调流）必须给定 partSize（最小 5MB），否则抛异常
            long partSize = size > 0 ? -1 : 5 * 1024 * 1024L;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(stream, size, partSize)
                            .contentType(contentType)
                            .build());
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to upload stream to MinIO", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    public InputStream downloadFile(String bucket, String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.error("Failed to download file from MinIO", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage(), e);
        }
    }

    public void deleteFile(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO", e);
        }
    }

    public List<String> listFiles(String bucket, String prefix) {
        List<String> files = new ArrayList<>();
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .recursive(true)
                            .build());
            for (Result<Item> result : results) {
                files.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.error("Failed to list files from MinIO", e);
        }
        return files;
    }

    public Map<String, Long> scanStatistics() {
        long count = 0L;
        long usedBytes = 0L;
        try {
            for (String bucket : List.of(bucketModels, bucketThumbnails, bucketAvatars)) {
                if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) continue;
                Iterable<Result<Item>> results = minioClient.listObjects(
                        ListObjectsArgs.builder().bucket(bucket).recursive(true).build());
                for (Result<Item> result : results) {
                    Item item = result.get();
                    count++;
                    usedBytes += item.size();
                }
            }
            Map<String, Long> stats = new LinkedHashMap<>();
            stats.put("assetCount", count);
            stats.put("usedBytes", usedBytes);
            return stats;
        } catch (Exception e) {
            throw new RuntimeException("对象存储连接失败: " + e.getMessage(), e);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created bucket: {}", bucket);
        }
    }

    public String getBucketModels() { return bucketModels; }
    public String getBucketThumbnails() { return bucketThumbnails; }
    public String getBucketAvatars() { return bucketAvatars; }
}
