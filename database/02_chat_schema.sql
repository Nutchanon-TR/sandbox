-- สร้าง Schema สำหรับ Chat
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'chat')
BEGIN
    EXEC('CREATE SCHEMA chat');
END
GO

-- 1. สร้างตาราง users
CREATE TABLE chat.users (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    username NVARCHAR(50) NOT NULL UNIQUE,
    email NVARCHAR(100) NOT NULL UNIQUE,
    password_hash NVARCHAR(255) NOT NULL,
    created_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET()
);

-- 2. สร้างตาราง rooms
CREATE TABLE chat.rooms (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    name NVARCHAR(100),
    is_group BIT DEFAULT 0, -- 0 = false, 1 = true
    created_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET()
);

-- 3. สร้างตาราง room_members
CREATE TABLE chat.room_members (
    room_id BIGINT FOREIGN KEY REFERENCES chat.rooms(id) ON DELETE CASCADE,
    user_id BIGINT FOREIGN KEY REFERENCES chat.users(id) ON DELETE CASCADE,
    joined_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET(),
    PRIMARY KEY (room_id, user_id)
);

-- 4. สร้างตาราง messages
CREATE TABLE chat.messages (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    room_id BIGINT NOT NULL FOREIGN KEY REFERENCES chat.rooms(id) ON DELETE CASCADE,
    sender_id BIGINT NOT NULL FOREIGN KEY REFERENCES chat.users(id) ON DELETE CASCADE,
    content NVARCHAR(MAX) NOT NULL,
    created_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET()
);

-- 5. สร้าง Indexes (Syntax เหมือนเดิม)
CREATE INDEX idx_messages_room_id ON chat.messages(room_id);
CREATE INDEX idx_messages_created_at ON chat.messages(created_at);
CREATE INDEX idx_room_members_user_id ON chat.room_members(user_id);
GO

-- 6. Insert ข้อมูล (ใช้ SET IDENTITY_INSERT เพื่อระบุ ID เองในครั้งแรก)
SET IDENTITY_INSERT chat.users ON;
INSERT INTO chat.users (id, username, email, password_hash) VALUES 
(1, 'alice', 'alice@example.com', 'fake_hash_1'),
(2, 'bob', 'bob@example.com', 'fake_hash_2'),
(3, 'charlie', 'charlie@example.com', 'fake_hash_3');
SET IDENTITY_INSERT chat.users OFF;

SET IDENTITY_INSERT chat.rooms ON;
INSERT INTO chat.rooms (id, name, is_group) VALUES 
(1, 'Alice & Bob', 0),
(2, 'Project Team', 1);
SET IDENTITY_INSERT chat.rooms OFF;

-- room_members ไม่มี IDENTITY เพราะใช้ Composite Key จาก Table อื่น
INSERT INTO chat.room_members (room_id, user_id) VALUES 
(1, 1), (1, 2), (2, 1), (2, 2), (2, 3);

SET IDENTITY_INSERT chat.messages ON;
INSERT INTO chat.messages (id, room_id, sender_id, content) VALUES 
(1, 1, 1, 'Hello Bob!'),
(2, 1, 2, 'Hi Alice, how are you?'),
(3, 2, 3, 'Welcome to the team everyone!');
SET IDENTITY_INSERT chat.messages OFF;