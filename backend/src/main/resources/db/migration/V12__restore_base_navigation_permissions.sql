-- Restore the two baseline permissions required by every authenticated user.
-- These rows may be missing in upgraded or manually adjusted databases even
-- when the original V5 migration is recorded as applied.
INSERT INTO permissions (name, code, module, description) VALUES
    ('查看仪表盘', 'dashboard:view', 'dashboard', '进入仪表盘并查看统计信息'),
    ('编辑个人资料', 'profile:edit', 'profile', '修改自己的个人资料')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.code IN ('dashboard:view', 'profile:edit')
ON CONFLICT DO NOTHING;
