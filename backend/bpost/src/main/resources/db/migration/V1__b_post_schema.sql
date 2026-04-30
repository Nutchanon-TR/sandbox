-- B-Post schema (Facebook-like social system)
-- Tables: posts, comments, post_likes, friendships, conversations, messages, notifications
-- Identity FK -> chat_app.users(id)

CREATE SCHEMA IF NOT EXISTS b_post;

-- ==========================
-- presence (additive on chat_app.users)
-- ==========================
ALTER TABLE chat_app.users
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

-- ==========================
-- posts
-- ==========================
CREATE TABLE IF NOT EXISTS b_post.posts (
    id          BIGSERIAL PRIMARY KEY,
    author_id   BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    content     TEXT        NOT NULL DEFAULT '',
    image_urls  TEXT[]      NOT NULL DEFAULT '{}',
    visibility  VARCHAR(16) NOT NULL DEFAULT 'FRIENDS',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    edited_at   TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_posts_author_created
    ON b_post.posts (author_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_posts_created
    ON b_post.posts (created_at DESC) WHERE deleted_at IS NULL;

-- ==========================
-- comments
-- ==========================
CREATE TABLE IF NOT EXISTS b_post.comments (
    id          BIGSERIAL PRIMARY KEY,
    post_id     BIGINT      NOT NULL REFERENCES b_post.posts(id) ON DELETE CASCADE,
    author_id   BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    edited_at   TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_comments_post_created
    ON b_post.comments (post_id, created_at ASC);

-- ==========================
-- post_likes
-- ==========================
CREATE TABLE IF NOT EXISTS b_post.post_likes (
    post_id    BIGINT      NOT NULL REFERENCES b_post.posts(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_post_likes_user ON b_post.post_likes (user_id);

-- ==========================
-- friendships (bilateral, normalized pair)
-- ==========================
CREATE TABLE IF NOT EXISTS b_post.friendships (
    id            BIGSERIAL PRIMARY KEY,
    requester_id  BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    addressee_id  BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at  TIMESTAMPTZ,
    CHECK (requester_id <> addressee_id)
);
-- prevent duplicate pairs regardless of direction
CREATE UNIQUE INDEX IF NOT EXISTS uq_friendships_pair
    ON b_post.friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));
CREATE INDEX IF NOT EXISTS idx_friendships_addressee_status
    ON b_post.friendships (addressee_id, status);
CREATE INDEX IF NOT EXISTS idx_friendships_requester_status
    ON b_post.friendships (requester_id, status);

-- ==========================
-- conversations (1:1 only, normalized pair)
-- ==========================
CREATE TABLE IF NOT EXISTS b_post.conversations (
    id              BIGSERIAL PRIMARY KEY,
    user_a_id       BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    user_b_id       BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (user_a_id < user_b_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_conversations_pair
    ON b_post.conversations (user_a_id, user_b_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user_a_last
    ON b_post.conversations (user_a_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_conversations_user_b_last
    ON b_post.conversations (user_b_id, last_message_at DESC);

-- ==========================
-- messages
-- ==========================
CREATE TABLE IF NOT EXISTS b_post.messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES b_post.conversations(id) ON DELETE CASCADE,
    sender_id       BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    content         TEXT,
    image_url       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ,
    CHECK (content IS NOT NULL OR image_url IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_messages_conv_id
    ON b_post.messages (conversation_id, id DESC);

-- ==========================
-- notifications
-- ==========================
CREATE TABLE IF NOT EXISTS b_post.notifications (
    id            BIGSERIAL PRIMARY KEY,
    recipient_id  BIGINT      NOT NULL REFERENCES chat_app.users(id) ON DELETE CASCADE,
    type          VARCHAR(32) NOT NULL,
    actor_id      BIGINT REFERENCES chat_app.users(id) ON DELETE SET NULL,
    target_id     BIGINT,
    target_kind   VARCHAR(32),
    message       TEXT,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created
    ON b_post.notifications (recipient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_unread
    ON b_post.notifications (recipient_id) WHERE read_at IS NULL;
