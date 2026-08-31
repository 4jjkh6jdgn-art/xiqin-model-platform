-- 项目封面由对象存储保存，数据库仅记录受保护对象的定位信息。
ALTER TABLE projects ADD COLUMN IF NOT EXISTS cover_s3_key VARCHAR(512);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS cover_mime_type VARCHAR(128);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS cover_file_name VARCHAR(255);

INSERT INTO permissions (name, code, module, description) VALUES
    ('管理项目封面', 'project:cover_manage', 'project_detail', '上传、更换或移除项目封面')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

-- 已拥有项目编辑能力的角色同步获得封面管理能力。
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT existing.role_id, target.id
FROM role_permissions existing
JOIN permissions current_permission ON current_permission.id = existing.permission_id
CROSS JOIN permissions target
WHERE current_permission.code = 'project:edit'
  AND target.code = 'project:cover_manage'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'project:cover_manage'
WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;
