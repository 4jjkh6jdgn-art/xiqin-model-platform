package com.xiqin.modules.user.service;

import com.xiqin.common.exception.BusinessException;
import com.xiqin.modules.auth.dto.UpdateUserRequest;
import com.xiqin.modules.auth.dto.UserVO;
import com.xiqin.modules.auth.entity.Role;
import com.xiqin.modules.auth.entity.User;
import com.xiqin.modules.auth.repository.RoleRepository;
import com.xiqin.modules.auth.repository.UserRepository;
import com.xiqin.modules.auth.service.AuthService;
import com.xiqin.modules.user.dto.BatchCreateUserRequest;
import com.xiqin.modules.user.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String INITIAL_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public Page<UserVO> searchUsers(String keyword, Integer status, int page, int size) {
        String pattern = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.toLowerCase() + "%";
        return userRepository.searchUsers(pattern, status, PageRequest.of(page, size))
                .map(authService::toUserVO);
    }

    public UserVO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return authService.toUserVO(user);
    }

    @Transactional
    public UserVO createUser(CreateUserRequest req) {
        String username = req.getUsername().trim();
        if (userRepository.existsByUsername(username)) throw new BusinessException("用户名已存在");
        String email = req.getEmail() == null || req.getEmail().isBlank() ? null : req.getEmail().trim();
        if (email != null && userRepository.existsByEmail(email)) throw new BusinessException("邮箱已被使用");
        Role role = roleRepository.findById(req.getRoleId())
                .orElseThrow(() -> new BusinessException("角色不存在"));
        User user = User.builder()
                .username(username)
                .email(email)
                .phone(req.getPhone() == null || req.getPhone().isBlank() ? null : req.getPhone().trim())
                .passwordHash(passwordEncoder.encode(INITIAL_PASSWORD))
                .role(role)
                .status(1)
                .build();
        return authService.toUserVO(userRepository.save(user));
    }

    @Transactional
    public List<UserVO> batchCreateUsers(BatchCreateUserRequest req) {
        Role role = roleRepository.findById(req.getRoleId())
                .orElseThrow(() -> new BusinessException("角色不存在"));
        List<String> usernames = new ArrayList<>();
        for (int i = 0; i < req.getCount(); i++) {
            int number = req.getStartNumber() + i;
            String username = req.getPrefix().trim() + String.format("%0" + req.getNumberWidth() + "d", number);
            if (username.length() > 32) throw new BusinessException("生成的用户名过长，请缩短前缀或序号位数");
            if (userRepository.existsByUsername(username)) {
                throw new BusinessException("用户名 " + username + " 已存在，未创建任何用户");
            }
            usernames.add(username);
        }
        String encodedPassword = passwordEncoder.encode(INITIAL_PASSWORD);
        LocalDateTime now = LocalDateTime.now();
        List<User> users = usernames.stream().map(username -> User.builder()
                .username(username).passwordHash(encodedPassword).role(role).status(1)
                .createdAt(now).updatedAt(now).build()).toList();
        return userRepository.saveAll(users).stream().map(authService::toUserVO).toList();
    }

    @Transactional
    public UserVO updateUser(Long id, UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        if (req.getStatus() != null) user.setStatus(req.getStatus());
        if (req.getGroupLeaderId() != null) user.setGroupLeaderId(req.getGroupLeaderId());

        if (req.getRoleId() != null) {
            Role role = roleRepository.findById(req.getRoleId())
                    .orElseThrow(() -> new BusinessException("角色不存在"));
            user.setRole(role);
        }

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }

        userRepository.save(user);
        return authService.toUserVO(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("用户不存在");
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public UserVO updateProfile(Long userId, UpdateUserRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }

        userRepository.save(user);
        return authService.toUserVO(user);
    }
}
