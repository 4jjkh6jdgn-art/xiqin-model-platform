CREATE TABLE IF NOT EXISTS model_projects (
    id BIGSERIAL PRIMARY KEY,
    model_id BIGINT NOT NULL REFERENCES models(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uk_model_project UNIQUE (model_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_model_projects_model ON model_projects(model_id);
CREATE INDEX IF NOT EXISTS idx_model_projects_project ON model_projects(project_id);

CREATE TABLE IF NOT EXISTS model_category_links (
    id BIGSERIAL PRIMARY KEY,
    model_id BIGINT NOT NULL REFERENCES models(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES model_categories(id) ON DELETE CASCADE,
    CONSTRAINT uk_model_category_link UNIQUE (model_id, category_id)
);

CREATE INDEX IF NOT EXISTS idx_model_category_links_model ON model_category_links(model_id);
CREATE INDEX IF NOT EXISTS idx_model_category_links_category ON model_category_links(category_id);

INSERT INTO model_projects(model_id, project_id)
SELECT id, project_id FROM models WHERE project_id IS NOT NULL
ON CONFLICT (model_id, project_id) DO NOTHING;

INSERT INTO model_category_links(model_id, category_id)
SELECT id, category_id FROM models WHERE category_id IS NOT NULL
ON CONFLICT (model_id, category_id) DO NOTHING;
