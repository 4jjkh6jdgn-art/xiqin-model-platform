package com.xiqin.modules.model.controller;

import com.xiqin.common.result.PageResult;
import com.xiqin.common.result.Result;
import com.xiqin.modules.model.dto.*;
import com.xiqin.modules.model.entity.*;
import com.xiqin.modules.model.service.ModelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;

    // ===================== Categories =====================
    @GetMapping("/categories")
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('model:view','model:category_view')")
    public Result<List<ModelCategory>> listCategories() {
        return Result.success(modelService.listCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:category_create')")
    public Result<ModelCategory> createCategory(@Valid @RequestBody CreateCategoryRequest req) {
        return Result.success(modelService.createCategory(req));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:category_edit')")
    public Result<ModelCategory> updateCategory(@PathVariable Long id, @RequestBody CreateCategoryRequest req) {
        return Result.success(modelService.updateCategory(id, req));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:category_delete')")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        modelService.deleteCategory(id);
        return Result.success();
    }

    // ===================== Models =====================
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<PageResult<Model>> listModels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long projectCategoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "time") String sortField,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Model> result = modelService.searchModels(
                keyword, categoryId, projectId, projectCategoryId, status, sortField, sortDirection, page, size);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/library-stats")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<Map<String, Object>> getLibraryStats() {
        return Result.success(modelService.getLibraryStats());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<ModelVO> getModel(@PathVariable Long id,
                                    @RequestParam(required = false) Integer version) {
        return Result.success(modelService.getModelDetail(id, version));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:upload')")
    public Result<Model> createModel(@Valid @RequestBody CreateModelRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.createModel(req, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:edit')")
    public Result<Model> updateModel(@PathVariable Long id, @RequestBody UpdateModelRequest req, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.updateModel(id, req, userId));
    }

    @PutMapping("/{id}/projects/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:edit')")
    public Result<Model> linkProject(@PathVariable Long id, @PathVariable Long projectId,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.linkProject(id, projectId, userId));
    }

    @DeleteMapping("/{id}/projects/{projectId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:edit')")
    public Result<Model> unlinkProject(@PathVariable Long id, @PathVariable Long projectId,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.unlinkProject(id, projectId, userId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:delete')")
    public Result<Void> deleteModel(@PathVariable Long id) {
        modelService.deleteModel(id);
        return Result.success();
    }

    // ===================== Scene Config (camera view + lighting) =====================
    @PutMapping("/{id}/scene-config")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:scene_manage')")
    public Result<Model> updateSceneConfig(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String cameraView = body.get("cameraView");
        String lighting = body.get("lighting");
        return Result.success(modelService.updateSceneConfig(id, cameraView, lighting, userId));
    }

    @PutMapping("/{id}/default-version")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:default_version_manage')")
    public Result<Model> setDefaultVersion(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.setDefaultVersion(id, body.get("version"), userId));
    }

    // ===================== Thumbnail (user-uploaded) =====================
    @PostMapping("/{id}/thumbnail")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:thumbnail_manage')")
    public Result<Model> uploadThumbnail(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.setThumbnail(id, file, userId));
    }

    @PutMapping("/{id}/thumbnail/{fileId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:thumbnail_manage')")
    public Result<Model> selectThumbnail(
            @PathVariable Long id,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.selectThumbnail(id, fileId, userId));
    }

    @DeleteMapping("/{id}/thumbnail/{fileId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:thumbnail_manage')")
    public Result<Void> deleteThumbnail(
            @PathVariable Long id,
            @PathVariable Long fileId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        modelService.deleteThumbnail(id, fileId, userId);
        return Result.success();
    }

    // ===================== Folder Upload =====================
    @PostMapping("/upload-folder")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:upload')")
    public Result<Model> uploadFolder(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("modelName") String modelName,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "categoryIds", required = false) String categoryIds,
            @RequestParam(value = "projectIds", required = false) String projectIds,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "fileTypes", required = false) String fileTypes,
            @RequestParam(value = "filePaths", required = false) String filePaths,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.uploadFolder(files, modelName, categoryId, projectId,
                categoryIds, projectIds, description, fileTypes, filePaths, userId));
    }

    // ===================== File Search =====================
    @GetMapping("/files/search")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<List<Map<String, Object>>> searchFiles(@RequestParam String keyword) {
        return Result.success(modelService.searchFiles(keyword));
    }

    // ===================== Download =====================
    @PostMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:download')")
    public Result<Map<String, String>> downloadModel(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String url = modelService.downloadModel(id, userId);
        return Result.success(Map.of("url", url));
    }

    @GetMapping("/{id}/versions/{version}/download.zip")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:download')")
    public void downloadModelArchive(
            @PathVariable Long id,
            @PathVariable Integer version,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        Long userId = (Long) request.getAttribute("userId");
        modelService.streamModelArchive(id, version, userId, response);
    }

    @PostMapping("/files/{fileId}/download")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:download')")
    public Result<Map<String, String>> downloadFile(@PathVariable Long fileId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String url = modelService.downloadFile(fileId, userId);
        return Result.success(Map.of("url", url));
    }

    @PutMapping("/files/{fileId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:file_edit')")
    public Result<Void> renameFile(
            @PathVariable Long fileId,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        modelService.renameFile(fileId, body.get("fileName"), userId);
        return Result.success();
    }

    @DeleteMapping("/files/{fileId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:file_delete')")
    public Result<Void> deleteFile(@PathVariable Long fileId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        modelService.deleteFile(fileId, userId);
        return Result.success();
    }

    // ===================== Same-origin streaming proxy =====================
    @GetMapping("/files/{fileId}/raw")
    public void streamFile(@PathVariable Long fileId, HttpServletResponse response) throws Exception {
        modelService.streamFile(fileId, response);
    }

    @GetMapping("/{id}/thumbnail/raw")
    public void streamThumbnail(@PathVariable Long id, HttpServletResponse response) throws Exception {
        modelService.streamThumbnail(id, response);
    }

    // ===================== Versions =====================
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:view')")
    public Result<List<ModelVersion>> getVersions(@PathVariable Long id) {
        return Result.success(modelService.getModelVersions(id));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:upload')")
    public Result<ModelVersion> createVersion(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.createVersion(id, body.get("changeLog"), userId));
    }

    @PostMapping("/{id}/versions/upload")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:upload')")
    public Result<ModelVersion> uploadVersion(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "changeLog", required = false) String changeLog,
            @RequestParam(value = "fileTypes", required = false) String fileTypes,
            @RequestParam(value = "filePaths", required = false) String filePaths,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.uploadVersion(id, files, changeLog, fileTypes, filePaths, userId));
    }

    @PostMapping("/{id}/versions/{version}/files")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:upload')")
    public Result<List<ModelFile>> addVersionFiles(
            @PathVariable Long id,
            @PathVariable Integer version,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "fileTypes", required = false) String fileTypes,
            @RequestParam(value = "filePaths", required = false) String filePaths,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(modelService.addVersionFiles(id, version, files, fileTypes, filePaths, userId));
    }

    // ===================== Records =====================
    @GetMapping("/records/processing")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:process_records_view')")
    public Result<PageResult<Map<String, Object>>> processingRecords(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Model> result = modelService.searchModels(
                null, null, null, null, status, "time", "desc", page, size);
        List<Map<String, Object>> summaries = result.getContent().stream().map(model -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", model.getId());
            item.put("name", model.getName());
            item.put("status", model.getStatus());
            item.put("version", model.getVersion());
            item.put("fileCount", model.getFileCount());
            item.put("fileSize", model.getFileSize());
            item.put("createdAt", model.getCreatedAt());
            item.put("updatedAt", model.getUpdatedAt());
            return item;
        }).toList();
        return Result.success(PageResult.<Map<String, Object>>builder()
                .list(summaries)
                .total(result.getTotalElements())
                .page(result.getNumber())
                .size(result.getSize())
                .totalPages(result.getTotalPages())
                .build());
    }

    @GetMapping("/records/uploads")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:upload_records_view')")
    public Result<PageResult<UploadRecord>> uploadRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(PageResult.of(modelService.getUploadRecords(userId, page, size)));
    }

    @GetMapping("/records/downloads")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:download_records_view')")
    public Result<PageResult<DownloadRecord>> downloadRecords(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(PageResult.of(modelService.getDownloadRecords(userId, page, size)));
    }

    @GetMapping("/{id}/records/modifications")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('model:history_view')")
    public Result<PageResult<ModificationRecord>> modificationRecords(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(PageResult.of(modelService.getModificationRecords(id, page, size)));
    }

    // ===================== Worker Callback =====================
    @PostMapping("/{id}/process-complete")
    public Result<Void> processComplete(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        String thumbnailKey = (String) body.get("thumbnailKey");
        String convertedFileKey = (String) body.get("convertedFileKey");
        String convertedFileFormat = (String) body.get("convertedFileFormat");
        Number versionValue = (Number) body.get("versionNum");
        Integer versionNum = versionValue != null ? versionValue.intValue() : null;
        modelService.onModelProcessed(id, versionNum, status, thumbnailKey, convertedFileKey, convertedFileFormat);
        return Result.success();
    }
}
