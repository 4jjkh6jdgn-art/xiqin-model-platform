package com.xiqin.modules.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<com.xiqin.modules.auth.entity.Permission, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM role_permissions WHERE role_id = :roleId", nativeQuery = true)
    void deleteByRoleId(@Param("roleId") Long roleId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO role_permissions (role_id, permission_id) VALUES (:roleId, :permissionId)", nativeQuery = true)
    void insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
