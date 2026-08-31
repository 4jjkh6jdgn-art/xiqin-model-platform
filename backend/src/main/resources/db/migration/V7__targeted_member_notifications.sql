ALTER TABLE system_messages
    ADD COLUMN IF NOT EXISTS recipient_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_system_messages_recipient_unread
    ON system_messages(recipient_user_id, is_read, created_at DESC);

-- 每个登录账号都需要读取自己的系统提醒；发布权限仍只按角色单独分配。
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('notification:view', 'notification:read')
ON CONFLICT DO NOTHING;
