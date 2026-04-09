-- สร้าง Schema สำหรับ Chat (PostgreSQL / Supabase)
-- อ้างอิงจาก JPA Entities ใน backend/chatapp

-- =============================================
-- Users Schema (User Service — single source of truth)
-- =============================================
CREATE SCHEMA IF NOT EXISTS users;

CREATE TABLE users.profiles (
    supabase_uid UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',   -- GUEST / USER / ADMIN
    avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_profiles_email ON users.profiles(email);
CREATE INDEX idx_profiles_role ON users.profiles(role);

-- =============================================
-- Chat Schema (Chat Service)
-- =============================================
CREATE SCHEMA IF NOT EXISTS chat;

-- 1. สร้างตาราง chat.users (slim — เก็บแค่ตัวเชื่อมกับ users.profiles)
CREATE TABLE chat.users (
    id BIGSERIAL PRIMARY KEY,
    supabase_uid UUID NOT NULL UNIQUE,    -- ตัวเชื่อมกับ users.profiles
    display_name VARCHAR(100) NOT NULL,   -- cache ชื่อจาก users.profiles
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. สร้างตาราง rooms
CREATE TABLE chat.rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100),
    is_group BOOLEAN DEFAULT FALSE,
    created_by BIGINT REFERENCES chat.users(id) ON DELETE SET NULL,
    ai_model VARCHAR(100),  -- ชื่อ AI model เช่น 'llama3-8b-8192', NULL = ห้อง group ปกติ
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. สร้างตาราง room_members (junction table — human users only)
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
    sender_id BIGINT REFERENCES chat.users(id) ON DELETE SET NULL,  -- NULL เมื่อ AI ตอบ
    is_ai BOOLEAN NOT NULL DEFAULT FALSE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. สร้างตาราง ai_context (system prompt + AI info ต่อห้อง)
CREATE TABLE chat.ai_context (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL UNIQUE REFERENCES chat.rooms(id) ON DELETE CASCADE,
    ai_name VARCHAR(100) NOT NULL DEFAULT 'AI Assistant',
    system_text TEXT NOT NULL
);

-- 6. สร้าง Indexes
CREATE INDEX idx_messages_room_id ON chat.messages(room_id);
CREATE INDEX idx_messages_created_at ON chat.messages(created_at);
CREATE INDEX idx_room_members_user_id ON chat.room_members(user_id);
CREATE INDEX idx_users_supabase_uid ON chat.users(supabase_uid);

-- 7. Insert ข้อมูลตัวอย่าง
INSERT INTO users.profiles (supabase_uid, username, email, role) VALUES
('00000000-0000-0000-0000-000000000001', 'alice', 'alice@example.com', 'USER'),
('00000000-0000-0000-0000-000000000002', 'bob', 'bob@example.com', 'USER'),
('00000000-0000-0000-0000-000000000003', 'charlie', 'charlie@example.com', 'USER');

INSERT INTO chat.users (supabase_uid, display_name) VALUES
('00000000-0000-0000-0000-000000000001', 'alice'),
('00000000-0000-0000-0000-000000000002', 'bob'),
('00000000-0000-0000-0000-000000000003', 'charlie');

-- alice มี 2 AI rooms (คนละ model) + 1 group room
INSERT INTO chat.rooms (name, is_group, created_by, ai_model) VALUES
('Alice x Llama', FALSE, 1, 'llama3-8b-8192'),
('Alice x Gemma', FALSE, 1, 'gemma2-9b-it'),
('Project Team',  TRUE,  1, NULL);

INSERT INTO chat.room_members (room_id, user_id) VALUES
(1, 1),            -- Alice ใน room 1 (AI room — ไม่ต้องมี AI เป็น member)
(2, 1),            -- Alice ใน room 2
(3, 1), (3, 2), (3, 3);  -- Alice, Bob, Charlie ใน group

INSERT INTO chat.messages (room_id, sender_id, is_ai, content) VALUES
(1, 1, FALSE, 'Hello Llama!'),
(1, NULL, TRUE, 'Hi! How can I help you today?'),
(3, 1, FALSE, 'Welcome to the team everyone!');

-- AI context ต่อห้อง
INSERT INTO chat.ai_context (room_id, ai_name, system_text) VALUES
(1, 'Llama Assistant', 'You are a helpful AI assistant. Respond concisely and helpfully.'),
(2, 'Gemma Creative', 'You are a creative writing assistant. Help with storytelling and ideas.');
