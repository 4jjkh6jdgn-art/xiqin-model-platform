-- 仪表盘业务入口对应的细粒度权限。
INSERT INTO permissions (name, code, module, description) VALUES
    ('查看模型处理队列', 'model:process_records_view', 'model_data', '查看平台模型的待处理、处理中、失败和可用状态'),
    ('查看资产总览', 'storage:asset_view', 'storage', '查看模型资产与项目资料总览，不包含存储凭据和位置配置')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

-- 原本可以查看模型的角色可查看处理状态；不额外开放模型内容。
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT existing.role_id, target.id
FROM role_permissions existing
JOIN permissions current_permission ON current_permission.id = existing.permission_id
CROSS JOIN permissions target
WHERE current_permission.code = 'model:view'
  AND target.code = 'model:process_records_view'
ON CONFLICT DO NOTHING;

-- 能查看模型或项目资料的角色，可进入统一资产总览。
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT existing.role_id, target.id
FROM role_permissions existing
JOIN permissions current_permission ON current_permission.id = existing.permission_id
CROSS JOIN permissions target
WHERE current_permission.code IN ('model:view', 'project:file_view')
  AND target.code = 'storage:asset_view'
ON CONFLICT DO NOTHING;

-- 管理员始终拥有本迁移增加的全部能力。
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('model:process_records_view', 'storage:asset_view')
WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;
