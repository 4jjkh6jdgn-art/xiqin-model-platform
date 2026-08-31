package com.xiqin.modules.notification.service;

import com.xiqin.common.exception.BusinessException;
import com.xiqin.modules.auth.repository.UserRepository;
import com.xiqin.modules.notification.entity.SystemMessage;
import com.xiqin.modules.notification.repository.SystemMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SystemMessageRepository repository;
    private final UserRepository userRepository;

    public Page<SystemMessage> search(Long viewerId, boolean isAdmin, String keyword, String type,
                                      Boolean unread, int page, int size) {
        String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.toLowerCase() + "%";
        String normalizedType = type == null || type.isBlank() ? null : type;
        return repository.searchVisible(viewerId, isAdmin, pattern, normalizedType,
                Boolean.TRUE.equals(unread) ? true : null,
                PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    public long unreadCount(Long viewerId, boolean isAdmin) {
        return repository.countVisibleUnread(viewerId, isAdmin);
    }

    @Transactional
    public void markRead(Long id, Long viewerId, boolean isAdmin) {
        SystemMessage message = repository.findById(id).orElseThrow(() -> new BusinessException("消息不存在"));
        if (!canAccess(message, viewerId, isAdmin)) {
            throw new BusinessException("无权查看该消息");
        }
        message.setIsRead(true);
        repository.save(message);
    }

    @Transactional
    public void markAllRead(Long viewerId, boolean isAdmin) {
        repository.markAllVisibleRead(viewerId, isAdmin);
    }

    @Transactional
    public SystemMessage create(String type, String severity, String title, String content,
                                String sourceType, Long sourceId, Long projectId, Long userId) {
        return create(type, severity, title, content, sourceType, sourceId, projectId, userId, null);
    }

    @Transactional
    public SystemMessage create(String type, String severity, String title, String content,
                                String sourceType, Long sourceId, Long projectId, Long userId,
                                Long recipientUserId) {
        String userName = userId == null ? "系统" : userRepository.findById(userId)
                .map(user -> user.getUsername()).orElse("用户 #" + userId);
        return repository.save(SystemMessage.builder()
                .messageType(type)
                .severity(severity == null || severity.isBlank() ? "info" : severity)
                .title(title)
                .content(content)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .projectId(projectId)
                .createdBy(userId)
                .createdByName(userName)
                .recipientUserId(recipientUserId)
                .build());
    }

    private boolean canAccess(SystemMessage message, Long viewerId, boolean isAdmin) {
        if (message.getRecipientUserId() == null) return isAdmin;
        return Objects.equals(message.getRecipientUserId(), viewerId);
    }

    public void notifyUploadError(String requestUri, String message, Long userId) {
        try {
            create("upload_error", "error", "文件上传失败", message,
                    requestUri != null && requestUri.contains("/models") ? "model" : "project_file",
                    null, null, userId);
        } catch (Exception ignored) {
            // The original upload error must remain the primary response.
        }
    }
}
