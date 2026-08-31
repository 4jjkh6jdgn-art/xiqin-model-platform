-- ============================================================
-- 西秦模型管理平台 - 数据库初始化脚本 v1
-- ============================================================

-- ===================== Roles & Permissions (RBAC) =====================
CREATE TABLE IF NOT EXISTS roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    code        VARCHAR(64) NOT NULL UNIQUE,
    description TEXT,
    is_system   BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS permissions (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(128) NOT NULL UNIQUE,
    module      VARCHAR(64) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ===================== Users =====================
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64) NOT NULL UNIQUE,
    email           VARCHAR(128) UNIQUE,
    phone           VARCHAR(20),
    password_hash   VARCHAR(256) NOT NULL,
    avatar_url      VARCHAR(512),
    role_id         BIGINT REFERENCES roles(id),
    status          SMALLINT NOT NULL DEFAULT 0,  -- 0=pending, 1=active, 2=disabled, 3=rejected
    group_leader_id BIGINT REFERENCES users(id),
    invitation_code VARCHAR(64),
    last_login_at   TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Registration Requests =====================
CREATE TABLE IF NOT EXISTS registration_requests (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      SMALLINT NOT NULL DEFAULT 0,  -- 0=pending, 1=approved, 2=rejected
    reviewed_by BIGINT REFERENCES users(id),
    review_note TEXT,
    reviewed_at TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Invitation Codes =====================
CREATE TABLE IF NOT EXISTS invitation_codes (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    created_by  BIGINT NOT NULL REFERENCES users(id),
    used_by     BIGINT REFERENCES users(id),
    status      SMALLINT NOT NULL DEFAULT 0,  -- 0=unused, 1=used, 2=expired
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Model Categories =====================
CREATE TABLE IF NOT EXISTS model_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    parent_id   BIGINT REFERENCES model_categories(id) ON DELETE SET NULL,
    code        VARCHAR(64),
    description TEXT,
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Project Categories =====================
CREATE TABLE IF NOT EXISTS project_categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    parent_id   BIGINT REFERENCES project_categories(id) ON DELETE SET NULL,
    code        VARCHAR(64),
    description TEXT,
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Projects =====================
CREATE TABLE IF NOT EXISTS projects (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(256) NOT NULL,
    category_id   BIGINT REFERENCES project_categories(id),
    description   TEXT,
    status        VARCHAR(32) DEFAULT 'planning',  -- planning, in_progress, completed, archived
    priority      SMALLINT DEFAULT 1,
    created_by    BIGINT NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_members (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       VARCHAR(64) DEFAULT 'member',  -- leader, member
    joined_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, user_id)
);

CREATE TABLE IF NOT EXISTS project_tasks (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title       VARCHAR(256) NOT NULL,
    description TEXT,
    assignee_id BIGINT REFERENCES users(id),
    status      VARCHAR(32) DEFAULT 'pending',  -- pending, in_progress, completed, cancelled
    priority    SMALLINT DEFAULT 1,
    deadline    TIMESTAMP,
    completed_at TIMESTAMP,
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS project_phases (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    sort_order  INT DEFAULT 0,
    status      VARCHAR(32) DEFAULT 'pending',
    start_date  DATE,
    end_date    DATE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Models =====================
CREATE TABLE IF NOT EXISTS models (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(256) NOT NULL,
    category_id   BIGINT REFERENCES model_categories(id),
    project_id    BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    description   TEXT,
    status        VARCHAR(32) DEFAULT 'draft',  -- draft, processing, ready, archived
    version       INT DEFAULT 1,
    thumbnail_url VARCHAR(512),
    created_by    BIGINT NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_files (
    id          BIGSERIAL PRIMARY KEY,
    model_id    BIGINT NOT NULL REFERENCES models(id) ON DELETE CASCADE,
    file_name   VARCHAR(512) NOT NULL,
    file_path   VARCHAR(1024) NOT NULL,
    file_type   VARCHAR(32) NOT NULL,  -- display, texture, other, thumbnail
    file_format VARCHAR(32),           -- fbx, obj, gltf, glb, png, jpg, etc.
    file_size   BIGINT,
    mime_type   VARCHAR(128),
    s3_key      VARCHAR(1024),
    s3_bucket   VARCHAR(128),
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_versions (
    id            BIGSERIAL PRIMARY KEY,
    model_id      BIGINT NOT NULL REFERENCES models(id) ON DELETE CASCADE,
    version_num   INT NOT NULL,
    change_log    TEXT,
    created_by    BIGINT NOT NULL REFERENCES users(id),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(model_id, version_num)
);

-- ===================== Records (Upload / Download / Modification) =====================
CREATE TABLE IF NOT EXISTS upload_records (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    model_id    BIGINT REFERENCES models(id) ON DELETE SET NULL,
    file_count  INT DEFAULT 0,
    total_size  BIGINT DEFAULT 0,
    status      VARCHAR(32) DEFAULT 'success',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS download_records (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    model_id    BIGINT NOT NULL REFERENCES models(id) ON DELETE CASCADE,
    file_name   VARCHAR(512),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS modification_records (
    id          BIGSERIAL PRIMARY KEY,
    model_id    BIGINT NOT NULL REFERENCES models(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    action      VARCHAR(64) NOT NULL,  -- create, update, delete, status_change, version_bump
    field_name  VARCHAR(128),
    old_value    TEXT,
    new_value    TEXT,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Project Files =====================
CREATE TABLE IF NOT EXISTS project_files (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id      BIGINT REFERENCES project_tasks(id) ON DELETE SET NULL,
    file_name   VARCHAR(512) NOT NULL,
    s3_key      VARCHAR(1024),
    s3_bucket   VARCHAR(128),
    file_size   BIGINT,
    mime_type   VARCHAR(128),
    version     INT DEFAULT 1,
    status      VARCHAR(32) DEFAULT 'uploaded',  -- uploaded, reviewed, approved, rejected
    uploaded_by BIGINT NOT NULL REFERENCES users(id),
    reviewed_by BIGINT REFERENCES users(id),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Asset Center (images & videos) =====================
CREATE TABLE IF NOT EXISTS assets (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    model_id    BIGINT REFERENCES models(id) ON DELETE SET NULL,
    name        VARCHAR(256) NOT NULL,
    asset_type  VARCHAR(32) NOT NULL,  -- image, video
    s3_key      VARCHAR(1024) NOT NULL,
    s3_bucket   VARCHAR(128) NOT NULL,
    file_size   BIGINT,
    mime_type   VARCHAR(128),
    thumbnail_url VARCHAR(512),
    uploaded_by BIGINT NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===================== Indexes =====================
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role_id);
CREATE INDEX IF NOT EXISTS idx_models_category ON models(category_id);
CREATE INDEX IF NOT EXISTS idx_models_project ON models(project_id);
CREATE INDEX IF NOT EXISTS idx_models_status ON models(status);
CREATE INDEX IF NOT EXISTS idx_model_files_model ON model_files(model_id);
CREATE INDEX IF NOT EXISTS idx_model_files_type ON model_files(file_type);
CREATE INDEX IF NOT EXISTS idx_upload_records_user ON upload_records(user_id);
CREATE INDEX IF NOT EXISTS idx_download_records_user ON download_records(user_id);
CREATE INDEX IF NOT EXISTS idx_download_records_model ON download_records(model_id);
CREATE INDEX IF NOT EXISTS idx_mod_records_model ON modification_records(model_id);
CREATE INDEX IF NOT EXISTS idx_project_members_project ON project_members(project_id);
CREATE INDEX IF NOT EXISTS idx_project_members_user ON project_members(user_id);
CREATE INDEX IF NOT EXISTS idx_project_tasks_project ON project_tasks(project_id);
CREATE INDEX IF NOT EXISTS idx_project_files_project ON project_files(project_id);

-- ===================== Seed Data =====================

-- System Roles
INSERT INTO roles (name, code, description, is_system) VALUES
    ('系统管理员', 'admin', '拥有所有权限', TRUE),
    ('组长', 'leader', '项目组长，可审批注册申请', TRUE),
    ('组员', 'member', '普通组员', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Permissions
INSERT INTO permissions (name, code, module, description) VALUES
    ('用户管理', 'user:manage', 'user', '管理所有用户'),
    ('角色管理', 'role:manage', 'role', '管理角色和权限'),
    ('模型管理', 'model:manage', 'model', '管理模型分类和模型'),
    ('模型上传', 'model:upload', 'model', '上传模型'),
    ('模型下载', 'model:download', 'model', '下载模型'),
    ('模型删除', 'model:delete', 'model', '删除模型'),
    ('项目管理', 'project:manage', 'project', '管理项目'),
    ('注册审批', 'registration:approve', 'auth', '审批注册申请'),
    ('邀请码管理', 'invitation:manage', 'auth', '生成和管理邀请码'),
    ('系统设置', 'system:config', 'system', '系统配置')
ON CONFLICT (code) DO NOTHING;

-- Grant all permissions to admin role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.code = 'admin'
ON CONFLICT DO NOTHING;

-- Grant leader permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.code = 'leader' AND p.code IN ('model:upload','model:download','project:manage','registration:approve','invitation:manage')
ON CONFLICT DO NOTHING;

-- Grant member permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p 
WHERE r.code = 'member' AND p.code IN ('model:upload','model:download')
ON CONFLICT DO NOTHING;

-- Default model categories
-- NOTE: admin user is created by DataInitializer with properly encoded password from .env
INSERT INTO model_categories (name, code, description, sort_order) VALUES
    ('角色模型', 'character', '人物、角色类3D模型', 1),
    ('场景模型', 'scene', '场景、环境类3D模型', 2),
    ('道具模型', 'prop', '道具、物品类3D模型', 3),
    ('建筑模型', 'architecture', '建筑、结构类3D模型', 4),
    ('植被模型', 'vegetation', '树木、植物类3D模型', 5),
    ('特效模型', 'effect', '特效、粒子类模型', 6)
ON CONFLICT DO NOTHING;

-- Default project categories
INSERT INTO project_categories (name, code, description, sort_order) VALUES
    ('研发项目', 'r_and_d', '产品研发类项目', 1),
    ('外包项目', 'outsourcing', '外包接单类项目', 2),
    ('内部项目', 'internal', '公司内部项目', 3),
    ('测试项目', 'testing', '测试验证类项目', 4)
ON CONFLICT DO NOTHING;
