package com.xiqin.modules.auth.service;

import com.xiqin.common.exception.BusinessException;
import com.xiqin.config.security.JwtTokenProvider;
import com.xiqin.modules.auth.dto.*;
import com.xiqin.modules.auth.entity.*;
import com.xiqin.modules.auth.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    private final RegistrationRequestRepository regRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public String register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        Role memberRole = roleRepository.findByCode("member")
                .orElseThrow(() -> new BusinessException("系统角色未初始化"));

        // Check invitation code
        String normalizedInvitationCode = req.getInvitationCode() == null
                ? null : req.getInvitationCode().trim().toUpperCase();
        boolean hasInvitation = normalizedInvitationCode != null && !normalizedInvitationCode.isBlank();
        Integer userStatus;
        InvitationCode inviteCode = null;

        if (hasInvitation) {
            // 对邀请码行加锁，避免两个注册请求同时消费同一个单次邀请码。
            inviteCode = invitationCodeRepository.findByCodeForUpdate(normalizedInvitationCode)
                    .orElseThrow(() -> new BusinessException("邀请码无效"));

            if (inviteCode.getStatus() != 0) {
                throw new BusinessException("邀请码已被使用或已过期");
            }
            if (inviteCode.getExpiresAt() != null && inviteCode.getExpiresAt().isBefore(LocalDateTime.now())) {
                inviteCode.setStatus(2);
                invitationCodeRepository.save(inviteCode);
                throw new BusinessException("邀请码已过期");
            }
            userStatus = 1; // Directly active with invitation code
        } else {
            userStatus = 0; // Pending approval
        }

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(memberRole)
                .status(userStatus)
                .invitationCode(hasInvitation ? normalizedInvitationCode : null)
                .build();
        userRepository.save(user);

        // Mark invitation code as used
        if (inviteCode != null) {
            inviteCode.setUsedBy(user.getId());
            inviteCode.setStatus(1);
            invitationCodeRepository.save(inviteCode);
        } else {
            // Create registration approval request
            RegistrationRequest regReq = RegistrationRequest.builder()
                    .userId(user.getId())
                    .status(0)
                    .build();
            regRequestRepository.save(regReq);
        }

        return hasInvitation ? "注册成功，请直接登录" : "注册成功，等待组长审批";
    }

    public LoginResponse login(LoginRequest req) {
        // Pre-check user existence and status for friendly error messages
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (user.getStatus() == 0) {
            throw new BusinessException("账号正在等待审批，请联系组长");
        }
        if (user.getStatus() == 2) {
            throw new BusinessException("账号已被禁用");
        }
        if (user.getStatus() == 3) {
            throw new BusinessException("注册申请已被拒绝");
        }

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = tokenProvider.generateToken(userDetails, user.getId());

        List<String> permissions = user.getRole() != null
                ? user.getRole().getPermissions().stream().map(Permission::getCode).collect(Collectors.toList())
                : List.of();

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatarUrl())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .roleCode(user.getRole() != null ? user.getRole().getCode() : null)
                .permissions(permissions)
                .build();
    }

    @Transactional
    public void reviewRegistration(Long requestId, ReviewRegistrationRequest req, Long reviewerId) {
        RegistrationRequest regReq = regRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("注册申请不存在"));

        if (regReq.getStatus() != 0) {
            throw new BusinessException("该申请已被处理");
        }

        boolean approved = "approve".equalsIgnoreCase(req.getAction());
        regReq.setStatus(approved ? 1 : 2);
        regReq.setReviewedBy(reviewerId);
        regReq.setReviewNote(req.getReviewNote());
        regReq.setReviewedAt(LocalDateTime.now());
        regRequestRepository.save(regReq);

        // Update user status
        User user = userRepository.findById(regReq.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setStatus(approved ? 1 : 3);
        userRepository.save(user);
    }

    @Transactional
    public InvitationCode generateInvitationCode(Long createdBy) {
        String code = "XQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        InvitationCode invite = InvitationCode.builder()
                .code(code)
                .createdBy(createdBy)
                .status(0)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        return invitationCodeRepository.save(invite);
    }

    @Transactional
    public boolean validateInvitationCode(String code) {
        if (code == null || code.isBlank()) return false;
        return invitationCodeRepository.findByCode(code.trim().toUpperCase())
                .map(ic -> {
                    if (ic.getStatus() != 0) return false;
                    if (ic.getExpiresAt() != null && !ic.getExpiresAt().isAfter(LocalDateTime.now())) {
                        ic.setStatus(2);
                        invitationCodeRepository.save(ic);
                        return false;
                    }
                    return true;
                }).orElse(false);
    }

    @Transactional
    public org.springframework.data.domain.Page<InvitationCode> listInvitationCodes(Integer status, int page, int size) {
        invitationCodeRepository.expireOverdue(LocalDateTime.now());
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return status == null
                ? invitationCodeRepository.findAllByOrderByCreatedAtDesc(pageable)
                : invitationCodeRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional
    public Map<String, Long> invitationSummary() {
        invitationCodeRepository.expireOverdue(LocalDateTime.now());
        return Map.of(
                "available", invitationCodeRepository.countByStatus(0),
                "used", invitationCodeRepository.countByStatus(1),
                "expired", invitationCodeRepository.countByStatus(2),
                "revoked", invitationCodeRepository.countByStatus(3),
                "total", invitationCodeRepository.count()
        );
    }

    @Transactional
    public void revokeInvitationCode(Long id) {
        InvitationCode invitationCode = invitationCodeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("邀请码不存在"));
        if (invitationCode.getStatus() != 0) {
            throw new BusinessException("只有未使用的邀请码可以撤销");
        }
        invitationCode.setStatus(3);
        invitationCodeRepository.save(invitationCode);
    }

    public org.springframework.data.domain.Page<RegistrationRequest> listRegistrationRequests(Integer status, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        if (status != null) {
            return regRequestRepository.findByStatus(status, pageable);
        }
        return regRequestRepository.findAll(pageable);
    }

    public UserVO getCurrentUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return toUserVO(user);
    }

    public UserVO toUserVO(User user) {
        List<String> permissions = user.getRole() != null
                ? user.getRole().getPermissions().stream().map(Permission::getCode).collect(Collectors.toList())
                : List.of();

        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatarUrl())
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .roleCode(user.getRole() != null ? user.getRole().getCode() : null)
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .status(user.getStatus())
                .statusText(getStatusText(user.getStatus()))
                .groupLeaderId(user.getGroupLeaderId())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .permissions(permissions)
                .build();
    }

    private String getStatusText(Integer status) {
        return switch (status) {
            case 0 -> "待审批";
            case 1 -> "正常";
            case 2 -> "已禁用";
            case 3 -> "已拒绝";
            default -> "未知";
        };
    }
}
