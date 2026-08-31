-- Project workspace: version snapshots, folders, file activities and admin inbox.

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS current_version INT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS project_versions (
    id            BIGSERIAL PRIMARY KEY,
    project_id    BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    version_num   INT NOT NULL,
    name          VARCHAR(256) NOT NULL,
    description   TEXT,
    status        VARCHAR(32) NOT NULL,
    priority      SMALLINT DEFAULT 1,
    change_log    VARCHAR(512),
    created_by    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, version_num)
);

INSERT INTO project_versions (project_id, version_num, name, description, status, priority, change_log, created_by, created_at)
SELECT p.id, 1, p.name, p.description, p.status, p.priority, '初始版本', p.created_by, p.created_at
FROM projects p
ON CONFLICT (project_id, version_num) DO NOTHING;

CREATE TABLE IF NOT EXISTS project_folders (
    id          BIGSERIAL PRIMARY KEY,
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    parent_id   BIGINT REFERENCES project_folders(id) ON DELETE CASCADE,
    name        VARCHAR(256) NOT NULL,
    created_by  BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_project_folder_path
    ON project_folders(project_id, COALESCE(parent_id, 0), LOWER(name));

ALTER TABLE project_files
    ADD COLUMN IF NOT EXISTS folder_id BIGINT REFERENCES project_folders(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS relative_path VARCHAR(1024);
ALTER TABLE project_files ALTER COLUMN status SET DEFAULT 'available';
UPDATE project_files SET status = 'available' WHERE status IS NULL OR status <> 'available';

CREATE TABLE IF NOT EXISTS project_file_activities (
    id          BIGSERIAL PRIMARY KEY,
    file_id     BIGINT NOT NULL REFERENCES project_files(id) ON DELETE CASCADE,
    project_id  BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    action      VARCHAR(32) NOT NULL,
    user_id     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    user_name   VARCHAR(128),
    detail      VARCHAR(512),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO project_file_activities (file_id, project_id, action, user_id, user_name, detail, created_at)
SELECT pf.id, pf.project_id, 'upload', pf.uploaded_by, u.username, '上传文件', pf.created_at
FROM project_files pf
LEFT JOIN users u ON u.id = pf.uploaded_by
WHERE NOT EXISTS (
    SELECT 1 FROM project_file_activities a WHERE a.file_id = pf.id AND a.action = 'upload'
);

CREATE TABLE IF NOT EXISTS system_messages (
    id              BIGSERIAL PRIMARY KEY,
    message_type    VARCHAR(32) NOT NULL,
    severity        VARCHAR(16) NOT NULL DEFAULT 'info',
    title           VARCHAR(256) NOT NULL,
    content         TEXT,
    source_type     VARCHAR(32),
    source_id       BIGINT,
    project_id      BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    created_by      BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_by_name VARCHAR(128),
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_project_versions_project ON project_versions(project_id, version_num DESC);
CREATE INDEX IF NOT EXISTS idx_project_folders_project ON project_folders(project_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_project_files_folder ON project_files(project_id, folder_id);
CREATE INDEX IF NOT EXISTS idx_project_file_activities_file ON project_file_activities(file_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_messages_unread ON system_messages(is_read, created_at DESC);
