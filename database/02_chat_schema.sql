-- สร้าง Schema สำหรับ Chat (PostgreSQL / Supabase)
-- อ้างอิงจาก JPA Entities ใน backend/chatapp
CREATE SCHEMA IF NOT EXISTS chat;

-- 1. สร้างตาราง users
CREATE TABLE chat.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    supabase_uid UUID UNIQUE,          -- เพิ่มเพื่อเชื่อม Supabase Auth (Migration: add_supabase_uid_to_chat_users)
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. สร้างตาราง rooms
CREATE TABLE chat.rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100),
    is_group BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. สร้างตาราง room_members (junction table)
CREATE TABLE chat.room_members (
    room_id BIGINT NOT NULL REFERENCES chat.rooms(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES chat.users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (room_id, user_id)
);

-- 4. สร้างตาราง messages
CREATE TABLE chat.messages (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES chat.rooms(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL REFERENCES chat.users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. สร้างตาราง ai_context (system prompt สำหรับ AI bot)
CREATE TABLE chat.ai_context (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES chat.users(id),
    system_text TEXT NOT NULL
);

-- 6. สร้าง Indexes
CREATE INDEX idx_messages_room_id ON chat.messages(room_id);
CREATE INDEX idx_messages_created_at ON chat.messages(created_at);
CREATE INDEX idx_room_members_user_id ON chat.room_members(user_id);
CREATE INDEX idx_users_supabase_uid ON chat.users(supabase_uid);

-- 7. Insert ข้อมูลตัวอย่าง
INSERT INTO chat.users (username, email, password_hash, role) VALUES
('alice', 'alice@example.com', 'fake_hash_1', 'USER'),
('bob', 'bob@example.com', 'fake_hash_2', 'USER'),
('charlie', 'charlie@example.com', 'fake_hash_3', 'USER'),
('ai_assistant', 'ai@sandbox.local', 'no-password', 'AI');

INSERT INTO chat.rooms (name, is_group) VALUES
('Alice & Bob', FALSE),
('Project Team', TRUE);

INSERT INTO chat.room_members (room_id, user_id) VALUES
(1, 1), (1, 2), (2, 1), (2, 2), (2, 3);

INSERT INTO chat.messages (room_id, sender_id, content) VALUES
(1, 1, 'Hello Bob!'),
(1, 2, 'Hi Alice, how are you?'),
(2, 3, 'Welcome to the team everyone!');

-- Insert AI context (system prompt)
INSERT INTO chat.ai_context (user_id, system_text) VALUES
(4, 'You are a helpful AI assistant. Respond concisely and helpfully.');
