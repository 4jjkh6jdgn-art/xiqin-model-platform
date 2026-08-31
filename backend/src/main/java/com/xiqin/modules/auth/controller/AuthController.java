package com.xiqin.modules.auth.controller;

import com.xiqin.common.result.PageResult;
import com.xiqin.common.result.Result;
import com.xiqin.modules.auth.dto.*;
import com.xiqin.modules.auth.entity.InvitationCode;
import com.xiqin.modules.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<String> register(@Valid @RequestBody RegisterRequest req) {
        String msg = authService.register(req);
        return Result.success(msg);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @GetMapping("/me")
    public Result<UserVO> currentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(authService.getCurrentUserInfo(userId));
    }

    @GetMapping("/invitation/validate")
    public Result<Map<String, Boolean>> validateInvitation(@RequestParam String code) {
        boolean valid = authService.validateInvitationCode(code);
        return Result.success(Map.of("valid", valid));
    }

    @PostMapping("/invitation/generate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('invitation:create')")
    public Result<Map<String, Object>> generateInvitation(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        InvitationCode invitation = authService.generateInvitationCode(userId);
        return Result.success(Map.of(
                "id", invitation.getId(),
                "code", invitation.getCode(),
                "expiresAt", invitation.getExpiresAt(),
                "createdAt", invitation.getCreatedAt()
        ));
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('invitation:view','invitation:create')")
    public Result<PageResult<InvitationCode>> listInvitations(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(PageResult.of(authService.listInvitationCodes(status, page, size)));
    }

    @GetMapping("/invitations/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('invitation:view','invitation:create')")
    public Result<Map<String, Long>> invitationSummary() {
        return Result.success(authService.invitationSummary());
    }

    @PostMapping("/invitations/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('invitation:revoke')")
    public Result<Void> revokeInvitation(@PathVariable Long id) {
        authService.revokeInvitationCode(id);
        return Result.success();
    }

    @GetMapping("/registration-requests")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registration:view')")
    public Result<?> listRegistrationRequests(
            @RequestParam(defaultValue = "0") Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(authService.listRegistrationRequests(status, page, size));
    }

    @PostMapping("/registration-requests/{id}/review")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registration:approve')")
    public Result<Void> reviewRegistration(
            @PathVariable Long id,
            @RequestBody ReviewRegistrationRequest req,
            HttpServletRequest request) {
        Long reviewerId = (Long) request.getAttribute("userId");
        authService.reviewRegistration(id, req, reviewerId);
        return Result.success();
    }
}
