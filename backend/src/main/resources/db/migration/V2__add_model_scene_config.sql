-- Add persisted 3D preview scene settings introduced after the initial schema.
-- IF NOT EXISTS keeps this safe for both fresh and already-initialized databases.
ALTER TABLE models
    ADD COLUMN IF NOT EXISTS camera_view TEXT,
    ADD COLUMN IF NOT EXISTS lighting TEXT;
