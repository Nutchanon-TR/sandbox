ALTER TABLE chat_app.ai_context
    ADD COLUMN IF NOT EXISTS persona_feed_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS persona_feed_min_interval_hours INTEGER NOT NULL DEFAULT 8,
    ADD COLUMN IF NOT EXISTS persona_feed_max_interval_hours INTEGER NOT NULL DEFAULT 24,
    ADD COLUMN IF NOT EXISTS persona_feed_window_start TIME,
    ADD COLUMN IF NOT EXISTS persona_feed_window_end TIME,
    ADD COLUMN IF NOT EXISTS persona_feed_timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Bangkok',
    ADD COLUMN IF NOT EXISTS persona_feed_next_post_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS persona_feed_last_post_at TIMESTAMPTZ;

ALTER TABLE chat_app.ai_context
    DROP CONSTRAINT IF EXISTS chk_ai_context_persona_feed_interval;

ALTER TABLE chat_app.ai_context
    ADD CONSTRAINT chk_ai_context_persona_feed_interval
    CHECK (
        persona_feed_min_interval_hours > 0
        AND persona_feed_max_interval_hours >= persona_feed_min_interval_hours
    );

CREATE TABLE IF NOT EXISTS chat_app.persona_feed_posts (
    id BIGSERIAL PRIMARY KEY,
    ai_context_id BIGINT NOT NULL REFERENCES chat_app.ai_context(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    image_urls TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_persona_feed_posts_created
    ON chat_app.persona_feed_posts (id DESC);

CREATE INDEX IF NOT EXISTS idx_persona_feed_posts_ai_created
    ON chat_app.persona_feed_posts (ai_context_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_ai_context_persona_feed_due
    ON chat_app.ai_context (persona_feed_next_post_at)
    WHERE persona_feed_enabled = true AND visibility = 'public';
