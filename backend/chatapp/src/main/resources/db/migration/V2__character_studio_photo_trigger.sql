ALTER TABLE chat_app.ai_context
    ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT REFERENCES chat_app.users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) NOT NULL DEFAULT 'private',
    ADD COLUMN IF NOT EXISTS style_examples JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS image_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS image_trigger_rules JSONB NOT NULL DEFAULT '{
      "photoKeywords": ["ถ่ายรูป", "ส่งรูป", "ขอดูรูป", "ถ่ายมาให้ดู"],
      "activityKeywords": ["ทำอะไรอยู่", "ตอนนี้ทำไร", "อยู่ไหน", "ทำอะไรตอนนี้"]
    }'::jsonb,
    ADD COLUMN IF NOT EXISTS image_prompt_template TEXT,
    ADD COLUMN IF NOT EXISTS fine_tune_status VARCHAR(32) NOT NULL DEFAULT 'not_started',
    ADD COLUMN IF NOT EXISTS fine_tuned_model_id TEXT;

UPDATE chat_app.ai_context ai
SET created_by_user_id = rooms.user_id
FROM chat_app.room_members room_members
JOIN chat_app.rooms rooms ON rooms.id = room_members.room_id
WHERE room_members.ai_id = ai.id
  AND ai.created_by_user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_ai_context_created_by_user_id
    ON chat_app.ai_context(created_by_user_id);

CREATE INDEX IF NOT EXISTS idx_ai_context_created_by_visibility
    ON chat_app.ai_context(created_by_user_id, visibility);

CREATE TABLE IF NOT EXISTS chat_app.chat_attachments (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES chat_app.chats(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    url TEXT NOT NULL,
    mime_type VARCHAR(100),
    prompt TEXT,
    provider VARCHAR(50),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_attachments_chat_id
    ON chat_app.chat_attachments(chat_id);

CREATE TABLE IF NOT EXISTS chat_app.training_examples (
    id BIGSERIAL PRIMARY KEY,
    ai_context_id BIGINT NOT NULL REFERENCES chat_app.ai_context(id) ON DELETE CASCADE,
    created_by_user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    source_chat_id BIGINT REFERENCES chat_app.chats(id) ON DELETE SET NULL,
    messages_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_training_examples_ai_context_id
    ON chat_app.training_examples(ai_context_id);

CREATE INDEX IF NOT EXISTS idx_training_examples_created_by_user_id
    ON chat_app.training_examples(created_by_user_id);

CREATE TABLE IF NOT EXISTS chat_app.training_runs (
    id BIGSERIAL PRIMARY KEY,
    ai_context_id BIGINT NOT NULL REFERENCES chat_app.ai_context(id) ON DELETE CASCADE,
    created_by_user_id BIGINT NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL DEFAULT 'manual',
    base_model TEXT,
    dataset_version TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'planned',
    external_job_id TEXT,
    fine_tuned_model_id TEXT,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_training_runs_ai_context_id
    ON chat_app.training_runs(ai_context_id);

CREATE INDEX IF NOT EXISTS idx_training_runs_created_by_user_id
    ON chat_app.training_runs(created_by_user_id);
