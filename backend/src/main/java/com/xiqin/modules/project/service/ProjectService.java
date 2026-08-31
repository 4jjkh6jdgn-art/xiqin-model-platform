package com.xiqin.modules.project.service;

import com.xiqin.common.exception.BusinessException;
import com.xiqin.modules.auth.entity.User;
import com.xiqin.modules.auth.repository.UserRepository;
import com.xiqin.modules.notification.service.NotificationService;
import com.xiqin.modules.project.entity.*;
import com.xiqin.modules.project.repository.*;
import com.xiqin.modules.storage.service.StorageService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import javax.crypto.SecretKey;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectCategoryRepository projectCategoryRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectTaskRepository taskRepository;
    private final ProjectPhaseRepository phaseRepository;
    private final ProjectFileRepository fileRepository;
    private final ProjectVersionRepository versionRepository;
    private final ProjectFolderRepository folderRepository;
    private final ProjectFileActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final NotificationService notificationService;

    @Value("${app.office.base-url:http://backend:8080}")
    private String officeBaseUrl;

    @Value("${app.office.server-url:http://127.0.0.1:8089}")
    private String officeServerUrl;

    @Value("${app.office.jwt-secret}")
    private String officeJwtSecret;

    // ===================== Categories =====================
    public List<ProjectCategory> listCategories() {
        return projectCategoryRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional
    public ProjectCategory createCategory(String name, String code, String description) {
        String normalizedName = normalizeCategoryName(name);
        if (projectCategoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new BusinessException("分类名称已存在");
        }
        return projectCategoryRepository.save(ProjectCategory.builder()
                .name(normalizedName)
                .code(code == null ? null : code.trim())
                .description(description)
                .build());
    }

    @Transactional
    public ProjectCategory updateCategory(Long id, String name, String code, String description) {
        ProjectCategory category = projectCategoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("项目分类不存在"));
        if (name != null) {
            String normalizedName = normalizeCategoryName(name);
            if (projectCategoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
                throw new BusinessException("分类名称已存在");
            }
            category.setName(normalizedName);
        }
        if (code != null) category.setCode(code.trim());
        if (description != null) category.setDescription(description);
        category.setUpdatedAt(LocalDateTime.now());
        return projectCategoryRepository.save(category);
    }

    private String normalizeCategoryName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("分类名称不能为空");
        }
        return value.trim();
    }

    @Transactional
    public void deleteCategory(Long id) {
        ProjectCategory category = projectCategoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("项目分类不存在"));
        projectCategoryRepository.delete(category);
    }

    // ===================== Projects =====================
    public Page<Project> searchProjects(String keyword, Long categoryId, String status, String scope,
                                        Long userId, int page, int size) {
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.toLowerCase() + "%";
        String normalizedScope = scope == null || scope.isBlank() ? null : scope.trim().toLowerCase();
        if ("completed".equals(normalizedScope)) {
            status = "completed";
            normalizedScope = null;
        } else if (normalizedScope != null && !List.of("created", "participated").contains(normalizedScope)) {
            throw new BusinessException("不支持的项目筛选方式");
        }
        return projectRepository.searchProjects(pattern, categoryId, status, normalizedScope, userId,
                PageRequest.of(page, size)).map(this::decorateProject);
    }

    public Project getProject(Long id) {
        return decorateProject(projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("项目不存在")));
    }

    @Transactional
    public Project createProject(Project project, Long userId) {
        project.setCreatedBy(userId);
        project.setStatus(project.getStatus() != null ? project.getStatus() : "planning");
        project = projectRepository.save(project);

        // Creator is the leader
        memberRepository.save(ProjectMember.builder()
                .projectId(project.getId())
                .userId(userId)
                .role("leader")
                .build());

        saveVersion(project, userId, "初始版本");

        return decorateProject(project);
    }

    @Transactional
    public Project updateProject(Long id, Project update, Long userId, String changeLog) {
        Project project = getProject(id);
        if (update.getName() != null) project.setName(update.getName());
        if (update.getCategoryId() != null) project.setCategoryId(update.getCategoryId());
        if (update.getDescription() != null) project.setDescription(update.getDescription());
        if (update.getStatus() != null) project.setStatus(update.getStatus());
        if (update.getPriority() != null) project.setPriority(update.getPriority());
        project.setCurrentVersion((project.getCurrentVersion() == null ? 1 : project.getCurrentVersion()) + 1);
        project.setUpdatedAt(LocalDateTime.now());
        project = projectRepository.save(project);
        saveVersion(project, userId, changeLog == null || changeLog.isBlank() ? "更新项目信息" : changeLog);
        return decorateProject(project);
    }

    public List<ProjectVersion> getVersions(Long projectId) {
        getProject(projectId);
        return versionRepository.findByProjectIdOrderByVersionNumDesc(projectId);
    }

    public ProjectVersion getVersion(Long projectId, Integer versionNum) {
        return versionRepository.findByProjectIdAndVersionNum(projectId, versionNum)
                .orElseThrow(() -> new BusinessException("项目版本不存在"));
    }

    @Transactional
    public Project setDefaultVersion(Long projectId, Integer versionNum) {
        Project project = getProject(projectId);
        if (versionNum == null || versionNum < 1
                || !versionRepository.findByProjectIdAndVersionNum(projectId, versionNum).isPresent()) {
            throw new BusinessException("项目版本不存在");
        }
        project.setDefaultVersion(versionNum);
        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    private void saveVersion(Project project, Long userId, String changeLog) {
        versionRepository.save(ProjectVersion.builder()
                .projectId(project.getId())
                .versionNum(project.getCurrentVersion() == null ? 1 : project.getCurrentVersion())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .priority(project.getPriority())
                .changeLog(changeLog)
                .createdBy(userId)
                .build());
    }

    @Transactional
    public void deleteProject(Long id) {
        Project project = getProject(id);
        deleteStoredCover(project);
        projectRepository.delete(project);
    }

    @Transactional
    public Project updateProjectCover(Long id, MultipartFile file) {
        Project project = getProject(id);
        if (file == null || file.isEmpty()) throw new BusinessException("请选择封面图片");
        if (file.getSize() > 8L * 1024 * 1024) throw new BusinessException("封面图片不能超过 8 MB");
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!List.of("image/jpeg", "image/png", "image/webp", "image/gif").contains(mime)) {
            throw new BusinessException("封面仅支持 JPG、PNG、WebP 或 GIF 图片");
        }

        String previousKey = project.getCoverS3Key();
        String bucket = storageService.getBucketThumbnails();
        String originalName = file.getOriginalFilename() == null ? "project-cover" : file.getOriginalFilename();
        String objectKey = "projects/" + id + "/covers/" + UUID.randomUUID() + "_" + originalName;
        storageService.uploadFile(bucket, objectKey, file);
        project.setCoverS3Key(objectKey);
        project.setCoverMimeType(mime);
        project.setCoverFileName(originalName);
        project.setUpdatedAt(LocalDateTime.now());
        Project saved = projectRepository.save(project);
        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(objectKey)) {
            storageService.deleteFile(bucket, previousKey);
        }
        return decorateProject(saved);
    }

    public InputStream getProjectCover(Long id) {
        Project project = getProject(id);
        if (project.getCoverS3Key() == null || project.getCoverS3Key().isBlank()) {
            throw new BusinessException("项目尚未设置封面");
        }
        return storageService.downloadFile(storageService.getBucketThumbnails(), project.getCoverS3Key());
    }

    public String getProjectCoverMimeType(Long id) {
        Project project = getProject(id);
        return project.getCoverMimeType() == null ? "image/jpeg" : project.getCoverMimeType();
    }

    public String getProjectCoverFileName(Long id) {
        Project project = getProject(id);
        return project.getCoverFileName() == null ? "project-cover.jpg" : project.getCoverFileName();
    }

    @Transactional
    public Project removeProjectCover(Long id) {
        Project project = getProject(id);
        deleteStoredCover(project);
        project.setCoverS3Key(null);
        project.setCoverMimeType(null);
        project.setCoverFileName(null);
        project.setCoverUrl(null);
        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    private void deleteStoredCover(Project project) {
        if (project.getCoverS3Key() != null && !project.getCoverS3Key().isBlank()) {
            storageService.deleteFile(storageService.getBucketThumbnails(), project.getCoverS3Key());
        }
    }

    private Project decorateProject(Project project) {
        project.setCoverUrl(project.getCoverS3Key() == null || project.getCoverS3Key().isBlank()
                ? null : "/api/projects/" + project.getId() + "/cover");
        return project;
    }

    // ===================== Members =====================
    public List<ProjectMember> getMembers(Long projectId) {
        return memberRepository.findByProjectId(projectId);
    }

    public List<Map<String, Object>> getMemberDetails(Long projectId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectMember member : getMembers(projectId)) {
            User user = userRepository.findById(member.getUserId()).orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", member.getId());
            row.put("userId", member.getUserId());
            row.put("projectRole", member.getRole());
            row.put("username", user != null ? user.getUsername() : "用户 #" + member.getUserId());
            row.put("email", user != null ? user.getEmail() : null);
            row.put("avatarUrl", user != null ? user.getAvatarUrl() : null);
            row.put("systemRoleCode", user != null && user.getRole() != null ? user.getRole().getCode() : null);
            row.put("systemRoleName", user != null && user.getRole() != null ? user.getRole().getName() : null);
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> getMemberCandidates(Long projectId, String keyword) {
        List<Long> existing = getMembers(projectId).stream().map(ProjectMember::getUserId).toList();
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (existing.contains(user.getId()) || user.getStatus() == null || user.getStatus() != 1) continue;
            String haystack = (user.getUsername() + " " + (user.getEmail() == null ? "" : user.getEmail())).toLowerCase();
            if (!normalized.isBlank() && !haystack.contains(normalized)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("email", user.getEmail());
            row.put("avatarUrl", user.getAvatarUrl());
            row.put("roleCode", user.getRole() != null ? user.getRole().getCode() : null);
            row.put("roleName", user.getRole() != null ? user.getRole().getName() : null);
            result.add(row);
        }
        return result;
    }

    @Transactional
    public void addMember(Long projectId, Long userId, String role) {
        addMembers(projectId, List.of(userId), role, null);
    }

    @Transactional
    public void addMembers(Long projectId, List<Long> userIds, String role, Long actorUserId) {
        Project project = getProject(projectId);
        List<Long> targets = userIds == null ? List.of() : userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (targets.isEmpty()) throw new BusinessException("请选择要添加的成员");

        String normalizedRole = role == null || role.isBlank() ? "member" : role;
        int added = 0;
        for (Long userId : targets) {
            if (memberRepository.findByProjectIdAndUserId(projectId, userId).isPresent()) continue;
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在：" + userId));
            if (user.getStatus() == null || user.getStatus() != 1) {
                throw new BusinessException("用户不可用：" + user.getUsername());
            }
            memberRepository.save(ProjectMember.builder()
                    .projectId(projectId)
                    .userId(userId)
                    .role(normalizedRole)
                    .build());
            notificationService.create(
                    "project_membership",
                    "info",
                    "已加入项目「" + project.getName() + "」",
                    "你已被添加为" + projectRoleText(normalizedRole) + "，可进入项目查看资料与协作内容。",
                    "project",
                    projectId,
                    projectId,
                    actorUserId,
                    userId);
            added++;
        }
        if (added == 0) throw new BusinessException("所选用户已在项目中");
    }

    private String projectRoleText(String role) {
        return switch (role) {
            case "leader" -> "项目负责人";
            case "viewer" -> "访客";
            default -> "协作成员";
        };
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        memberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    // ===================== Tasks =====================
    public List<ProjectTask> getTasks(Long projectId) {
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    @Transactional
    public ProjectTask createTask(ProjectTask task) {
        task.setStatus(task.getStatus() != null ? task.getStatus() : "pending");
        return taskRepository.save(task);
    }

    @Transactional
    public ProjectTask updateTask(Long id, ProjectTask update) {
        ProjectTask task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在"));
        if (update.getTitle() != null) task.setTitle(update.getTitle());
        if (update.getDescription() != null) task.setDescription(update.getDescription());
        if (update.getAssigneeId() != null) task.setAssigneeId(update.getAssigneeId());
        if (update.getStatus() != null) {
            task.setStatus(update.getStatus());
            if ("completed".equals(update.getStatus())) {
                task.setCompletedAt(LocalDateTime.now());
            }
        }
        if (update.getPriority() != null) task.setPriority(update.getPriority());
        if (update.getDeadline() != null) task.setDeadline(update.getDeadline());
        task.setUpdatedAt(LocalDateTime.now());
        return taskRepository.save(task);
    }

    @Transactional
    public void deleteTask(Long id) {
        taskRepository.deleteById(id);
    }

    // ===================== Phases =====================
    public List<ProjectPhase> getPhases(Long projectId) {
        return phaseRepository.findByProjectIdOrderBySortOrder(projectId);
    }

    @Transactional
    public ProjectPhase createPhase(ProjectPhase phase) {
        phase.setStatus(phase.getStatus() != null ? phase.getStatus() : "pending");
        return phaseRepository.save(phase);
    }

    // ===================== Files =====================
    public List<ProjectFile> getFiles(Long projectId) {
        return fileRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<ProjectFolder> getFolders(Long projectId) {
        getProject(projectId);
        return folderRepository.findByProjectIdOrderByNameAsc(projectId);
    }

    @Transactional
    public ProjectFolder createFolder(Long projectId, Long parentId, String name, Long userId) {
        getProject(projectId);
        String normalized = sanitizeFolderName(name);
        if (folderRepository.existsByProjectIdAndParentIdAndNameIgnoreCase(projectId, parentId, normalized)) {
            throw new BusinessException("同级文件夹名称已存在");
        }
        if (parentId != null) {
            ProjectFolder parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new BusinessException("上级文件夹不存在"));
            if (!projectId.equals(parent.getProjectId())) throw new BusinessException("文件夹不属于当前项目");
        }
        return folderRepository.save(ProjectFolder.builder()
                .projectId(projectId).parentId(parentId).name(normalized).createdBy(userId).build());
    }

    @Transactional
    public ProjectFile uploadFile(Long projectId, MultipartFile file, Long taskId, Long userId,
                                  Long folderId, String relativePath) {
        getProject(projectId);
        if (folderId != null) validateFolder(projectId, folderId);
        String bucket = storageService.getBucketModels();
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String s3Key = "projects/" + projectId + "/" + UUID.randomUUID() + "/" + originalName;
        storageService.uploadFile(bucket, s3Key, file);

        ProjectFile saved = fileRepository.save(ProjectFile.builder()
                .projectId(projectId)
                .taskId(taskId)
                .folderId(folderId)
                .relativePath(relativePath)
                .fileName(originalName)
                .s3Key(s3Key)
                .s3Bucket(bucket)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .uploadedBy(userId)
                .build());
        recordActivity(saved, "upload", userId,
                relativePath == null || relativePath.isBlank() ? "上传文件" : "上传文件夹内容：" + relativePath);
        return saved;
    }

    @Transactional
    public List<ProjectFile> uploadFolder(Long projectId, MultipartFile[] files, String[] relativePaths,
                                          Long targetFolderId, Long userId) {
        if (files == null || files.length == 0) throw new BusinessException("请选择要上传的文件夹");
        if (relativePaths == null || relativePaths.length != files.length) {
            throw new BusinessException("文件夹路径信息不完整");
        }
        List<ProjectFile> result = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            String path = normalizeRelativePath(relativePaths[i]);
            String[] parts = path.split("/");
            Long parentId = targetFolderId;
            for (int p = 0; p < parts.length - 1; p++) {
                if (parts[p].isBlank()) continue;
                parentId = ensureFolder(projectId, parentId, parts[p], userId).getId();
            }
            result.add(uploadFile(projectId, files[i], null, userId, parentId, path));
        }
        return result;
    }

    @Transactional
    public ProjectFile moveFile(Long fileId, Long folderId, Long userId) {
        ProjectFile file = getFile(fileId);
        if (folderId != null) validateFolder(file.getProjectId(), folderId);
        file.setFolderId(folderId);
        file.setUpdatedAt(LocalDateTime.now());
        file = fileRepository.save(file);
        recordActivity(file, "move", userId, folderId == null ? "移动到项目根目录" : "移动到文件夹");
        return file;
    }

    public List<ProjectFileActivity> getFileActivities(Long fileId, String keyword) {
        getFile(fileId);
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.toLowerCase() + "%";
        return activityRepository.searchByFileId(fileId, pattern);
    }

    @Transactional
    public void reviewFile(Long fileId, String status, Long reviewerId) {
        ProjectFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在"));
        file.setStatus(status);
        file.setReviewedBy(reviewerId);
        file.setUpdatedAt(LocalDateTime.now());
        fileRepository.save(file);
    }

    public String getFilePreviewUrl(Long fileId) {
        getFile(fileId);
        return "/api/projects/files/" + fileId + "/preview";
    }

    public InputStream downloadFile(Long fileId, Long userId, boolean record) {
        ProjectFile file = getFile(fileId);
        if (record) recordActivity(file, "download", userId, "下载文件");
        return storageService.downloadFile(file.getS3Bucket(), file.getS3Key());
    }

    public String getFileName(Long fileId) {
        return fileRepository.findById(fileId)
                .map(ProjectFile::getFileName)
                .orElse("file");
    }

    public String getFileMimeType(Long fileId) {
        return fileRepository.findById(fileId)
                .map(ProjectFile::getMimeType)
                .orElse(null);
    }

    // ===================== File Content (inline edit) =====================
    public String getFileContent(Long fileId, Long userId) {
        ProjectFile file = getFile(fileId);
        recordActivity(file, "view", userId, "查看文件内容");
        try (InputStream in = storageService.downloadFile(file.getS3Bucket(), file.getS3Key())) {
            byte[] bytes = in.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (content.indexOf('\uFFFD') >= 0) {
                try {
                    content = new String(bytes, Charset.forName("GBK"));
                } catch (Exception ignored) {
                    // keep UTF-8 result
                }
            }
            return content;
        } catch (Exception e) {
            throw new RuntimeException("读取文件内容失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void saveFileContent(Long fileId, String content, Long userId) {
        ProjectFile file = getFile(fileId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(bytes);
        storageService.uploadStream(file.getS3Bucket(), file.getS3Key(), stream, bytes.length, file.getMimeType());
        file.setUpdatedAt(LocalDateTime.now());
        fileRepository.save(file);
        recordActivity(file, "update", userId, "更新文件内容");
    }

    // ===================== OnlyOffice online edit =====================
    public Map<String, Object> getOfficeConfig(Long fileId, String requestedMode, Long userId, String userName) {
        ProjectFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在"));
        recordActivity(file, "edit".equalsIgnoreCase(requestedMode) ? "edit" : "view", userId,
                "edit".equalsIgnoreCase(requestedMode) ? "打开在线编辑" : "在线查看文件");
        String ext = getFileExt(file.getFileName()).toLowerCase();
        String documentType = switch (ext) {
            case "doc", "docx" -> "word";
            case "xls", "xlsx" -> "cell";
            case "ppt", "pptx" -> "slide";
            default -> throw new BusinessException("该文件类型不支持 Office 在线查看");
        };
        String mode = "view".equalsIgnoreCase(requestedMode) ? "view" : "edit";
        String docUrl = officeBaseUrl + "/api/projects/files/" + fileId + "/raw";
        String callbackUrl = officeBaseUrl + "/api/projects/files/" + fileId + "/office-callback";
        long version = file.getUpdatedAt() != null ? file.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : fileId;

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", ext);
        document.put("key", fileId + "_" + version);
        document.put("title", file.getFileName());
        document.put("url", docUrl);
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", "edit".equals(mode));
        permissions.put("download", true);
        permissions.put("print", true);
        document.put("permissions", permissions);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("mode", mode);
        editorConfig.put("lang", "zh-CN");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId != null ? userId.toString() : "local-user");
        user.put("name", userName != null && !userName.isBlank() ? userName : "编辑者");
        editorConfig.put("user", user);
        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("autosave", true);
        customization.put("compactHeader", true);
        customization.put("compactToolbar", true);
        customization.put("chat", false);
        customization.put("comments", false);
        editorConfig.put("customization", customization);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("documentType", documentType);
        config.put("document", document);
        config.put("editorConfig", editorConfig);
        config.put("type", "desktop");
        String token = Jwts.builder()
                .claims(config)
                .signWith(officeSigningKey(), Jwts.SIG.HS256)
                .compact();
        config.put("token", token);
        config.put("documentServerUrl", officeServerUrl);
        return config;
    }

    public void verifyOfficeToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException("OnlyOffice 回调缺少签名");
        }
        Jwts.parser().verifyWith(officeSigningKey()).build().parseSignedClaims(token);
    }

    private SecretKey officeSigningKey() {
        return Keys.hmacShaKeyFor(officeJwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void saveOfficeFile(Long fileId, InputStream data) {
        ProjectFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在"));
        storageService.uploadStream(file.getS3Bucket(), file.getS3Key(), data, -1, file.getMimeType());
        file.setUpdatedAt(LocalDateTime.now());
        fileRepository.save(file);
        recordActivity(file, "update", null, "在线编辑器保存文件");
    }

    private ProjectFile getFile(Long fileId) {
        return fileRepository.findById(fileId).orElseThrow(() -> new BusinessException("文件不存在"));
    }

    private void recordActivity(ProjectFile file, String action, Long userId, String detail) {
        String userName = userId == null ? "系统" : userRepository.findById(userId)
                .map(User::getUsername).orElse("用户 #" + userId);
        activityRepository.save(ProjectFileActivity.builder()
                .fileId(file.getId()).projectId(file.getProjectId()).action(action)
                .userId(userId).userName(userName).detail(detail).build());
    }

    private void validateFolder(Long projectId, Long folderId) {
        ProjectFolder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new BusinessException("文件夹不存在"));
        if (!projectId.equals(folder.getProjectId())) throw new BusinessException("文件夹不属于当前项目");
    }

    private ProjectFolder ensureFolder(Long projectId, Long parentId, String name, Long userId) {
        String normalized = sanitizeFolderName(name);
        return folderRepository.findByProjectIdAndParentIdAndNameIgnoreCase(projectId, parentId, normalized)
                .orElseGet(() -> folderRepository.save(ProjectFolder.builder()
                        .projectId(projectId).parentId(parentId).name(normalized).createdBy(userId).build()));
    }

    private String sanitizeFolderName(String name) {
        if (name == null || name.isBlank()) throw new BusinessException("文件夹名称不能为空");
        String value = name.trim().replace("/", "").replace("\\", "");
        if (value.isBlank() || ".".equals(value) || "..".equals(value)) throw new BusinessException("文件夹名称无效");
        return value;
    }

    private String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) throw new BusinessException("文件相对路径不能为空");
        String value = path.replace('\\', '/');
        if (value.startsWith("/") || value.contains("../") || value.equals("..")) {
            throw new BusinessException("文件相对路径无效");
        }
        return value;
    }

    private String getFileExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1) : "";
    }

    // ===================== Assets =====================
    public List<Asset> getAssets(Long projectId, String type) {
        if (type != null) {
            return assetRepository.findByAssetType(type);
        }
        return assetRepository.findByProjectId(projectId);
    }
}
