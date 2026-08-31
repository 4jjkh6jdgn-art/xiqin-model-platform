-- 邀请码管理从单次生成扩展为可查看、可撤销的完整流程。
INSERT INTO permissions (name, code, module, description) VALUES
    ('查看邀请码', 'invitation:view', 'organization_registration', '查看邀请码列表、状态和使用记录'),
    ('撤销邀请码', 'invitation:revoke', 'organization_registration', '撤销尚未使用的邀请码')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('invitation:view', 'invitation:revoke')
WHERE r.code IN ('admin', 'leader')
ON CONFLICT DO NOTHING;

COMMENT ON COLUMN invitation_codes.status IS '0=unused, 1=used, 2=expired, 3=revoked';
