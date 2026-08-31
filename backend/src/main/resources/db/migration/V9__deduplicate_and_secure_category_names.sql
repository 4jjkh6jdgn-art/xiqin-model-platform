-- Consolidate categories accidentally inserted by the legacy raw-SQL startup loop.
-- Keep the earliest ID and migrate every relation before deleting duplicate rows.

CREATE TEMP TABLE tmp_model_category_merge ON COMMIT DROP AS
SELECT id AS old_id,
       MIN(id) OVER (PARTITION BY LOWER(BTRIM(name))) AS keep_id
FROM model_categories;

INSERT INTO model_category_links(model_id, category_id)
SELECT DISTINCT link.model_id, merge.keep_id
FROM model_category_links link
JOIN tmp_model_category_merge merge ON merge.old_id = link.category_id
WHERE merge.old_id <> merge.keep_id
ON CONFLICT (model_id, category_id) DO NOTHING;

DELETE FROM model_category_links link
USING tmp_model_category_merge merge
WHERE link.category_id = merge.old_id
  AND merge.old_id <> merge.keep_id;

UPDATE models model
SET category_id = merge.keep_id
FROM tmp_model_category_merge merge
WHERE model.category_id = merge.old_id
  AND merge.old_id <> merge.keep_id;

UPDATE model_categories category
SET parent_id = merge.keep_id
FROM tmp_model_category_merge merge
WHERE category.parent_id = merge.old_id
  AND merge.old_id <> merge.keep_id;

DELETE FROM model_categories category
USING tmp_model_category_merge merge
WHERE category.id = merge.old_id
  AND merge.old_id <> merge.keep_id;

UPDATE model_categories SET name = BTRIM(name) WHERE name <> BTRIM(name);

CREATE UNIQUE INDEX IF NOT EXISTS ux_model_categories_normalized_name
    ON model_categories (LOWER(BTRIM(name)));

ALTER TABLE model_categories
    DROP CONSTRAINT IF EXISTS ck_model_categories_name_not_blank;
ALTER TABLE model_categories
    ADD CONSTRAINT ck_model_categories_name_not_blank CHECK (BTRIM(name) <> '');

CREATE TEMP TABLE tmp_project_category_merge ON COMMIT DROP AS
SELECT id AS old_id,
       MIN(id) OVER (PARTITION BY LOWER(BTRIM(name))) AS keep_id
FROM project_categories;

UPDATE projects project
SET category_id = merge.keep_id
FROM tmp_project_category_merge merge
WHERE project.category_id = merge.old_id
  AND merge.old_id <> merge.keep_id;

UPDATE project_categories category
SET parent_id = merge.keep_id
FROM tmp_project_category_merge merge
WHERE category.parent_id = merge.old_id
  AND merge.old_id <> merge.keep_id;

DELETE FROM project_categories category
USING tmp_project_category_merge merge
WHERE category.id = merge.old_id
  AND merge.old_id <> merge.keep_id;

UPDATE project_categories SET name = BTRIM(name) WHERE name <> BTRIM(name);

CREATE UNIQUE INDEX IF NOT EXISTS ux_project_categories_normalized_name
    ON project_categories (LOWER(BTRIM(name)));

ALTER TABLE project_categories
    DROP CONSTRAINT IF EXISTS ck_project_categories_name_not_blank;
ALTER TABLE project_categories
    ADD CONSTRAINT ck_project_categories_name_not_blank CHECK (BTRIM(name) <> '');
