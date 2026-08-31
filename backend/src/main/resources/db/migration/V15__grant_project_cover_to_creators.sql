-- 能新建项目的角色也必须能为刚创建的项目保存封面。
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT existing.role_id, target.id
FROM role_permissions existing
JOIN permissions current_permission ON current_permission.id = existing.permission_id
CROSS JOIN permissions target
WHERE current_permission.code IN ('project:create', 'project:edit')
  AND target.code = 'project:cover_manage'
ON CONFLICT DO NOTHING;
