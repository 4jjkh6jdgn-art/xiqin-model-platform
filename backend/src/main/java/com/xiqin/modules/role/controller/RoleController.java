package com.xiqin.modules.role.controller;

import com.xiqin.common.result.Result;
import com.xiqin.modules.auth.entity.Permission;
import com.xiqin.modules.auth.entity.Role;
import com.xiqin.modules.role.service.RoleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('role:view','user:edit','user:create','user:batch_create')")
    public Result<List<Role>> listRoles() {
        return Result.success(roleService.listRoles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAnyAuthority('role:view','role:permission_assign')")
    public Result<Role> getRole(@PathVariable Long id) {
        return Result.success(roleService.getRoleById(id));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:permission_assign')")
    public Result<List<Permission>> listPermissions() {
        return Result.success(roleService.listAllPermissions());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:create')")
    public Result<Role> createRole(@RequestBody CreateRoleRequest req) {
        return Result.success(roleService.createRole(req.getName(), req.getCode(), req.getDescription()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:edit')")
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody CreateRoleRequest req) {
        return Result.success(roleService.updateRole(id, req.getName(), req.getDescription()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:delete')")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:permission_assign')")
    public Result<Role> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permIds = body.getOrDefault("permissionIds", List.of());
        return Result.success(roleService.updateRolePermissions(id, permIds));
    }

    @Data
    public static class CreateRoleRequest {
        private String name;
        private String code;
        private String description;
    }
}
