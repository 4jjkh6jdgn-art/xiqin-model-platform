ALTER TABLE models
    ADD COLUMN IF NOT EXISTS default_version INTEGER NOT NULL DEFAULT 1;

UPDATE models
SET default_version = LEAST(GREATEST(COALESCE(default_version, 1), 1), COALESCE(version, 1));

CREATE TABLE IF NOT EXISTS storage_locations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    address VARCHAR(500) NOT NULL,
    mount_path VARCHAR(500),
    username VARCHAR(120),
    credential_secret VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'unknown',
    is_current BOOLEAN NOT NULL DEFAULT FALSE,
    is_protected BOOLEAN NOT NULL DEFAULT FALSE,
    asset_count BIGINT NOT NULL DEFAULT 0,
    used_bytes BIGINT NOT NULL DEFAULT 0,
    last_scan_at TIMESTAMP,
    last_error VARCHAR(800),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_storage_locations_single_current
    ON storage_locations (is_current) WHERE is_current = TRUE;

INSERT INTO storage_locations (name, type, address, status, is_current, is_protected)
SELECT '平台对象存储', 'MINIO', 'models / thumbnails / avatars', 'online', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM storage_locations);

INSERT INTO permissions (name, code, module, description) VALUES
    ('添加用户', 'user:create', 'organization_user', '添加单个用户，初始密码为 123456'),
    ('批量生成用户', 'user:batch_create', 'organization_user', '按账号规则批量生成用户'),
    ('设置默认模型版本', 'model:default_version_manage', 'model_detail', '设置模型进入详情时默认显示的版本'),
    ('查看存储空间', 'storage:view', 'storage', '进入存储空间管理并查看容量与状态'),
    ('添加存储位置', 'storage:create', 'storage', '添加本地目录、对象存储或远程存储位置'),
    ('编辑存储位置', 'storage:edit', 'storage', '修改存储位置并测试连接'),
    ('切换当前存储', 'storage:activate', 'storage', '把可用的存储位置设为当前位置'),
    ('扫描存储位置', 'storage:scan', 'storage', '扫描存储文件并更新资产数与占用空间'),
    ('删除存储位置', 'storage:delete', 'storage', '删除非当前且非平台保护的存储位置')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'model:default_version_manage'
WHERE r.code = 'leader'
ON CONFLICT DO NOTHING;
