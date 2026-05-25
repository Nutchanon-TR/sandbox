ALTER TABLE chat_app.ai_context
    ADD COLUMN IF NOT EXISTS appearance_reference_url TEXT,
    ADD COLUMN IF NOT EXISTS appearance_reference_object_path TEXT;
