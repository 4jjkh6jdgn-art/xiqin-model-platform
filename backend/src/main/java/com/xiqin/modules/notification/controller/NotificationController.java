package com.xiqin.modules.notification.controller;

import com.xiqin.common.result.PageResult;
import com.xiqin.common.result.Result;
import com.xiqin.modules.notification.entity.SystemMessage;
import com.xiqin.modules.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<PageResult<SystemMessage>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            HttpServletRequest request,
            Authentication authentication) {
        Long viewerId = (Long) request.getAttribute("userId");
        return Result.success(PageResult.of(service.search(viewerId, isAdmin(authentication),
                keyword, type, unread, page, size)));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Long>> unreadCount(HttpServletRequest request, Authentication authentication) {
        Long viewerId = (Long) request.getAttribute("userId");
        return Result.success(Map.of("count", service.unreadCount(viewerId, isAdmin(authentication))));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest request, Authentication authentication) {
        Long viewerId = (Long) request.getAttribute("userId");
        service.markRead(id, viewerId, isAdmin(authentication));
        return Result.success();
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> markAllRead(HttpServletRequest request, Authentication authentication) {
        Long viewerId = (Long) request.getAttribute("userId");
        service.markAllRead(viewerId, isAdmin(authentication));
        return Result.success();
    }

    @PostMapping("/feedback")
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('model:feedback','project:feedback')")
    public Result<SystemMessage> feedback(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Long sourceId = number(body.get("sourceId"));
        Long projectId = number(body.get("projectId"));
        String sourceType = String.valueOf(body.getOrDefault("sourceType", "project"));
        String title = String.valueOf(body.getOrDefault("title", "问题反馈"));
        String content = String.valueOf(body.getOrDefault("content", ""));
        return Result.success(service.create("feedback", "warning", title, content,
                sourceType, sourceId, projectId, userId));
    }

    @PostMapping("/reminders")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('notification:publish')")
    public Result<SystemMessage> createReminder(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(service.create("reminder", "info",
                String.valueOf(body.getOrDefault("title", "工作提醒")),
                String.valueOf(body.getOrDefault("content", "")),
                "system", null, number(body.get("projectId")), userId));
    }

    private Long number(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        return Long.valueOf(value.toString());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
