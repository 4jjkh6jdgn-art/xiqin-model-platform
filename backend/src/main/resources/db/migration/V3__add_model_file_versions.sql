ALTER TABLE model_files
    ADD COLUMN IF NOT EXISTS version_num INT;

UPDATE model_files
SET version_num = 1
WHERE version_num IS NULL;

ALTER TABLE model_files
    ALTER COLUMN version_num SET DEFAULT 1;

ALTER TABLE model_files
    ALTER COLUMN version_num SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_model_files_model_version
    ON model_files(model_id, version_num);

INSERT INTO model_versions (model_id, version_num, change_log, created_by, created_at)
SELECT m.id, 1, '初始上传', m.created_by, m.created_at
FROM models m
WHERE m.created_by IS NOT NULL
ON CONFLICT (model_id, version_num) DO NOTHING;
