package com.xiqin.modules.project.controller;

import com.xiqin.common.result.PageResult;
import com.xiqin.common.result.Result;
import com.xiqin.modules.project.entity.*;
import com.xiqin.modules.project.service.ProjectService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    // ===================== Categories =====================
    @GetMapping("/categories")
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('project:view','project:category_view')")
    public Result<List<ProjectCategory>> listCategories() {
        return Result.success(projectService.listCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:category_create')")
    public Result<ProjectCategory> createCategory(@RequestBody Map<String, String> body) {
        return Result.success(projectService.createCategory(
                body.get("name"), body.get("code"), body.get("description")));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:category_edit')")
    public Result<ProjectCategory> updateCategory(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.success(projectService.updateCategory(
                id, body.get("name"), body.get("code"), body.get("description")));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:category_delete')")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        projectService.deleteCategory(id);
        return Result.success();
    }

    // ===================== Projects =====================
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:view')")
    public Result<PageResult<Project>> listProjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Page<Project> result = projectService.searchProjects(keyword, categoryId, status, scope, userId, page, size);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:view')")
    public Result<Project> getProject(@PathVariable Long id) {
        return Result.success(projectService.getProject(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:create')")
    public Result<Project> createProject(@RequestBody Project project, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(projectService.createProject(project, userId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:edit')")
    public Result<Project> updateProject(@PathVariable Long id, @RequestBody Project update,
                                         @RequestParam(required = false) String changeLog,
                                         HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(projectService.updateProject(id, update, userId, changeLog));
    }

    @PostMapping("/{id}/cover")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:cover_manage')")
    public Result<Project> updateProjectCover(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return Result.success(projectService.updateProjectCover(id, file));
    }

    @GetMapping("/{id}/cover")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:view')")
    public void getProjectCover(@PathVariable Long id, HttpServletResponse response) {
        String fileName = projectService.getProjectCoverFileName(id);
        response.setContentType(projectService.getProjectCoverMimeType(id));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" +
                URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
        try (InputStream in = projectService.getProjectCover(id)) {
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.error("读取项目封面失败: projectId={}", id, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("/{id}/cover")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:cover_manage')")
    public Result<Project> removeProjectCover(@PathVariable Long id) {
        return Result.success(projectService.removeProjectCover(id));
    }

    @GetMapping("/{projectId}/versions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:version_view')")
    public Result<List<ProjectVersion>> getVersions(@PathVariable Long projectId) {
        return Result.success(projectService.getVersions(projectId));
    }

    @GetMapping("/{projectId}/versions/{versionNum}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:version_view')")
    public Result<ProjectVersion> getVersion(@PathVariable Long projectId, @PathVariable Integer versionNum) {
        return Result.success(projectService.getVersion(projectId, versionNum));
    }

    @PutMapping("/{projectId}/default-version")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:default_version_manage')")
    public Result<Project> setDefaultVersion(@PathVariable Long projectId,
                                             @RequestBody Map<String, Integer> body) {
        return Result.success(projectService.setDefaultVersion(projectId, body.get("version")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:delete')")
    public Result<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return Result.success();
    }

    // ===================== Members =====================
    @GetMapping("/{projectId}/members")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:member_view')")
    public Result<List<Map<String, Object>>> getMembers(@PathVariable Long projectId) {
        return Result.success(projectService.getMemberDetails(projectId));
    }

    @GetMapping("/{projectId}/member-candidates")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:member_manage')")
    public Result<List<Map<String, Object>>> getMemberCandidates(@PathVariable Long projectId,
                                                                  @RequestParam(required = false) String keyword) {
        return Result.success(projectService.getMemberCandidates(projectId, keyword));
    }

    @PostMapping("/{projectId}/members")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:member_manage')")
    public Result<Void> addMember(@PathVariable Long projectId, @RequestBody Map<String, Object> body,
                                  HttpServletRequest request) {
        List<Long> userIds;
        if (body.get("userIds") instanceof List<?> values) {
            userIds = values.stream().map(value -> Long.valueOf(value.toString())).toList();
        } else if (body.get("userId") != null) {
            userIds = List.of(Long.valueOf(body.get("userId").toString()));
        } else {
            userIds = List.of();
        }
        String role = body.getOrDefault("role", "member").toString();
        Long actorUserId = (Long) request.getAttribute("userId");
        projectService.addMembers(projectId, userIds, role, actorUserId);
        return Result.success();
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:member_manage')")
    public Result<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(projectId, userId);
        return Result.success();
    }

    // ===================== Tasks =====================
    @GetMapping("/{projectId}/tasks")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:task_view')")
    public Result<List<ProjectTask>> getTasks(@PathVariable Long projectId) {
        return Result.success(projectService.getTasks(projectId));
    }

    @PostMapping("/{projectId}/tasks")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:task_create')")
    public Result<ProjectTask> createTask(@PathVariable Long projectId, @RequestBody ProjectTask task, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        task.setProjectId(projectId);
        task.setCreatedBy(userId);
        return Result.success(projectService.createTask(task));
    }

    @PutMapping("/tasks/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:task_edit')")
    public Result<ProjectTask> updateTask(@PathVariable Long id, @RequestBody ProjectTask update) {
        return Result.success(projectService.updateTask(id, update));
    }

    @DeleteMapping("/tasks/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:task_delete')")
    public Result<Void> deleteTask(@PathVariable Long id) {
        projectService.deleteTask(id);
        return Result.success();
    }

    // ===================== Phases =====================
    @GetMapping("/{projectId}/phases")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:stage_view')")
    public Result<List<ProjectPhase>> getPhases(@PathVariable Long projectId) {
        return Result.success(projectService.getPhases(projectId));
    }

    @PostMapping("/{projectId}/phases")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:stage_manage')")
    public Result<ProjectPhase> createPhase(@PathVariable Long projectId, @RequestBody ProjectPhase phase) {
        phase.setProjectId(projectId);
        return Result.success(projectService.createPhase(phase));
    }

    // ===================== Files =====================
    @GetMapping("/{projectId}/files")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_view')")
    public Result<List<ProjectFile>> getFiles(@PathVariable Long projectId) {
        return Result.success(projectService.getFiles(projectId));
    }

    @GetMapping("/{projectId}/folders")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_view')")
    public Result<List<ProjectFolder>> getFolders(@PathVariable Long projectId) {
        return Result.success(projectService.getFolders(projectId));
    }

    @PostMapping("/{projectId}/folders")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:folder_manage')")
    public Result<ProjectFolder> createFolder(@PathVariable Long projectId,
                                               @RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long parentId = body.get("parentId") == null || body.get("parentId").toString().isBlank()
                ? null : Long.valueOf(body.get("parentId").toString());
        return Result.success(projectService.createFolder(projectId, parentId,
                String.valueOf(body.getOrDefault("name", "")), userId));
    }

    @PostMapping("/{projectId}/files")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_upload')")
    public Result<ProjectFile> uploadFile(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskId", required = false) Long taskId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "relativePath", required = false) String relativePath,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(projectService.uploadFile(projectId, file, taskId, userId, folderId, relativePath));
    }

    @PostMapping("/{projectId}/folders/upload")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_upload')")
    public Result<List<ProjectFile>> uploadFolder(@PathVariable Long projectId,
                                                   @RequestParam("files") MultipartFile[] files,
                                                   @RequestParam("relativePaths") String[] relativePaths,
                                                   @RequestParam(value = "targetFolderId", required = false) Long targetFolderId,
                                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(projectService.uploadFolder(projectId, files, relativePaths, targetFolderId, userId));
    }

    @PutMapping("/files/{fileId}/folder")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_edit')")
    public Result<ProjectFile> moveFile(@PathVariable Long fileId,
                                        @RequestBody Map<String, Object> body,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long folderId = body.get("folderId") == null || body.get("folderId").toString().isBlank()
                ? null : Long.valueOf(body.get("folderId").toString());
        return Result.success(projectService.moveFile(fileId, folderId, userId));
    }

    @GetMapping("/files/{fileId}/activities")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_activity_view')")
    public Result<List<ProjectFileActivity>> getFileActivities(@PathVariable Long fileId,
                                                                @RequestParam(required = false) String keyword) {
        return Result.success(projectService.getFileActivities(fileId, keyword));
    }

    @PutMapping("/files/{fileId}/review")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_edit')")
    public Result<Void> reviewFile(@PathVariable Long fileId, @RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        projectService.reviewFile(fileId, body.get("status"), userId);
        return Result.success();
    }

    @GetMapping("/files/{fileId}/preview")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_view')")
    public void previewFile(@PathVariable Long fileId, HttpServletResponse response) {
        streamProjectFile(fileId, true, response);
    }

    @GetMapping("/files/{fileId}/download")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_download')")
    public void downloadFile(@PathVariable Long fileId,
                             @RequestParam(defaultValue = "true") boolean record,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        String fileName = projectService.getFileName(fileId);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20") + "\"");
        Long userId = (Long) request.getAttribute("userId");
        try (InputStream in = projectService.downloadFile(fileId, userId, record)) {
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.error("下载文件失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // 原始文件流（供 OnlyOffice 拉取，保持正确 Content-Type）
    @GetMapping("/files/{fileId}/raw")
    public void rawFile(@PathVariable Long fileId, HttpServletResponse response) {
        streamProjectFile(fileId, true, response);
    }

    private void streamProjectFile(Long fileId, boolean inline, HttpServletResponse response) {
        String fileName = projectService.getFileName(fileId);
        String storedMime = projectService.getFileMimeType(fileId);
        String mime = storedMime != null && !storedMime.isBlank() ? storedMime : resolveMime(fileName);
        response.setContentType(mime);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                (inline ? "inline" : "attachment") + "; filename*=UTF-8''" +
                        URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        try (InputStream in = projectService.downloadFile(fileId, null, false)) {
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.error("读取文件失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // 文件文本内容（供在线编辑 text/md/code）
    @GetMapping("/files/{fileId}/content")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_view')")
    public Result<String> getFileContent(@PathVariable Long fileId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(projectService.getFileContent(fileId, userId));
    }

    @PutMapping("/files/{fileId}/content")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:file_edit')")
    public Result<Void> saveFileContent(@PathVariable Long fileId, @RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        projectService.saveFileContent(fileId, body.get("content"), userId);
        return Result.success();
    }

    // OnlyOffice 编辑器配置
    @GetMapping("/files/{fileId}/office-config")
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('project:file_view','project:file_edit')")
    public Result<Map<String, Object>> getOfficeConfig(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "edit") String mode,
            HttpServletRequest request,
            Authentication authentication) {
        boolean canEdit = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "project:file_edit".equals(authority.getAuthority()));
        if (!"view".equalsIgnoreCase(mode) && !canEdit) {
            throw new AccessDeniedException("无权编辑项目文件");
        }
        Long userId = (Long) request.getAttribute("userId");
        String userName = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : null;
        return Result.success(projectService.getOfficeConfig(fileId, mode, userId, userName));
    }

    // OnlyOffice 保存回调
    @PostMapping("/files/{fileId}/office-callback")
    public Map<String, Object> officeCallback(@PathVariable Long fileId, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            projectService.verifyOfficeToken(body.get("token") != null ? body.get("token").toString() : null);
            int status = body.get("status") != null ? Integer.parseInt(body.get("status").toString()) : 0;
            if (status == 2 || status == 6) {
                String url = body.get("url") != null ? body.get("url").toString() : null;
                if (url != null) {
                    try (InputStream in = new java.net.URL(url).openStream()) {
                        projectService.saveOfficeFile(fileId, in);
                    }
                }
            }
            result.put("error", 0);
        } catch (Exception e) {
            log.error("OnlyOffice 回调处理失败", e);
            result.put("error", 1);
        }
        return result;
    }

    private String resolveMime(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".ogv") || lower.endsWith(".ogg")) return "video/ogg";
        if (lower.endsWith(".mov")) return "video/quicktime";
        return "application/octet-stream";
    }

    // ===================== Assets =====================
    @GetMapping("/{projectId}/assets")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('project:view')")
    public Result<List<Asset>> getAssets(@PathVariable Long projectId, @RequestParam(required = false) String type) {
        return Result.success(projectService.getAssets(projectId, type));
    }
}
