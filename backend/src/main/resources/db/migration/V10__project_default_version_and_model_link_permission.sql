ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS default_version INTEGER NOT NULL DEFAULT 1;

UPDATE projects
SET default_version = LEAST(
    GREATEST(COALESCE(default_version, 1), 1),
    COALESCE(current_version, 1)
);

INSERT INTO permissions (name, code, module, description) VALUES
    ('设置默认项目版本', 'project:default_version_manage', 'project_detail', '设置进入项目详情时默认显示的项目版本')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

-- 已经拥有项目编辑能力的角色同步获得默认版本管理能力。
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT existing.role_id, target.id
FROM role_permissions existing
JOIN permissions current_permission ON current_permission.id = existing.permission_id
CROSS JOIN permissions target
WHERE current_permission.code = 'project:edit'
  AND target.code = 'project:default_version_manage'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'project:default_version_manage'
WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;
