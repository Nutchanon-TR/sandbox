ALTER TABLE chat_app.ai_context
    ADD COLUMN IF NOT EXISTS system_context TEXT,
    ADD COLUMN IF NOT EXISTS personality_traits JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS speech_style TEXT,
    ADD COLUMN IF NOT EXISTS relationship_context TEXT,
    ADD COLUMN IF NOT EXISTS memory_notes TEXT,
    ADD COLUMN IF NOT EXISTS response_boundaries TEXT;
