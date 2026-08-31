package com.xiqin.modules.role.service;

import com.xiqin.common.exception.BusinessException;
import com.xiqin.modules.auth.entity.Permission;
import com.xiqin.modules.auth.entity.Role;
import com.xiqin.modules.auth.repository.PermissionRepository;
import com.xiqin.modules.auth.repository.RolePermissionRepository;
import com.xiqin.modules.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String ADMIN_ROLE_CODE = "admin";
    private static final Set<String> REQUIRED_BASE_PERMISSION_CODES = Set.of("dashboard:view", "profile:edit");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    public List<Permission> listAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional
    public Role createRole(String name, String code, String description) {
        if (roleRepository.existsByCode(code)) {
            throw new BusinessException("角色编码已存在");
        }
        if (roleRepository.existsByName(name)) {
            throw new BusinessException("角色名称已存在");
        }
        Role role = Role.builder()
                .name(name)
                .code(code)
                .description(description)
                .isSystem(false)
                .build();
        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long id, String name, String description) {
        Role role = getRoleById(id);
        if (Boolean.TRUE.equals(role.getIsSystem()) && name != null && !name.equals(role.getName())) {
            throw new BusinessException("系统角色名称不可修改");
        }
        if (name != null) role.setName(name);
        if (description != null) role.setDescription(description);
        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = getRoleById(id);
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BusinessException("系统角色不可删除");
        }
        roleRepository.delete(role);
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = getRoleById(roleId);
        if (ADMIN_ROLE_CODE.equals(role.getCode())) {
            throw new BusinessException("系统管理员权限不可修改");
        }
        // 仅保留 permissions 表中真实存在的 ID：页面长时间未刷新时可能携带历史残留 ID，
        // 直接插入会触发 role_permissions_permission_id_fkey 外键冲突，导致整个保存失败
        List<Long> requested = permissionIds == null ? List.of()
                : permissionIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<Permission> requestedPermissions = permissionRepository.findAllById(requested);
        List<Permission> validPermissions = new ArrayList<>(requestedPermissions);
        REQUIRED_BASE_PERMISSION_CODES.stream()
                .map(permissionRepository::findByCode)
                .flatMap(java.util.Optional::stream)
                .filter(required -> validPermissions.stream().noneMatch(permission -> permission.getId().equals(required.getId())))
                .forEach(validPermissions::add);
        rolePermissionRepository.deleteByRoleId(roleId);
        for (Permission permission : validPermissions) {
            rolePermissionRepository.insert(roleId, permission.getId());
        }
        if (requestedPermissions.size() < requested.size()) {
            List<Long> skipped = requested.stream()
                    .filter(id -> requestedPermissions.stream().noneMatch(p -> p.getId().equals(id)))
                    .toList();
            log.warn("Skipped {} stale permission ids for role {}: {}", skipped.size(), roleId, skipped);
        }
        log.info("Assigned {} permissions to role {}", validPermissions.size(), roleId);
    }

    @Transactional
    public Role updateRolePermissions(Long roleId, List<Long> permissionIds) {
        assignPermissions(roleId, permissionIds);
        return roleRepository.findById(roleId).orElseThrow();
    }
}
