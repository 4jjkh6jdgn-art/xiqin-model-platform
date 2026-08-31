package com.xiqin.modules.model.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiqin.common.exception.BusinessException;
import com.xiqin.modules.model.dto.*;
import com.xiqin.modules.model.entity.*;
import com.xiqin.modules.model.repository.*;
import com.xiqin.modules.auth.entity.User;
import com.xiqin.modules.auth.repository.UserRepository;
import com.xiqin.modules.project.entity.Project;
import com.xiqin.modules.project.repository.ProjectFileRepository;
import com.xiqin.modules.project.repository.ProjectRepository;
import com.xiqin.modules.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final ModelRepository modelRepository;
    private final ModelProjectLinkRepository modelProjectLinkRepository;
    private final ModelCategoryLinkRepository modelCategoryLinkRepository;
    private final ModelFileRepository modelFileRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ModelCategoryRepository categoryRepository;
    private final UploadRecordRepository uploadRecordRepository;
    private final DownloadRecordRepository downloadRecordRepository;
    private final ModificationRecordRepository modificationRecordRepository;
    private final ProjectRepository projectRepository;
    private final ProjectFileRepository projectFileRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-model-process}")
    private String routingKey;

    // ===================== Category Management =====================
    public List<ModelCategory> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public List<ModelCategory> listRootCategories() {
        return categoryRepository.findByParentIdIsNullOrderBySortOrder();
    }

    @Transactional
    public ModelCategory createCategory(CreateCategoryRequest req) {
        String name = normalizeCategoryName(req.getName());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException("分类名称已存在");
        }
        ModelCategory cat = ModelCategory.builder()
                .name(name)
                .parentId(req.getParentId())
                .code(req.getCode() != null && !req.getCode().isBlank() ? req.getCode().trim() : name)
                .description(req.getDescription())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
        return categoryRepository.save(cat);
    }

    @Transactional
    public ModelCategory updateCategory(Long id, CreateCategoryRequest req) {
        ModelCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("分类不存在"));
        if (req.getName() != null) {
            String name = normalizeCategoryName(req.getName());
            if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
                throw new BusinessException("分类名称已存在");
            }
            cat.setName(name);
        }
        if (req.getParentId() != null) cat.setParentId(req.getParentId());
        if (req.getCode() != null) cat.setCode(req.getCode().trim());
        if (req.getDescription() != null) cat.setDescription(req.getDescription());
        if (req.getSortOrder() != null) cat.setSortOrder(req.getSortOrder());
        cat.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(cat);
    }

    private String normalizeCategoryName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("分类名称不能为空");
        }
        return value.trim();
    }

    @Transactional
    public void deleteCategory(Long id) {
        if (!categoryRepository.existsById(id)) {
            throw new BusinessException("分类不存在");
        }
        categoryRepository.deleteById(id);
    }

    // ===================== Model CRUD =====================
    public Page<Model> searchModels(String keyword, Long categoryId, Long projectId, Long projectCategoryId,
                                    String status, String sortField, String sortDirection, int page, int size) {
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.toLowerCase() + "%";
        String sortProperty = "name".equalsIgnoreCase(sortField) ? "name" : "updatedAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(page, size, Sort.by(direction, sortProperty));
        Set<Long> relationModelIds = null;
        if (categoryId != null) {
            relationModelIds = modelCategoryLinkRepository.findByCategoryId(categoryId).stream()
                    .map(ModelCategoryLink::getModelId).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (projectId != null) {
            Set<Long> ids = modelProjectLinkRepository.findByProjectId(projectId).stream()
                    .map(ModelProjectLink::getModelId).collect(Collectors.toSet());
            modelRepository.findByProjectId(projectId).stream().map(Model::getId).forEach(ids::add);
            relationModelIds = intersectModelIds(relationModelIds, ids);
        }
        if (projectCategoryId != null) {
            List<Long> projectIds = projectRepository.findAll().stream()
                    .filter(project -> Objects.equals(project.getCategoryId(), projectCategoryId))
                    .map(Project::getId).collect(Collectors.toList());
            Set<Long> ids = projectIds.isEmpty() ? Collections.emptySet()
                    : modelProjectLinkRepository.findByProjectIdIn(projectIds).stream()
                    .map(ModelProjectLink::getModelId).collect(Collectors.toSet());
            if (!projectIds.isEmpty()) {
                Set<Long> mutableIds = new LinkedHashSet<>(ids);
                modelRepository.findAll().stream()
                        .filter(model -> model.getProjectId() != null && projectIds.contains(model.getProjectId()))
                        .map(Model::getId).forEach(mutableIds::add);
                ids = mutableIds;
            }
            relationModelIds = intersectModelIds(relationModelIds, ids);
        }
        Page<Model> result = relationModelIds == null
                ? modelRepository.searchModels(pattern, null, null, null, status, pageable)
                : relationModelIds.isEmpty()
                    ? org.springframework.data.domain.Page.empty(pageable)
                    : modelRepository.searchModelsByIds(pattern, status, relationModelIds, pageable);
        List<Model> models = result.getContent();
        if (!models.isEmpty()) {
            List<Long> modelIds = models.stream().map(Model::getId).collect(Collectors.toList());
            Map<Long, List<ModelFile>> filesByModel = modelFileRepository.findLibraryFilesByModelIds(modelIds)
                    .stream().collect(Collectors.groupingBy(ModelFile::getModelId));
            Map<Long, Long> downloadCounts = downloadRecordRepository.countByModelIds(modelIds).stream()
                    .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));
            Map<Long, List<Long>> categoryIdsByModel = modelCategoryLinkRepository.findByModelIdIn(modelIds).stream()
                    .collect(Collectors.groupingBy(ModelCategoryLink::getModelId, LinkedHashMap::new,
                            Collectors.mapping(ModelCategoryLink::getCategoryId, Collectors.toList())));
            Map<Long, List<Long>> projectIdsByModel = modelProjectLinkRepository.findByModelIdIn(modelIds).stream()
                    .collect(Collectors.groupingBy(ModelProjectLink::getModelId, LinkedHashMap::new,
                            Collectors.mapping(ModelProjectLink::getProjectId, Collectors.toList())));
            Set<Long> linkedProjectIds = projectIdsByModel.values().stream().flatMap(Collection::stream)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            linkedProjectIds.addAll(models.stream().map(Model::getProjectId).filter(Objects::nonNull).toList());
            Map<Long, Project> projects = projectRepository.findAllById(linkedProjectIds)
                    .stream().collect(Collectors.toMap(Project::getId, p -> p));
            Map<Long, User> users = userRepository.findAllById(models.stream()
                    .map(Model::getCreatedBy).filter(Objects::nonNull).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(User::getId, u -> u));
            Map<Long, String> categoryNames = categoryRepository.findAll().stream()
                    .collect(Collectors.toMap(ModelCategory::getId, ModelCategory::getName));

            models.forEach(m -> {
                List<ModelFile> files = filesByModel.getOrDefault(m.getId(), Collections.emptyList());
                m.setFileSize(files.stream().map(ModelFile::getFileSize).filter(Objects::nonNull).mapToLong(Long::longValue).sum());
                m.setFileCount(files.size());
                m.setFileFormats(buildLibraryBadges(files));
                m.setDownloadCount(downloadCounts.getOrDefault(m.getId(), 0L));
                List<Long> categoryIds = categoryIdsByModel.getOrDefault(m.getId(), Collections.emptyList());
                if (categoryIds.isEmpty() && m.getCategoryId() != null) categoryIds = List.of(m.getCategoryId());
                List<String> categoryLabels = categoryIds.stream().map(categoryNames::get).filter(Objects::nonNull).toList();
                m.setCategoryIds(categoryIds);
                m.setCategoryNames(categoryLabels);
                m.setCategoryName(categoryLabels.isEmpty() ? null : String.join(" / ", categoryLabels));

                List<Long> projectIds = projectIdsByModel.getOrDefault(m.getId(), Collections.emptyList());
                if (projectIds.isEmpty() && m.getProjectId() != null) projectIds = List.of(m.getProjectId());
                List<String> projectLabels = projectIds.stream().map(projects::get).filter(Objects::nonNull)
                        .map(Project::getName).toList();
                m.setProjectIds(projectIds);
                m.setProjectNames(projectLabels);
                m.setProjectName(projectLabels.isEmpty() ? null : String.join("、", projectLabels));
                m.setCreatedByName(m.getCreatedBy() != null && users.containsKey(m.getCreatedBy())
                        ? users.get(m.getCreatedBy()).getUsername() : null);
            });
        }
        // Thumbnails are served through the backend proxy (same origin) so the
        // browser never needs to reach MinIO directly.
        models.forEach(m -> {
            if (m.getThumbnailUrl() != null && !m.getThumbnailUrl().startsWith("http")) {
                m.setThumbnailUrl("/api/models/" + m.getId() + "/thumbnail/raw");
            }
        });
        return result;
    }

    private Set<Long> intersectModelIds(Set<Long> current, Set<Long> incoming) {
        if (current == null) return new LinkedHashSet<>(incoming);
        current.retainAll(incoming);
        return current;
    }

    public Map<String, Object> getLibraryStats() {
        Map<String, Long> modelCategoryCounts = new LinkedHashMap<>();
        modelCategoryLinkRepository.countModelsByCategory().forEach(row ->
                modelCategoryCounts.put(String.valueOf(((Number) row[0]).longValue()), ((Number) row[1]).longValue()));
        List<Model> allModels = modelRepository.findAll();
        Map<Long, Set<Long>> modelsByProject = new LinkedHashMap<>();
        modelProjectLinkRepository.findAll().forEach(link ->
                modelsByProject.computeIfAbsent(link.getProjectId(), ignored -> new LinkedHashSet<>()).add(link.getModelId()));
        // 兼容尚未迁移到多项目关系表的历史模型主项目字段。
        allModels.stream().filter(model -> model.getProjectId() != null).forEach(model ->
                modelsByProject.computeIfAbsent(model.getProjectId(), ignored -> new LinkedHashSet<>()).add(model.getId()));
        Map<String, Long> projectCounts = new LinkedHashMap<>();
        modelsByProject.forEach((projectId, modelIds) ->
                projectCounts.put(String.valueOf(projectId), (long) modelIds.size()));

        Map<String, Long> projectCategoryCounts = new LinkedHashMap<>();
        Map<Long, Long> projectCategories = projectRepository.findAll().stream()
                .filter(project -> project.getCategoryId() != null)
                .collect(Collectors.toMap(Project::getId, Project::getCategoryId));
        Map<Long, Set<Long>> modelsByProjectCategory = new LinkedHashMap<>();
        modelsByProject.forEach((projectId, modelIds) -> {
            Long projectCategory = projectCategories.get(projectId);
            if (projectCategory != null) {
                modelsByProjectCategory.computeIfAbsent(projectCategory, ignored -> new LinkedHashSet<>()).addAll(modelIds);
            }
        });
        modelsByProjectCategory.forEach((categoryId, modelIds) ->
                projectCategoryCounts.put(String.valueOf(categoryId), (long) modelIds.size()));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalModels", (long) allModels.size());
        stats.put("modelCategoryCounts", modelCategoryCounts);
        stats.put("projectCategoryCounts", projectCategoryCounts);
        stats.put("projectCounts", projectCounts);
        stats.put("totalStorageBytes", Optional.ofNullable(modelFileRepository.sumTotalFileSize()).orElse(0L)
                + Optional.ofNullable(projectFileRepository.sumTotalFileSize()).orElse(0L));
        stats.put("memberCount", userRepository.countByStatus(1));
        stats.put("downloadCount", downloadRecordRepository.count());
        return stats;
    }

    private List<String> buildLibraryBadges(List<ModelFile> files) {
        LinkedHashSet<String> badges = new LinkedHashSet<>();
        List<String> displayFormats = files.stream()
                .filter(f -> "display".equals(f.getFileType()))
                .map(ModelFile::getFileFormat).filter(Objects::nonNull)
                .map(String::toUpperCase).distinct().collect(Collectors.toList());
        displayFormats.stream().filter(f -> !Set.of("GLB", "GLTF").contains(f)).findFirst()
                .or(() -> displayFormats.stream().findFirst()).ifPresent(badges::add);
        if (files.stream().anyMatch(f -> "texture".equals(f.getFileType()))) badges.add("TEX");
        if (files.stream().anyMatch(f -> "spp".equalsIgnoreCase(f.getFileFormat()))) badges.add("SP");
        if (files.stream().anyMatch(f -> "unitypackage".equalsIgnoreCase(f.getFileFormat()))) badges.add("UNITY");
        return new ArrayList<>(badges);
    }

    public ModelVO getModelDetail(Long id, Integer requestedVersion) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));

        int latestVersion = model.getVersion() != null ? model.getVersion() : 1;
        int defaultVersion = model.getDefaultVersion() != null ? model.getDefaultVersion() : latestVersion;
        if (defaultVersion < 1 || defaultVersion > latestVersion) defaultVersion = latestVersion;
        int selectedVersion = requestedVersion != null ? requestedVersion : defaultVersion;
        if (selectedVersion < 1 || selectedVersion > latestVersion) {
            throw new BusinessException("模型版本不存在");
        }
        List<ModelFile> versionFiles = modelFileRepository.findByModelIdAndVersionNumOrderBySortOrder(id, selectedVersion)
                .stream().filter(f -> !"thumbnail".equals(f.getFileType())).collect(Collectors.toList());
        List<ModelFile> thumbnailFiles = modelFileRepository.findByModelIdAndFileType(id, "thumbnail");
        List<ModelFile> files = new ArrayList<>(versionFiles);
        files.addAll(thumbnailFiles);
        String categoryName = model.getCategoryId() != null
                ? categoryRepository.findById(model.getCategoryId()).map(ModelCategory::getName).orElse(null)
                : null;
        String projectName = model.getProjectId() != null
                ? projectRepository.findById(model.getProjectId()).map(Project::getName).orElse(null)
                : null;
        String createdByName = model.getCreatedBy() != null
                ? userRepository.findById(model.getCreatedBy()).map(User::getUsername).orElse(null)
                : null;
        List<Long> loadedCategoryIds = modelCategoryLinkRepository.findByModelId(id).stream()
                .map(ModelCategoryLink::getCategoryId).distinct().collect(Collectors.toList());
        List<Long> loadedProjectIds = modelProjectLinkRepository.findByModelId(id).stream()
                .map(ModelProjectLink::getProjectId).distinct().collect(Collectors.toList());
        final List<Long> categoryIds = loadedCategoryIds.isEmpty() && model.getCategoryId() != null
                ? List.of(model.getCategoryId()) : loadedCategoryIds;
        final List<Long> projectIds = loadedProjectIds.isEmpty() && model.getProjectId() != null
                ? List.of(model.getProjectId()) : loadedProjectIds;
        Map<Long, String> allCategoryNames = categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(ModelCategory::getId, ModelCategory::getName));
        Map<Long, String> allProjectNames = projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Project::getName));

        // Build file VOs with URLs routed through the backend proxy (same origin).
        // Viewer captures use ModelFile records as reusable thumbnail candidates,
        // while ordinary model files remain a separate list in the UI.
        List<ModelFileVO> allFileVOs = files.stream().map(f -> {
            String url = f.getS3Key() != null
                    ? "/api/models/files/" + f.getId() + "/raw"
                    : null;
            return ModelFileVO.builder()
                    .id(f.getId())
                    .fileName(f.getFileName())
                    .filePath(logicalFilePath(f))
                    .fileType(f.getFileType())
                    .fileFormat(f.getFileFormat())
                    .fileSize(f.getFileSize())
                    .mimeType(f.getMimeType())
                    .url(url)
                    .sortOrder(f.getSortOrder())
                    .build();
        }).collect(Collectors.toList());

        List<ModelFileVO> fileVOs = allFileVOs.stream()
                .filter(f -> !"thumbnail".equals(f.getFileType()))
                .collect(Collectors.toList());
        List<ModelFileVO> thumbnailCandidates = allFileVOs.stream()
                .filter(f -> "thumbnail".equals(f.getFileType()))
                .collect(Collectors.toList());
        Long thumbnailCandidateId = files.stream()
                .filter(f -> "thumbnail".equals(f.getFileType()))
                .filter(f -> Objects.equals(f.getS3Key(), model.getThumbnailUrl()))
                .map(ModelFile::getId)
                .findFirst()
                .orElse(null);

        // Find display file URL for 3D viewer.
        // Prefer the converted GLB/GLTF (what three.js GLTFLoader can actually parse);
        // fall back to the first display file (e.g. raw FBX) only if no GLB/GLTF exists.
        String displayFileUrl = fileVOs.stream()
                .filter(f -> "display".equals(f.getFileType()))
                .filter(f -> f.getUrl() != null)
                .min(java.util.Comparator.comparingInt((ModelFileVO f) -> isGltfFamily(f.getFileName()) ? 0 : 1))
                .map(ModelFileVO::getUrl)
                .orElse(null);

        List<String> textureUrls = fileVOs.stream()
                .filter(f -> "texture".equals(f.getFileType()))
                .map(ModelFileVO::getUrl)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return ModelVO.builder()
                .id(model.getId())
                .name(model.getName())
                .categoryId(model.getCategoryId())
                .categoryName(categoryName)
                .projectId(model.getProjectId())
                .projectName(projectName)
                .categoryIds(categoryIds)
                .projectIds(projectIds)
                .categories(categoryIds.stream().map(category -> Map.<String, Object>of(
                        "id", category, "name", allCategoryNames.getOrDefault(category, "分类 #" + category)))
                        .collect(Collectors.toList()))
                .projects(projectIds.stream().map(project -> Map.<String, Object>of(
                        "id", project, "name", allProjectNames.getOrDefault(project, "项目 #" + project)))
                        .collect(Collectors.toList()))
                .description(model.getDescription())
                .status(model.getStatus())
                .version(selectedVersion)
                .latestVersion(latestVersion)
                .defaultVersion(defaultVersion)
                .thumbnailUrl(model.getThumbnailUrl() != null && !model.getThumbnailUrl().startsWith("http")
                        ? "/api/models/" + model.getId() + "/thumbnail/raw"
                        : model.getThumbnailUrl())
                .cameraView(model.getCameraView())
                .lighting(model.getLighting())
                .createdBy(createdByName)
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .files(fileVOs)
                .thumbnailCandidates(thumbnailCandidates)
                .thumbnailCandidateId(thumbnailCandidateId)
                .displayFileUrl(displayFileUrl)
                .textureUrls(textureUrls)
                .fileSize(fileVOs.stream().mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0L).sum())
                .build();
    }

    @Transactional
    public Model createModel(CreateModelRequest req, Long userId) {
        List<Long> categoryIds = normalizeIds(req.getCategoryIds(), req.getCategoryId());
        List<Long> projectIds = normalizeIds(req.getProjectIds(), req.getProjectId());
        Model model = Model.builder()
                .name(req.getName())
                .categoryId(firstOrNull(categoryIds))
                .projectId(firstOrNull(projectIds))
                .description(req.getDescription())
                .status("draft")
                .version(1)
                .createdBy(userId)
                .build();
        model = modelRepository.save(model);
        syncRelations(model.getId(), categoryIds, projectIds);

        // Log modification
        logModification(model.getId(), userId, "create", null, null, model.getName());
        return model;
    }

    @Transactional
    public Model updateModel(Long id, UpdateModelRequest req, Long userId) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));

        if (req.getName() != null) {
            logModification(id, userId, "update", "name", model.getName(), req.getName());
            model.setName(req.getName());
        }
        if (req.getCategoryIds() != null || req.getCategoryId() != null) {
            List<Long> ids = normalizeIds(req.getCategoryIds(), req.getCategoryId());
            model.setCategoryId(firstOrNull(ids));
            replaceCategoryLinks(id, ids);
        }
        if (req.getProjectIds() != null || req.getProjectId() != null) {
            List<Long> ids = normalizeIds(req.getProjectIds(), req.getProjectId());
            model.setProjectId(firstOrNull(ids));
            replaceProjectLinks(id, ids);
        }
        if (req.getDescription() != null) model.setDescription(req.getDescription());
        if (req.getStatus() != null) {
            logModification(id, userId, "status_change", "status", model.getStatus(), req.getStatus());
            model.setStatus(req.getStatus());
        }
        model.setUpdatedAt(LocalDateTime.now());
        return modelRepository.save(model);
    }

    @Transactional
    public Model linkProject(Long modelId, Long projectId, Long userId) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));
        if (!modelProjectLinkRepository.existsByModelIdAndProjectId(modelId, projectId)) {
            modelProjectLinkRepository.save(ModelProjectLink.builder()
                    .modelId(modelId).projectId(projectId).build());
            if (model.getProjectId() == null) model.setProjectId(projectId);
            model.setUpdatedAt(LocalDateTime.now());
            modelRepository.save(model);
            logModification(modelId, userId, "update", "project", null, project.getName());
        }
        return model;
    }

    @Transactional
    public Model unlinkProject(Long modelId, Long projectId, Long userId) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("项目不存在"));
        modelProjectLinkRepository.deleteByModelIdAndProjectId(modelId, projectId);
        if (Objects.equals(model.getProjectId(), projectId)) {
            model.setProjectId(modelProjectLinkRepository.findByModelId(modelId).stream()
                    .map(ModelProjectLink::getProjectId).findFirst().orElse(null));
        }
        model.setUpdatedAt(LocalDateTime.now());
        modelRepository.save(model);
        logModification(modelId, userId, "update", "project", project.getName(), null);
        return model;
    }

    @Transactional
    public void deleteModel(Long id) {
        List<ModelFile> files = modelFileRepository.findByModelId(id);
        for (ModelFile file : files) {
            if (file.getS3Key() != null) {
                storageService.deleteFile(file.getS3Bucket(), file.getS3Key());
            }
        }
        modelFileRepository.deleteByModelId(id);
        modelCategoryLinkRepository.deleteByModelId(id);
        modelProjectLinkRepository.deleteByModelId(id);
        modelRepository.deleteById(id);
    }

    // ===================== Scene Config (camera / lighting) =====================
    @Transactional
    public Model updateSceneConfig(Long id, String cameraView, String lighting, Long userId) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        if (cameraView != null) {
            model.setCameraView(cameraView);
        }
        if (lighting != null) {
            model.setLighting(lighting);
        }
        model.setUpdatedAt(LocalDateTime.now());
        return modelRepository.save(model);
    }

    @Transactional
    public Model setDefaultVersion(Long id, Integer version, Long userId) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        int latestVersion = model.getVersion() != null ? model.getVersion() : 1;
        if (version == null || version < 1 || version > latestVersion
                || !modelVersionRepository.existsByModelIdAndVersionNum(id, version)) {
            throw new BusinessException("模型版本不存在");
        }
        String previous = String.valueOf(model.getDefaultVersion() != null ? model.getDefaultVersion() : latestVersion);
        model.setDefaultVersion(version);
        model.setUpdatedAt(LocalDateTime.now());
        logModification(id, userId, "update", "defaultVersion", previous, String.valueOf(version));
        return modelRepository.save(model);
    }

    // ===================== Thumbnail (user-set) =====================
    @Transactional
    public Model setThumbnail(Long id, MultipartFile file, Long userId) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "thumbnail.png";
        String key = "thumbnails/" + id + "/" + System.currentTimeMillis() + "_" + originalName;
        storageService.uploadFile(storageService.getBucketModels(), key, file);
        ModelFile candidate = modelFileRepository.save(ModelFile.builder()
                .modelId(id)
                .versionNum(model.getVersion() != null ? model.getVersion() : 1)
                .fileName(originalName)
                .filePath(key)
                .fileType("thumbnail")
                .fileFormat("png")
                .fileSize(file.getSize())
                .mimeType(file.getContentType() != null ? file.getContentType() : "image/png")
                .s3Key(key)
                .s3Bucket(storageService.getBucketModels())
                .sortOrder(-1000)
                .build());
        model.setThumbnailUrl(key);
        model.setUpdatedAt(LocalDateTime.now());
        Model saved = modelRepository.save(model);
        log.info("Saved thumbnail candidate {} for model {}", candidate.getId(), id);
        return saved;
    }

    @Transactional
    public Model selectThumbnail(Long id, Long fileId, Long userId) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        ModelFile candidate = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("缩略图不存在"));
        if (!Objects.equals(candidate.getModelId(), id) || !"thumbnail".equals(candidate.getFileType())) {
            throw new BusinessException("缩略图不属于当前模型");
        }
        model.setThumbnailUrl(candidate.getS3Key());
        model.setUpdatedAt(LocalDateTime.now());
        return modelRepository.save(model);
    }

    @Transactional
    public void deleteThumbnail(Long id, Long fileId, Long userId) {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        ModelFile candidate = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("缩略图不存在"));
        if (!Objects.equals(candidate.getModelId(), id) || !"thumbnail".equals(candidate.getFileType())) {
            throw new BusinessException("缩略图不属于当前模型");
        }
        if (Objects.equals(candidate.getS3Key(), model.getThumbnailUrl())) {
            throw new BusinessException("当前正在使用的缩略图不能删除，请先切换主图");
        }
        if (candidate.getS3Key() != null) {
            storageService.deleteFile(candidate.getS3Bucket(), candidate.getS3Key());
        }
        modelFileRepository.delete(candidate);
        logModification(id, userId, "delete", "thumbnail", candidate.getFileName(), null);
    }

    // ===================== Folder Upload with Auto-Detection =====================
    @Transactional
    public Model uploadFolder(MultipartFile[] files, String modelName, Long categoryId, Long projectId,
                              String categoryIdsJson, String projectIdsJson, String description,
                              String fileTypesJson, String filePathsJson, Long userId) {
        List<Long> categoryIds = parseLongIds(categoryIdsJson, categoryId);
        List<Long> projectIds = parseLongIds(projectIdsJson, projectId);
        // Create model record
        Model model = Model.builder()
                .name(modelName)
                .categoryId(firstOrNull(categoryIds))
                .projectId(firstOrNull(projectIds))
                .description(description)
                .status("processing")
                .version(1)
                .createdBy(userId)
                .build();
        model = modelRepository.save(model);
        syncRelations(model.getId(), categoryIds, projectIds);

        long totalSize = 0;
        int fileCount = 0;
        String modelBucket = storageService.getBucketModels();
        String s3Prefix = "models/" + model.getId() + "/versions/v1/";

        List<ModelFile> savedFiles = new ArrayList<>();
        int displayOrder = 0;
        int textureOrder = 0;
        int otherOrder = 0;
        Map<String, String> fileTypeOverrides = parseFileTypeOverrides(fileTypesJson);
        List<String> filePaths = parseFilePaths(filePathsJson);

        for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
            MultipartFile file = files[fileIndex];
            String uploadName = file.getOriginalFilename();
            if (uploadName == null || file.isEmpty()) continue;
            String requestedPath = fileIndex < filePaths.size() ? filePaths.get(fileIndex) : uploadName;
            String originalName = normalizeRelativePath(requestedPath, uploadName);

            String ext = getExtension(originalName).toLowerCase();
            String requestedType = fileTypeOverrides.getOrDefault(originalName, fileTypeOverrides.get(uploadName));
            String fileType = requestedType != null && Set.of("display", "texture", "reference", "other").contains(requestedType)
                    ? requestedType
                    : detectFileType(originalName, ext);
            int sortOrder = "display".equals(fileType) ? displayOrder++
                    : "texture".equals(fileType) ? textureOrder++
                    : otherOrder++;

            String s3Key = s3Prefix + "source/" + originalName;
            storageService.uploadFile(modelBucket, s3Key, file);

            ModelFile modelFile = ModelFile.builder()
                    .modelId(model.getId())
                    .versionNum(1)
                    .fileName(baseName(originalName))
                    .filePath(originalName)
                    .fileType(fileType)
                    .fileFormat(ext)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .s3Key(s3Key)
                    .s3Bucket(modelBucket)
                    .sortOrder(sortOrder)
                    .build();
            savedFiles.add(modelFileRepository.save(modelFile));

            totalSize += file.getSize();
            fileCount++;
        }

        // Create upload record
        UploadRecord record = UploadRecord.builder()
                .userId(userId)
                .modelId(model.getId())
                .fileCount(fileCount)
                .totalSize(totalSize)
                .status("success")
                .build();
        uploadRecordRepository.save(record);

        modelVersionRepository.save(ModelVersion.builder()
                .modelId(model.getId())
                .versionNum(1)
                .changeLog("初始上传")
                .createdBy(userId)
                .build());

        // Log modification
        logModification(model.getId(), userId, "create", null, null, model.getName());

        // Send message to worker for processing (format conversion, thumbnail generation)
        Map<String, Object> message = new HashMap<>();
        message.put("modelId", model.getId());
        message.put("versionNum", 1);
        message.put("userId", userId);
        message.put("bucket", modelBucket);
        message.put("prefix", s3Prefix);
        message.put("files", savedFiles.stream().map(f -> Map.of(
                "id", f.getId(),
                "fileName", f.getFileName(),
                "fileType", f.getFileType(),
                "fileFormat", f.getFileFormat(),
                "s3Key", f.getS3Key()
        )).collect(Collectors.toList()));

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.info("Sent model {} to worker for processing", model.getId());
        } catch (Exception e) {
            log.error("Failed to send model to worker: {}", e.getMessage());
        }

        return model;
    }

    @Transactional
    public ModelVersion uploadVersion(Long modelId, MultipartFile[] files, String changeLog,
                                      String fileTypesJson, String filePathsJson, Long userId) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        if (files == null || files.length == 0) {
            throw new BusinessException("请选择新版本文件");
        }

        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumDesc(modelId);
        int recordedVersion = versions.isEmpty() ? 1 : versions.get(0).getVersionNum();
        int currentVersion = model.getVersion() != null ? model.getVersion() : 1;
        int nextVersion = Math.max(recordedVersion, currentVersion) + 1;
        String modelBucket = storageService.getBucketModels();
        String s3Prefix = "models/" + modelId + "/versions/v" + nextVersion + "/";
        Map<String, String> fileTypeOverrides = parseFileTypeOverrides(fileTypesJson);
        List<String> filePaths = parseFilePaths(filePathsJson);
        List<ModelFile> savedFiles = new ArrayList<>();
        long totalSize = 0;
        int fileCount = 0;
        int displayOrder = 0;
        int textureOrder = 0;
        int otherOrder = 0;

        for (int fileIndex = 0; fileIndex < files.length; fileIndex++) {
            MultipartFile file = files[fileIndex];
            String uploadName = file.getOriginalFilename();
            if (uploadName == null || file.isEmpty()) continue;
            String requestedPath = fileIndex < filePaths.size() ? filePaths.get(fileIndex) : uploadName;
            String originalName = normalizeRelativePath(requestedPath, uploadName);
            String ext = getExtension(originalName).toLowerCase();
            String requestedType = fileTypeOverrides.getOrDefault(originalName, fileTypeOverrides.get(uploadName));
            String fileType = requestedType != null && Set.of("display", "texture", "reference", "other").contains(requestedType)
                    ? requestedType
                    : detectFileType(originalName, ext);
            int sortOrder = "display".equals(fileType) ? displayOrder++
                    : "texture".equals(fileType) ? textureOrder++
                    : otherOrder++;
            String s3Key = s3Prefix + "source/" + originalName;
            storageService.uploadFile(modelBucket, s3Key, file);

            savedFiles.add(modelFileRepository.save(ModelFile.builder()
                    .modelId(modelId)
                    .versionNum(nextVersion)
                    .fileName(baseName(originalName))
                    .filePath(originalName)
                    .fileType(fileType)
                    .fileFormat(ext)
                    .fileSize(file.getSize())
                    .mimeType(file.getContentType())
                    .s3Key(s3Key)
                    .s3Bucket(modelBucket)
                    .sortOrder(sortOrder)
                    .build()));
            totalSize += file.getSize();
            fileCount++;
        }
        if (savedFiles.isEmpty()) {
            throw new BusinessException("新版本没有有效文件");
        }

        ModelVersion version = modelVersionRepository.save(ModelVersion.builder()
                .modelId(modelId)
                .versionNum(nextVersion)
                .changeLog(changeLog == null || changeLog.isBlank() ? "更新模型文件" : changeLog.trim())
                .createdBy(userId)
                .build());
        uploadRecordRepository.save(UploadRecord.builder()
                .userId(userId)
                .modelId(modelId)
                .fileCount(fileCount)
                .totalSize(totalSize)
                .status("success")
                .build());
        model.setVersion(nextVersion);
        model.setStatus("processing");
        model.setUpdatedAt(LocalDateTime.now());
        modelRepository.save(model);
        logModification(modelId, userId, "version_bump", "version",
                "v" + currentVersion, "v" + nextVersion);

        Map<String, Object> message = new HashMap<>();
        message.put("modelId", modelId);
        message.put("versionNum", nextVersion);
        message.put("userId", userId);
        message.put("bucket", modelBucket);
        message.put("prefix", s3Prefix);
        message.put("files", savedFiles.stream().map(f -> Map.of(
                "id", f.getId(),
                "fileName", f.getFileName(),
                "fileType", f.getFileType(),
                "fileFormat", f.getFileFormat(),
                "s3Key", f.getS3Key()
        )).collect(Collectors.toList()));
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.info("Sent model {} version {} to worker for processing", modelId, nextVersion);
        } catch (Exception e) {
            log.error("Failed to send model version to worker: {}", e.getMessage());
        }
        return version;
    }

    @Transactional
    public List<ModelFile> addVersionFiles(Long modelId, Integer version, MultipartFile[] files,
                                           String fileTypesJson, String filePathsJson, Long userId) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        int latestVersion = model.getVersion() != null ? model.getVersion() : 1;
        if (version == null || version < 1 || version > latestVersion
                || !modelVersionRepository.existsByModelIdAndVersionNum(modelId, version)) {
            throw new BusinessException("模型版本不存在");
        }
        if (files == null || files.length == 0) throw new BusinessException("请选择文件");

        String bucket = storageService.getBucketModels();
        String prefix = "models/" + modelId + "/versions/v" + version + "/";
        Map<String, String> overrides = parseFileTypeOverrides(fileTypesJson);
        List<String> paths = parseFilePaths(filePathsJson);
        List<ModelFile> saved = new ArrayList<>();
        int sortBase = modelFileRepository.findByModelIdAndVersionNumOrderBySortOrder(modelId, version).size() + 10;
        long totalSize = 0;
        for (int index = 0; index < files.length; index++) {
            MultipartFile file = files[index];
            if (file == null || file.isEmpty()) continue;
            String uploadName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            String relativePath = normalizeRelativePath(index < paths.size() ? paths.get(index) : uploadName, uploadName);
            String extension = getExtension(relativePath).toLowerCase();
            String requestedType = overrides.getOrDefault(relativePath, overrides.get(uploadName));
            String fileType = requestedType != null && Set.of("display", "texture", "reference", "other").contains(requestedType)
                    ? requestedType : detectFileType(relativePath, extension);
            String key = prefix + "source/" + relativePath;
            storageService.uploadFile(bucket, key, file);
            saved.add(modelFileRepository.save(ModelFile.builder()
                    .modelId(modelId).versionNum(version)
                    .fileName(baseName(relativePath)).filePath(relativePath)
                    .fileType(fileType).fileFormat(extension)
                    .fileSize(file.getSize()).mimeType(file.getContentType())
                    .s3Key(key).s3Bucket(bucket).sortOrder(sortBase + index).build()));
            totalSize += file.getSize();
        }
        if (saved.isEmpty()) throw new BusinessException("没有可添加的有效文件");
        uploadRecordRepository.save(UploadRecord.builder().userId(userId).modelId(modelId)
                .fileCount(saved.size()).totalSize(totalSize).status("success").build());
        model.setStatus("processing");
        model.setUpdatedAt(LocalDateTime.now());
        modelRepository.save(model);
        logModification(modelId, userId, "update", "versionFiles", null, "新增 " + saved.size() + " 个文件");

        Map<String, Object> message = new HashMap<>();
        message.put("modelId", modelId);
        message.put("versionNum", version);
        message.put("userId", userId);
        message.put("bucket", bucket);
        message.put("prefix", prefix);
        message.put("files", saved.stream().map(file -> Map.of(
                "id", file.getId(), "fileName", file.getFileName(), "fileType", file.getFileType(),
                "fileFormat", file.getFileFormat(), "s3Key", file.getS3Key())).collect(Collectors.toList()));
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
        } catch (Exception exception) {
            log.error("Failed to reprocess model files: {}", exception.getMessage());
        }
        return saved;
    }

    // ===================== Download =====================
    @Transactional
    public String downloadModel(Long modelId, Long userId) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));

        // Find the main display file
        List<ModelFile> displayFiles = modelFileRepository.findByModelIdAndFileType(modelId, "display");
        if (displayFiles.isEmpty()) {
            throw new BusinessException("模型没有可下载的文件");
        }

        ModelFile file = displayFiles.get(0);

        // Record download
        downloadRecordRepository.save(DownloadRecord.builder()
                .userId(userId)
                .modelId(modelId)
                .fileName(file.getFileName())
                .build());

        return "/api/models/files/" + file.getId() + "/raw";
    }

    @Transactional
    public void streamModelArchive(Long modelId, Integer requestedVersion, Long userId,
                                   HttpServletResponse response) throws IOException {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        int latestVersion = model.getVersion() != null ? model.getVersion() : 1;
        int version = requestedVersion != null ? requestedVersion : latestVersion;
        if (version < 1 || version > latestVersion) {
            throw new BusinessException("模型版本不存在");
        }
        List<ModelFile> files = modelFileRepository.findByModelIdAndVersionNumOrderBySortOrder(modelId, version)
                .stream()
                .filter(f -> !"thumbnail".equals(f.getFileType()))
                .filter(f -> f.getSortOrder() == null || f.getSortOrder() >= 0)
                .filter(f -> f.getS3Key() != null && f.getS3Bucket() != null)
                .collect(Collectors.toList());
        if (files.isEmpty()) {
            throw new BusinessException("该版本没有可下载的原始文件");
        }

        String archiveName = safeArchiveFileName(model.getName()) + "_v" + version + ".zip";
        String encodedName = URLEncoder.encode(archiveName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"model_v" + version + ".zip\"; filename*=UTF-8''" + encodedName);
        response.setHeader("Cache-Control", "no-store");

        downloadRecordRepository.save(DownloadRecord.builder()
                .userId(userId)
                .modelId(modelId)
                .fileName(archiveName)
                .build());

        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            byte[] buffer = new byte[64 * 1024];
            for (ModelFile file : files) {
                String entryName = uniqueZipEntryName(normalizeRelativePath(logicalFilePath(file), "file_" + file.getId()), usedNames);
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = storageService.downloadFile(file.getS3Bucket(), file.getS3Key())) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    @Transactional
    public String downloadFile(Long fileId, Long userId) {
        ModelFile file = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在"));
        downloadRecordRepository.save(DownloadRecord.builder()
                .userId(userId)
                .modelId(file.getModelId())
                .fileName(file.getFileName())
                .build());
        return "/api/models/files/" + fileId + "/raw";
    }

    @Transactional
    public void renameFile(Long fileId, String requestedName, Long userId) {
        ModelFile file = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在"));
        if ("thumbnail".equals(file.getFileType())) {
            throw new BusinessException("缩略图请在缩略图管理中操作");
        }

        String nextName = normalizeRenamedFileName(requestedName, file.getFileName());
        if (nextName.equals(file.getFileName())) return;
        if (modelFileRepository.existsByModelIdAndVersionNumAndFileNameIgnoreCaseAndIdNot(
                file.getModelId(), file.getVersionNum(), nextName, file.getId())) {
            throw new BusinessException("当前版本中已存在同名文件");
        }

        String oldName = file.getFileName();
        file.setFileName(nextName);
        String oldPath = logicalFilePath(file);
        int slash = oldPath.lastIndexOf('/');
        file.setFilePath(slash >= 0 ? oldPath.substring(0, slash + 1) + nextName : nextName);
        modelFileRepository.save(file);
        modelRepository.findById(file.getModelId()).ifPresent(model -> {
            model.setUpdatedAt(LocalDateTime.now());
            modelRepository.save(model);
        });
        logModification(file.getModelId(), userId, "update", "fileName", oldName, nextName);
    }

    @Transactional
    public void deleteFile(Long fileId, Long userId) {
        ModelFile file = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在"));
        if ("thumbnail".equals(file.getFileType())) {
            throw new BusinessException("缩略图请在缩略图管理中操作");
        }
        if ("display".equals(file.getFileType())) {
            long displayCount = modelFileRepository.findByModelIdAndVersionNumOrderBySortOrder(
                            file.getModelId(), file.getVersionNum()).stream()
                    .filter(item -> "display".equals(item.getFileType()))
                    .count();
            if (displayCount <= 1) {
                throw new BusinessException("当前版本至少需要保留一个模型文件");
            }
        }

        String oldName = file.getFileName();
        if (file.getS3Bucket() != null && file.getS3Key() != null
                && modelFileRepository.countByS3BucketAndS3Key(file.getS3Bucket(), file.getS3Key()) <= 1) {
            storageService.deleteFile(file.getS3Bucket(), file.getS3Key());
        }
        modelFileRepository.delete(file);
        modelRepository.findById(file.getModelId()).ifPresent(model -> {
            model.setUpdatedAt(LocalDateTime.now());
            modelRepository.save(model);
        });
        logModification(file.getModelId(), userId, "delete", "file:" + file.getFileType(), oldName, null);
    }

    // ===================== Streaming proxy (same-origin, no MinIO exposure) =====================
    public void streamFile(Long fileId, HttpServletResponse response) throws IOException {
        ModelFile file = modelFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("文件不存在"));
        streamFromMinio(file.getS3Bucket(), file.getS3Key(), file.getFileName(), file.getMimeType(), response);
    }

    public void streamThumbnail(Long id, HttpServletResponse response) throws IOException {
        Model model = modelRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        String key = model.getThumbnailUrl();
        if (key == null || key.startsWith("http")) {
            throw new BusinessException("缩略图不存在");
        }
        int slash = key.lastIndexOf('/');
        String name = slash >= 0 ? key.substring(slash + 1) : key;
        streamFromMinio(storageService.getBucketModels(), key, name, "image/png", response);
    }

    private void streamFromMinio(String bucket, String key, String fileName, String mimeType, HttpServletResponse response) throws IOException {
        response.setContentType((mimeType != null && !mimeType.isBlank()) ? mimeType : determineContentType(key));
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "no-store");
        try (InputStream in = storageService.downloadFile(bucket, key);
             ServletOutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }

    private static boolean isGltfFamily(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".glb") || lower.endsWith(".gltf");
    }

    private static String normalizeRenamedFileName(String requestedName, String currentName) {
        if (requestedName == null || requestedName.trim().isEmpty()) {
            throw new BusinessException("文件名称不能为空");
        }
        String nextName = requestedName.trim();
        if (nextName.length() > 255 || nextName.contains("/") || nextName.contains("\\") || nextName.indexOf('\0') >= 0) {
            throw new BusinessException("文件名称不合法");
        }

        int currentDot = currentName != null ? currentName.lastIndexOf('.') : -1;
        String currentExt = currentDot > 0 ? currentName.substring(currentDot + 1) : "";
        int nextDot = nextName.lastIndexOf('.');
        String nextExt = nextDot > 0 ? nextName.substring(nextDot + 1) : "";
        if (!currentExt.isEmpty() && nextExt.isEmpty()) {
            nextName = nextName + "." + currentExt;
        } else if (!currentExt.equalsIgnoreCase(nextExt)) {
            throw new BusinessException("文件扩展名不能修改");
        }
        return nextName;
    }

    private String determineContentType(String key) {
        if (key == null) return "application/octet-stream";
        String lower = key.toLowerCase();
        if (lower.endsWith(".glb")) return "model/gltf-binary";
        if (lower.endsWith(".gltf")) return "model/gltf+json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bin")) return "application/octet-stream";
        if (lower.endsWith(".fbx")) return "application/octet-stream";
        if (lower.endsWith(".obj")) return "application/octet-stream";
        if (lower.endsWith(".stl")) return "application/octet-stream";
        return "application/octet-stream";
    }

    // ===================== File Search =====================
    public List<Map<String, Object>> searchFiles(String keyword) {
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.toLowerCase() + "%";
        List<ModelFile> files = modelFileRepository.searchFiles(pattern);
        return files.stream().map(f -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", f.getId());
            item.put("fileName", f.getFileName());
            item.put("fileType", f.getFileType());
            item.put("fileFormat", f.getFileFormat());
            item.put("fileSize", f.getFileSize());
            item.put("modelId", f.getModelId());
            String url = f.getS3Key() != null
                    ? "/api/models/files/" + f.getId() + "/raw"
                    : null;
            item.put("url", url);
            // Get model name
            modelRepository.findById(f.getModelId()).ifPresent(m -> {
                item.put("modelName", m.getName());
                item.put("modelStatus", m.getStatus());
            });
            return item;
        }).collect(Collectors.toList());
    }

    // ===================== Records =====================
    public Page<UploadRecord> getUploadRecords(Long userId, int page, int size) {
        if (userId != null) {
            return uploadRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        }
        return uploadRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<DownloadRecord> getDownloadRecords(Long userId, int page, int size) {
        if (userId != null) {
            return downloadRecordRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        }
        return downloadRecordRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<ModificationRecord> getModificationRecords(Long modelId, int page, int size) {
        return modificationRecordRepository.findByModelIdOrderByCreatedAtDesc(modelId, PageRequest.of(page, size));
    }

    // ===================== Worker Callback =====================
    @Transactional
    public void onModelProcessed(Long modelId, Integer versionNum, String status, String thumbnailKey,
                                 String convertedFileKey, String convertedFileFormat) {
        Model model = modelRepository.findById(modelId)
                .orElseThrow(() -> new BusinessException("模型不存在"));
        int processedVersion = versionNum != null ? versionNum : (model.getVersion() != null ? model.getVersion() : 1);
        boolean isLatestVersion = Objects.equals(processedVersion, model.getVersion());
        if (isLatestVersion) {
            model.setStatus(status != null ? status : "ready");
            if (thumbnailKey != null) {
                // Store the S3 key; presigned URLs are generated on read
                model.setThumbnailUrl(thumbnailKey);
            }
            model.setUpdatedAt(LocalDateTime.now());
            modelRepository.save(model);
        }

        // Add converted file if provided
        if (convertedFileKey != null && modelFileRepository
                .findByModelIdAndVersionNumAndS3Key(modelId, processedVersion, convertedFileKey).isEmpty()) {
            ModelFile convertedFile = ModelFile.builder()
                    .modelId(modelId)
                    .versionNum(processedVersion)
                    .fileName("converted." + convertedFileFormat)
                    .filePath(convertedFileKey)
                    .fileType("display")
                    .fileFormat(convertedFileFormat)
                    .s3Key(convertedFileKey)
                    .s3Bucket(storageService.getBucketModels())
                    .sortOrder(-1) // Converted file gets highest priority
                    .build();
            modelFileRepository.save(convertedFile);
            log.info("Added converted file {} for model {}", convertedFileKey, modelId);
        }
    }

    // ===================== Helper Methods =====================
    private List<Long> normalizeIds(List<Long> ids, Long fallback) {
        LinkedHashSet<Long> values = new LinkedHashSet<>();
        if (ids != null) ids.stream().filter(Objects::nonNull).forEach(values::add);
        if (values.isEmpty() && fallback != null) values.add(fallback);
        return new ArrayList<>(values);
    }

    private List<Long> parseLongIds(String json, Long fallback) {
        if (json == null || json.isBlank()) return normalizeIds(null, fallback);
        try {
            return normalizeIds(objectMapper.readValue(json, new TypeReference<List<Long>>() {}), fallback);
        } catch (Exception ignored) {
            List<Long> ids = Arrays.stream(json.replace("[", "").replace("]", "").split(","))
                    .map(String::trim).filter(value -> !value.isBlank())
                    .map(value -> {
                        try { return Long.valueOf(value); } catch (NumberFormatException e) { return null; }
                    }).filter(Objects::nonNull).collect(Collectors.toList());
            return normalizeIds(ids, fallback);
        }
    }

    private Long firstOrNull(List<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids.get(0);
    }

    private void syncRelations(Long modelId, List<Long> categoryIds, List<Long> projectIds) {
        replaceCategoryLinks(modelId, categoryIds);
        replaceProjectLinks(modelId, projectIds);
    }

    private void replaceCategoryLinks(Long modelId, List<Long> categoryIds) {
        modelCategoryLinkRepository.deleteByModelId(modelId);
        if (categoryIds != null) categoryIds.stream().distinct().forEach(categoryId ->
                modelCategoryLinkRepository.save(ModelCategoryLink.builder()
                        .modelId(modelId).categoryId(categoryId).build()));
    }

    private void replaceProjectLinks(Long modelId, List<Long> projectIds) {
        modelProjectLinkRepository.deleteByModelId(modelId);
        if (projectIds != null) projectIds.stream().distinct().forEach(projectId ->
                modelProjectLinkRepository.save(ModelProjectLink.builder()
                        .modelId(modelId).projectId(projectId).build()));
    }

    private String baseName(String path) {
        if (path == null) return "file";
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String logicalFilePath(ModelFile file) {
        String path = file.getFilePath();
        if (path == null || path.isBlank()) return file.getFileName();
        String marker = "/source/";
        int source = path.indexOf(marker);
        if (source >= 0) path = path.substring(source + marker.length());
        else if (path.startsWith("models/") || path.startsWith("thumbnails/")) return file.getFileName();
        return normalizeRelativePath(path, file.getFileName());
    }

    private String detectFileType(String filename, String ext) {
        // 3D model display files
        if (Set.of("fbx", "obj", "gltf", "glb", "stl", "ply", "dae", "3mf", "usdz").contains(ext)) {
            return "display";
        }
        // 图片先按文件名语义区分材质通道和参考效果图，避免所有 PNG 都参与材质匹配。
        if (Set.of("png", "jpg", "jpeg", "tga", "bmp", "tiff", "tif", "exr", "hdr", "psd", "webp").contains(ext)) {
            String base = filename.replaceFirst("\\.[^.]+$", "");
            if (base.matches("(?i).*(截图|效果图|预览|缩略图|渲染图|参考图|screenshot|preview|thumbnail|render|reference).*$")) {
                return "reference";
            }
            if (base.matches("(?i).*(^|[_ .-])(base[ _-]?color|basecolor|diffuse|albedo|color|normal|normalgl|normaldx|bump|metallic|metalness|metal|roughness|rough|ao|occlusion|ambient|emissive|emission|opacity|alpha|displacement|height|disp|d|n|m|r|e|a)$")) {
                return "texture";
            }
            return "reference";
        }
        return "other";
    }

    private Map<String, String> parseFileTypeOverrides(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Ignoring invalid upload file type mapping: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<String> parseFilePaths(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Ignoring invalid upload file path list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String normalizeRelativePath(String value, String fallback) {
        String raw = value == null || value.isBlank() ? fallback : value;
        if (raw == null || raw.isBlank()) raw = "unnamed-file";
        raw = raw.replace('\\', '/').replaceAll("^[A-Za-z]:/", "");
        while (raw.startsWith("/")) raw = raw.substring(1);
        List<String> parts = new ArrayList<>();
        for (String part : raw.split("/")) {
            if (part.isBlank() || ".".equals(part)) continue;
            if ("..".equals(part)) throw new BusinessException("文件路径不合法");
            parts.add(part.replaceAll("[\\x00-\\x1F:*?\"<>|]", "_"));
        }
        if (parts.isEmpty()) {
            return fallback != null && !fallback.isBlank() ? fallback.replaceAll("[\\x00-\\x1F:*?\"<>|]", "_") : "unnamed-file";
        }
        return String.join("/", parts);
    }

    private String safeArchiveFileName(String value) {
        String name = value == null || value.isBlank() ? "model" : value.trim();
        name = name.replaceAll("[\\x00-\\x1F\\\\/:*?\"<>|]", "_");
        return name.isBlank() ? "model" : name;
    }

    private String uniqueZipEntryName(String requestedName, Set<String> usedNames) {
        if (usedNames.add(requestedName)) return requestedName;
        int slash = requestedName.lastIndexOf('/');
        String directory = slash >= 0 ? requestedName.substring(0, slash + 1) : "";
        String fileName = slash >= 0 ? requestedName.substring(slash + 1) : requestedName;
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        int suffix = 2;
        String candidate;
        do {
            candidate = directory + stem + "_" + suffix++ + extension;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }

    private void logModification(Long modelId, Long userId, String action, String field, String oldVal, String newVal) {
        modificationRecordRepository.save(ModificationRecord.builder()
                .modelId(modelId)
                .userId(userId)
                .action(action)
                .fieldName(field)
                .oldValue(oldVal)
                .newValue(newVal)
                .build());
    }

    // ===================== Model Versions =====================
    public List<ModelVersion> getModelVersions(Long modelId) {
        return modelVersionRepository.findByModelIdOrderByVersionNumDesc(modelId);
    }

    @Transactional
    public ModelVersion createVersion(Long modelId, String changeLog, Long userId) {
        List<ModelVersion> versions = modelVersionRepository.findByModelIdOrderByVersionNumDesc(modelId);
        int nextVersion = versions.isEmpty() ? 1 : versions.get(0).getVersionNum() + 1;

        ModelVersion version = ModelVersion.builder()
                .modelId(modelId)
                .versionNum(nextVersion)
                .changeLog(changeLog)
                .createdBy(userId)
                .build();
        version = modelVersionRepository.save(version);

        // Update model version number
        Model model = modelRepository.findById(modelId).orElseThrow();
        model.setVersion(nextVersion);
        model.setUpdatedAt(LocalDateTime.now());
        modelRepository.save(model);

        return version;
    }
}
