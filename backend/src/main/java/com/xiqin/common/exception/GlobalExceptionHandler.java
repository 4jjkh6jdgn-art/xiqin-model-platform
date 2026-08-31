package com.xiqin.common.exception;

import com.xiqin.common.result.Result;
import com.xiqin.modules.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final NotificationService notificationService;

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusiness(BusinessException e, HttpServletRequest request) {
        log.warn("Business error: {}", e.getMessage());
        notifyUploadFailure(request, e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleBadCredentials(BadCredentialsException e) {
        return Result.error(401, "用户名或密码错误");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(403, "没有权限访问");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        String details = deepestMessage(e).toLowerCase();
        if (details.contains("ux_model_categories_normalized_name")
                || details.contains("ux_project_categories_normalized_name")) {
            return Result.error(409, "分类名称已存在");
        }
        log.warn("Data integrity conflict: {}", deepestMessage(e));
        return Result.error(409, "当前数据已被关联或与现有数据冲突，无法完成操作");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result<Void> handleMaxSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        notifyUploadFailure(request, "文件大小超过限制");
        return Result.error(413, "文件大小超过限制");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException e, HttpServletRequest request) {
        // 下载、预览或页面跳转时，浏览器可能主动取消尚未完成的响应流。
        // 这是正常的客户端断开，不应记为系统故障，也不能再尝试向已关闭的响应写入错误 JSON。
        log.debug("Client disconnected while streaming {}", request == null ? "" : request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleGeneral(Exception e, HttpServletRequest request) {
        log.error("Unexpected error", e);
        notifyUploadFailure(request, e.getMessage());
        return Result.error(500, "服务器内部错误: " + e.getMessage());
    }

    private void notifyUploadFailure(HttpServletRequest request, String message) {
        if (request == null || !"POST".equalsIgnoreCase(request.getMethod())) return;
        String uri = request.getRequestURI();
        if (uri == null || (!uri.contains("upload") && !uri.matches(".*/projects/\\d+/files.*"))) return;
        Object attr = request.getAttribute("userId");
        Long userId = attr instanceof Long ? (Long) attr : null;
        notificationService.notifyUploadError(uri, message == null ? "未知上传错误" : message, userId);
    }

    private String deepestMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? "" : current.getMessage();
    }
}
