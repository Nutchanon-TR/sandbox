# ChatApp — เอกสารอธิบายฟีเจอร์และ Spec

> อัปเดตล่าสุดจาก source code จริง

## ภาพรวม

ChatApp คือ Microservice สำหรับแชทกับ AI Companion ภายในโปรเจกต์ Sandbox
ผู้ใช้ Login ผ่าน Supabase OAuth แล้วสนทนากับ AI ที่ขับเคลื่อนด้วย Groq API (Llama 3)
ระบบบันทึกประวัติแชทไว้ใน PostgreSQL (Supabase) และมีระบบ Vector Search ด้วย HuggingFace Embedding

นอกจาก 1-on-1 chat ยังมี **social layer** (like / friend / comment) ระหว่าง user กับ AI แต่ละตัว
และ **discovery feed** (`/blog/list`, `/blog/detail/{aiId}`) สำหรับเลือก/เปิดดูโปรไฟล์ AI

---

## สถาปัตยกรรม

```
Browser (Next.js)
      │
      ▼
Nginx Gateway (port 80)
      │  /v1/api/chat-app/*  → auth_request → oauth2-proxy (JWT)
      ▼
chat-service (Spring Boot :8080)
      │
      ├── PostgreSQL (Supabase via Supavisor pooler :6543)  ← เก็บข้อมูลถาวร
      ├── Groq API                                          ← AI model (Llama 3.3-70b)
      └── HuggingFace API                                   ← Embedding (multilingual-e5-small)
```

---

## Tech Stack

| Layer | เครื่องมือ | หน้าที่ |
|-------|-----------|----|
| **Frontend** | Next.js 15 (App Router) | UI หน้าแชท |
| **State / Auth** | Supabase SSR (`@supabase/ssr`) | Session management |
| **Backend** | Spring Boot 3.2.5 (Java 17 compile / Java 21 runtime) | Business logic, API |
| **AI Model** | Groq API — `llama-3.3-70b-versatile` | สร้างคำตอบจาก AI |
| **AI Framework** | Spring AI 1.0.0-M1 | ต่อกับ Groq ผ่าน OpenAI-compatible API |
| **Resilience** | Resilience4j | Circuit Breaker + Retry สำหรับ Groq API |
| **Embedding** | HuggingFace API — `multilingual-e5-small` | สร้าง vector จากข้อความ (RAG) |
| **Database** | Supabase PostgreSQL (via Supavisor pooler) | เก็บ users, rooms, chats, chat_embeddings, social tables |
| **Gateway** | Nginx + oauth2-proxy | Reverse proxy, JWT validation |
| **ORM** | Spring Data JPA (Hibernate) + JdbcTemplate | Object-Relational Mapping + native SQL สำหรับ room_members |

---

## Database Schema

### `chat_app` schema

```
chat_app.users
├── id            BIGSERIAL PK
├── supabase_uid  UUID UNIQUE NOT NULL  → เชื่อม Supabase Auth
├── display_name  VARCHAR(100) NOT NULL
└── created_at    TIMESTAMPTZ

chat_app.rooms
├── id         BIGSERIAL PK
├── name       VARCHAR(100) NULLABLE    → NULL สำหรับ AI room (ใช้ ai_name แทน)
├── is_group   BOOLEAN DEFAULT false
├── user_id    FK → users.id  NOT NULL  → เจ้าของห้อง (1 user ต่อ 1 room)
└── created_at TIMESTAMPTZ

chat_app.room_members  (junction: room ↔ AI — รองรับหลาย AI ต่อ 1 room)
├── room_id  FK → rooms.id      NOT NULL
└── ai_id    FK → ai_context.id NOT NULL

chat_app.chats  (เดิม messages — rename แล้ว)
├── id         BIGSERIAL PK
├── room_id    FK → rooms.id   NOT NULL
├── sender_id  FK → users.id   NULLABLE  → NULL เมื่อ AI ส่ง
├── is_ai      BOOLEAN DEFAULT false NOT NULL
├── content    TEXT NOT NULL
└── created_at TIMESTAMPTZ

chat_app.ai_context  (personality ของ AI — standalone)
├── id          BIGSERIAL PK
├── ai_name     VARCHAR(100) DEFAULT 'AI Assistant'
├── avatar_url  TEXT NULLABLE     → avatar (path prefix: `images/ai-avatars/`)
├── poster_url  TEXT NULLABLE     → character poster รูปที่ 2 (path prefix: `images/ai_poster/`)
├── role        TEXT NULLABLE     → ส่วนประกอบ system prompt
├── "character" TEXT NULLABLE     → (quoted — keyword ของ Postgres)
├── biography   TEXT NULLABLE
└── rule        TEXT NULLABLE

# system_text เดิมถูกแยกเป็น 4 ส่วน → BE ต่อกลับเป็น system prompt ผ่าน AiContext.buildSystemPrompt()
# link room ↔ ai_context: ผ่าน room_members.ai_id (M:N — 1 room อาจมีหลาย AI)

chat_app.chat_embeddings  (เดิม message_embeddings — rename แล้ว)
├── id         BIGSERIAL PK
├── chat_id    FK → chats.id  UNIQUE
├── embedding  vector(384)        → multilingual-e5-small มี 384 มิติ
└── created_at TIMESTAMPTZ

chat_app.ai_likes  (social — M:N user ↔ AI)
├── id          BIGSERIAL PK
├── user_id     FK → users.id      NOT NULL
├── ai_id       FK → ai_context.id NOT NULL
├── created_at  TIMESTAMPTZ
└── UNIQUE (user_id, ai_id)

chat_app.ai_friends  (social — M:N user ↔ AI)
├── id          BIGSERIAL PK
├── user_id     FK → users.id      NOT NULL
├── ai_id       FK → ai_context.id NOT NULL
├── created_at  TIMESTAMPTZ
└── UNIQUE (user_id, ai_id)

chat_app.comments  (social — user comment บน AI profile)
├── id          BIGSERIAL PK
├── user_id     FK → users.id      NOT NULL
├── ai_id       FK → ai_context.id NOT NULL
├── content     TEXT NOT NULL
├── created_at  TIMESTAMPTZ
└── INDEX idx_comments_ai_id (ai_id)
```

### `users` schema

```
users.profiles
├── supabase_uid  UUID PK             → ตรงกับ Supabase Auth id
├── username      VARCHAR(50) NOT NULL
├── email         VARCHAR(100) UNIQUE NOT NULL
├── role          VARCHAR(20) DEFAULT 'USER'
├── avatar_url    TEXT
└── created_at    TIMESTAMPTZ
```

> **หมายเหตุ:** `chat_app.users` และ `users.profiles` เป็น 2 table คนละ schema
> - `chat_app.users` — identity ของผู้ใช้ในระบบแชท (write โดย user-service, ChatApp อ่าน read-only)
> - `users.profiles` — ข้อมูล profile ของผู้ใช้ทั่วไป (จัดการโดย user-service)

---

## Controllers และ Endpoints

> **หมายเหตุ:** User management (UserController, ProfileController, UserResolutionService, ProfileService)
> ถูกย้ายไปที่ **user-service** (`backend/user/`) แล้ว — ดู endpoint ใหม่ที่ `/v1/api/user/`
> ChatApp ยังคง read-only access ถึง `chat_app.users` ผ่าน JPA สำหรับดึงข้อมูล sender ในแชท

### `RoomController`

#### `GET /v1/api/chat-app/room/list/{userId}`

ดึงรายการ room ทั้งหมดของ user

**Response:**
```json
[
  {
    "id": 3,
    "name": "Kealith",
    "isGroup": false,
    "aiAvatarUrl": "https://<supabase>/storage/v1/object/public/images/ai-avatars/kealith.png",
    "createdAt": "2025-01-01T10:00:00Z"
  }
]
```

> - AI room จะใช้ `ai_name` จาก `ai_context` เป็น display name แทน `rooms.name`
> - `aiAvatarUrl` ดึงจาก `ai_context.avatar_url` — ถ้ายังไม่ได้ตั้งรูปจะเป็น `null`

#### `POST /v1/api/chat-app/room/create/{userId}`

สร้าง AI room ใหม่

**Request:**
```json
{
  "name": "My AI Assistant",
  "isGroup": false,
  "systemPrompt": "You are a helpful assistant..."
}
```

**Logic (RoomService):**
- สร้าง Room (`is_group=false`, `user_id=userId`) → สร้าง `ai_context` (โดย `systemPrompt` จะถูกเก็บใน `role` เป็นค่าเริ่มต้น) → INSERT `room_members` (ai_id)
- **หมายเหตุ:** `user_id` อยู่บน `rooms` แล้ว (1 room = 1 user เจ้าของ) — `room_members` ใช้เฉพาะผูก AI (รองรับหลาย AI ต่อ room ในอนาคต)

---

### `ChatController`

#### `GET /v1/api/chat-app/chat/history/{roomId}`

ดึงประวัติแชท Paginated (cursor-based) เรียง asc (เก่า → ใหม่)

**Query Parameters:**

| Parameter | Required | Default | คำอธิบาย |
|-----------|----------|---------|---------|
| `limit` | ไม่บังคับ | `20` | จำนวน message ที่ต้องการ |
| `beforeId` | ไม่บังคับ | (ไม่มี) | ดึง messages ที่ id < beforeId (load more ขึ้นบน) |

**Response:**
```json
{
  "messages": [
    {
      "id": 1,
      "roomId": 3,
      "senderId": 5,
      "senderName": "John Doe",
      "isAi": false,
      "content": "สวัสดี",
      "createdAt": "2025-01-01T10:00:00Z"
    },
    {
      "id": 2,
      "roomId": 3,
      "senderId": null,
      "senderName": "AI Assistant",
      "isAi": true,
      "content": "สวัสดีครับ มีอะไรให้ช่วยไหม",
      "createdAt": "2025-01-01T10:00:05Z"
    }
  ],
  "hasMore": false
}
```

> **ต่างจากเดิม:** field เปลี่ยนจาก `senderUsername` / `senderRole` เป็น `senderName` / `isAi: boolean`
> AI message จะมี `senderId: null` และ `isAi: true`

> **หมายเหตุ:** ปัจจุบันยังไม่ได้เปิด cache — ทุก request อ่าน DB ตรงๆ (ถ้าจะเพิ่มต้อง add `spring-boot-starter-data-redis` + `@EnableCaching` + @Cacheable/@CacheEvict ก่อน)

#### `POST /v1/api/chat-app/chat`

ส่งข้อความและรับคำตอบจาก AI

**Request:**
```json
{
  "roomId": 3,
  "senderId": 5,
  "message": "ช่วยสรุป Spring Boot ให้หน่อย"
}
```

**Response:**
```json
{
  "reply": "Spring Boot คือ framework ที่ช่วยสร้าง Java application ได้รวดเร็ว..."
}
```

**Logic (ChatService.getAiResponse):**
1. Validate roomId + senderId มีอยู่จริง
2. บันทึก user message ลง `chat_app.chats` (`is_ai=false`)
3. เรียก `EmbeddingService.embedAndSave()` เพื่อสร้าง vector ของข้อความ
4. ดึง `ai_id` จาก `room_members` แล้วโหลด `ai_context`
5. เรียก `callAiAndSaveReply()` ส่ง prompt ให้ Groq

**Logic (callAiAndSaveReply):**
1. เพิ่ม System Message จาก `aiContext.buildSystemPrompt()` — concat `role` / `character` / `biography` / `rule` ด้วย label `[ROLE]`, `[CHARACTER]`, `[BIOGRAPHY]`, `[RULE]` (skip field ที่ null/blank)
2. **Vector Search:** หา 5 messages ที่คล้ายกันมากที่สุดใน room (`VECTOR_SEARCH_LIMIT=5`) → เพิ่มเป็น System message ที่ 2 ในรูปแบบ "Relevant past messages:\n- {sender}: {content}"
3. ดึง **20 messages ล่าสุด** (`DEFAULT_PAGE_SIZE=20`) → reverse เป็น asc → เพิ่มเป็น history (UserMessage/AssistantMessage)
4. ส่ง prompt ทั้งหมดให้ `GroqAiClient.chat()`
5. บันทึก AI reply ลง `chat_app.chats` (`is_ai=true`, `sender_id=null`)
6. Embed AI reply ด้วย `EmbeddingService.embedAndSave()`

---

### `SyncHubController` (Social + Discovery)

Folder structure:
```
controllers/SyncHubController/
├── CardController/          ← per-AI action (like / friend / comment / detail)
│   ├── LikeController.java
│   ├── FriendController.java
│   ├── CommentController.java
│   └── DetailController.java
└── BlogController/          ← feed-level (list / sorting ในอนาคต)
    └── ListController.java
```

**จุดประสงค์:**
- **Card** = โลจิคเล็กๆที่ผูกกับ AI ตัวเดียว (like, friend, comment, detail)
- **Blog** = โลจิคกว้างๆระดับ collection (list, sorting/ranking algorithm ในอนาคต)

#### Like endpoints

| Method | Path | คำอธิบาย |
|--------|------|---------|
| `POST` | `/v1/api/chat-app/ai/{aiId}/like/{userId}` | user กด like AI ตัวนี้ (idempotent ผ่าน `UNIQUE(user_id, ai_id)`) |
| `DELETE` | `/v1/api/chat-app/ai/{aiId}/like/{userId}` | ยกเลิก like |

#### Friend endpoints

| Method | Path | คำอธิบาย |
|--------|------|---------|
| `POST` | `/v1/api/chat-app/ai/{aiId}/friend/{userId}` | add friend กับ AI |
| `DELETE` | `/v1/api/chat-app/ai/{aiId}/friend/{userId}` | unfriend |
| `GET` | `/v1/api/chat-app/user/{userId}/friends` | list AI friends ของ user |

#### Comment endpoints

| Method | Path | คำอธิบาย |
|--------|------|---------|
| `POST` | `/v1/api/chat-app/ai/{aiId}/comments` | เพิ่ม comment (body: `{userId, content}`) |
| `GET` | `/v1/api/chat-app/ai/{aiId}/comments` | ดึง comments ของ AI (เรียง created_at DESC + join display_name) |
| `DELETE` | `/v1/api/chat-app/comments/{commentId}` | ลบ comment |

#### Detail endpoint

| Method | Path | คำอธิบาย |
|--------|------|---------|
| `GET` | `/v1/api/chat-app/blog/detail/{aiId}` | ดึง AI detail ทั้งหมด (ai_context fields + like/friend/comment counts) |

#### Blog list endpoint

| Method | Path | คำอธิบาย |
|--------|------|---------|
| `GET` | `/v1/api/chat-app/blog/list` | ดึงรายการ AI ทั้งหมด + user เจ้าของ + counts (ใช้เป็น discovery feed) |

**ListController response (AiContextUserDto):**
```json
[
  {
    "aiId": 1,
    "aiName": "Kealith",
    "avatarUrl": "...",
    "posterUrl": "...",
    "userId": 5,
    "userDisplayName": "John",
    "roomId": 3,
    "likeCount": 12,
    "friendCount": 3,
    "commentCount": 8
  }
]
```

> **หมายเหตุ:** endpoint เดิม `/blog/inquiry` ถูกยกเลิก — ใช้ `/blog/list` แทน

---

> **ProfileController** ถูกย้ายไปที่ user-service แล้ว — endpoint ใหม่:
> - `GET /v1/api/user/profile/{supabaseUid}`
> - `POST /v1/api/user/profile`

---

## AI Flow (Prompt Building)

```
┌──────────────────────────────────────────────────────────┐
│  Prompt ที่ส่งให้ Groq                                     │
├──────────────────────────────────────────────────────────┤
│ [System]  → buildSystemPrompt() — concat 4 sections:     │
│             [ROLE]      {role}                           │
│             [CHARACTER] {character}                      │
│             [BIOGRAPHY] {biography}                      │
│             [RULE]      {rule}                           │
│             (skip field ที่ null/blank)                   │
│ [System]  → "Relevant past messages:\n- ..."             │  ← Vector Search (top 5)
│ [User]    → "สวัสดี"          ↑ 20 messages ล่าสุด       │
│ [AI]      → "สวัสดีครับ..."                               │
│ [User]    → "ช่วยสรุป Spring Boot หน่อย"  (ข้อความใหม่)  │
└──────────────────────────────────────────────────────────┘
```

---

## Embedding & Vector Search (RAG)

ใช้ **HuggingFace Inference API** — model `intfloat/multilingual-e5-small` (384 มิติ)

| Operation | ใช้เมื่อ | Prefix |
|-----------|---------|--------|
| `embedAndSave()` | หลังบันทึก message ใหม่ (ทั้ง user และ AI) | `"passage: "` |
| `searchSimilarMessages()` | ก่อน build prompt — หา context | `"query: "` |

**Flow:**
```
user ส่งข้อความ
  → save message → embedAndSave("passage: " + content) → INSERT chat_app.chat_embeddings
  → searchSimilarMessages("query: " + userQuery, roomId, limit=5)
     → cosine distance ผ่าน pgvector (<=> operator)
     → คืน message_id list
  → ดึง message จาก id list → เพิ่มเป็น [System] context ใน prompt
```

> **Graceful degradation:** ถ้า HuggingFace API ล้ม → log warning แต่ยังคง reply ได้ (ไม่มี vector context)

---

## Resilience (Circuit Breaker)

ใช้ **Resilience4j** ห่อการเรียก Groq API ใน `GroqAiClient`

| ค่า | Setting |
|-----|---------|
| Sliding window | 10 requests |
| Minimum calls | 5 |
| เปิด circuit เมื่อ fail | ≥ 50% |
| รอก่อน half-open | 30 วินาที |
| Permitted calls in half-open | 3 |
| Slow call rate threshold | ≥ 80% |
| Slow call duration | > 10 วินาที |
| Retry สูงสุด | 3 ครั้ง |
| รอระหว่าง retry | 2 วินาที |

**Fallback:** `"ขออภัย ระบบ AI ไม่สามารถตอบได้ชั่วคราว กรุณาลองใหม่อีกครั้ง"`

---

## Repositories

### MessageRepository (core chat)

| Repository | Method หลัก |
|------------|------------|
| `UserRepository` (read-only) | `findBySupabaseUid(UUID)`, `findById(Long)` — อ่านข้อมูล sender (write ย้ายไป user-service) |
| `RoomRepository` | `findAllByUserId(userId)` — JPA query บน `rooms.user_id` |
| `RoomMemberRepository` | `findAiIdByRoomId(roomId)`, `addRoomAi(roomId, aiId)` — native SQL |
| `ChatRepository` | `findTopNByRoomId(roomId, pageable)`, `findLatestByRoomId(roomId, pageable)`, `findByRoomIdBeforeId(roomId, beforeId, pageable)` |
| `AiContextRepository` | `findById(Long)` — JpaRepository default |
| `ChatEmbeddingRepository` | native SQL สำหรับ pgvector cosine search (ผ่าน `EmbeddingService`) |

### SyncHubRepository (social + discovery)

| Repository | Type | Method หลัก |
|------------|------|------------|
| `CardRepository/LikeRepository` | JpaRepository | `existsByUserIdAndAiId`, `countByAiId`, `deleteByUserIdAndAiId` |
| `CardRepository/FriendRepository` | JpaRepository | เหมือน Like + `findByUserId` |
| `CardRepository/CommentRepository` | JpaRepository | `findByAiIdOrderByCreatedAtDesc`, `countByAiId` |
| `CardRepository/DetailRepository` | JdbcTemplate | ai_context ตัวเดียว + counts (likes/friends/comments) |
| `BlogRepository/ListRepository` | JdbcTemplate | join ai_context + room_members + rooms + users + 3 counts → list AI ทั้งหมด |

---

## Environment Variables ที่จำเป็น

| ตัวแปร | ใช้ที่ | คำอธิบาย |
|--------|--------|---------|
| `GROK_API_KEY` | chat-service | API Key สำหรับ Groq |
| `HUGGINGFACE_API_KEY` | chat-service | API Key สำหรับ HuggingFace Embedding |
| `SUPABASE_DB_USERNAME` | chat-service | Username ต่อ Supabase DB (format `postgres.<project-ref>` สำหรับ pooler) |
| `SUPABASE_DB_PASSWORD` | chat-service | Password ต่อ Supabase DB |
| `SPRING_DATASOURCE_URL` | chat-service (ACA) | Override JDBC URL ให้ชี้ Supavisor pooler `:6543` — ดู [POOLER.md](POOLER.md) |
| `SUPABASE_SERVICE_ROLE_KEY` | chat-service | Service role key (สำหรับ Supabase operations) |
| `NEXT_PUBLIC_SUPABASE_URL` | frontend | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | frontend | Supabase anon key |
| `NEXT_PUBLIC_API_URL` | frontend | Base URL ของ Nginx gateway (ปล่อยว่างไว้ → ใช้ relative URL) |

---

## Supabase Storage

รูปของ AI เก็บใน Supabase Storage bucket `images` (public) — แบ่ง path prefix ตามประเภท

| ประเภท | Path prefix | เก็บใน | ใช้โดย |
|--------|-------------|--------|--------|
| AI avatar | `images/ai-avatars/` | `ai_context.avatar_url` | แสดงเป็นรูป AI ในหน้าแชท (เหมือน profile picture) |
| AI character poster | `images/ai_poster/` | `ai_context.poster_url` | รูปที่ 2 — character poster ของ AI (สำหรับหน้า detail / card) |

**Public URL format:** `https://<project>.supabase.co/storage/v1/object/public/images/<prefix>/<name>.png`

> - Frontend ดึง `aiAvatarUrl` จาก response ของ `GET /room/list/{userId}` — ถ้า `null` fallback เป็น `/ai_avatar.png` (static)
> - Upload endpoint สำหรับ `poster_url` ยังไม่รวมใน phase นี้ — admin/FE upload ตรงไป Supabase แล้วเก็บ URL ลง column

---

## WebSocket — Real-time Chat Plan

### ปัญหาปัจจุบัน

ตอนนี้ ChatApp ใช้ **HTTP request-response** ทั้งหมด:
- ส่งข้อความ → `POST /v1/api/chat-app/chat` → รอ AI reply → return
- ดึงประวัติ → `GET /v1/api/chat-app/chat/history/{roomId}`

**ข้อจำกัด:**
1. ไม่มี real-time — ผู้ใช้ต้อง poll หรือ refresh เพื่อดู messages ใหม่
2. AI reply ใช้เวลา 3-15 วินาที — user ต้องรอ blocking request
3. Group chat (อนาคต) ไม่สามารถแจ้ง members คนอื่นได้ real-time

### สถาปัตยกรรม WebSocket (เป้าหมาย)

```
Browser (Next.js)
      │
      ▼ WebSocket upgrade
Nginx Gateway (port 80)
      │  /ws/chat
      ▼
chat-service (Spring Boot :8080)
      │  STOMP over WebSocket
      ├── /topic/room/{roomId}     ← subscribe: รับ messages real-time
      ├── /app/chat.send           ← send: ส่งข้อความ
      └── /user/queue/errors       ← personal error channel
```

### Tech Stack เพิ่มเติม

| Layer | เครื่องมือ | หน้าที่ |
|-------|-----------|---------|
| **Backend** | Spring WebSocket + STOMP | WebSocket endpoint + message broker |
| **Frontend** | `@stomp/stompjs` + `sockjs-client` | STOMP client สำหรับ browser |
| **Gateway** | Nginx `proxy_pass` with upgrade | WebSocket proxy |

### Backend Implementation Plan

#### 1. Dependencies (`pom.xml`)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

#### 2. WebSocket Configuration
**ไฟล์ใหม่:** `config/WebSocketConfig.java`

- Enable STOMP messaging: `@EnableWebSocketMessageBroker`
- Register STOMP endpoint: `/ws/chat` (with SockJS fallback)
- Configure message broker:
  - `/topic` — broadcast ไปทุกคนใน room (public channel)
  - `/user/queue` — ส่งถึง user คนเดียว (errors, typing indicator)
- Application destination prefix: `/app`
- Allowed origins: ใช้ `app.cors.allowedOrigins` เดิม

#### 3. STOMP Controller
**ไฟล์ใหม่:** `controllers/ChatWebSocketController.java`

```
@MessageMapping("/chat.send")
public void sendMessage(ChatRequestDto request, SimpMessageHeaderAccessor headerAccessor):
  1. Validate roomId + senderId
  2. Save user message → DB
  3. Broadcast user message → /topic/room/{roomId}
  4. Async: call AI → save reply → broadcast AI reply → /topic/room/{roomId}
```

**ข้อสำคัญ:** AI reply ทำ async (`@Async`) เพราะใช้เวลานาน — ไม่ block WebSocket thread

#### 4. Flow ใหม่ (WebSocket)

```
User ส่งข้อความผ่าน STOMP (/app/chat.send)
  │
  ├── 1. Save user message ลง DB
  ├── 2. Broadcast user message ไป /topic/room/{roomId}
  │      → ทุก subscriber ใน room เห็นข้อความทันที
  │
  └── 3. Async: เรียก AI (Groq API)
         ├── Save AI reply ลง DB
         ├── Embed message (HuggingFace)
         └── Broadcast AI reply ไป /topic/room/{roomId}
               → ทุก subscriber เห็น AI reply ทันที
```

#### 5. Authentication บน WebSocket

- ใช้ Supabase JWT token ใน `connect` frame header
- สร้าง `ChannelInterceptor` เพื่อ validate JWT ตอน CONNECT
- Extract user info จาก token → set เป็น `Principal` ใน session
- Reject connection ถ้า token invalid

#### 6. History Endpoint (คงไว้)

`GET /v1/api/chat-app/chat/history/{roomId}` ยังคงใช้ HTTP GET เดิม
— ใช้สำหรับ load ประวัติเก่าตอนเปิดหน้า (ไม่ต้องใช้ WebSocket)

### Frontend Implementation Plan

#### 1. Dependencies
```bash
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client
```

#### 2. STOMP Hook / Store
**ไฟล์ใหม่:** `stores/chatStore.ts` (Zustand)

- `connected: boolean`
- `messages: Map<roomId, ChatMessage[]>`
- `connect(token: string)` — สร้าง STOMP client
- `subscribe(roomId: number)` — subscribe `/topic/room/{roomId}`
- `sendMessage(roomId, senderId, message)` — publish ไป `/app/chat.send`
- `disconnect()` — cleanup

#### 3. Flow ใหม่ (Frontend)

```
เปิดหน้า Chat
  │
  ├── 1. GET /chat/history/{roomId} → load ประวัติเก่า (HTTP)
  ├── 2. Connect WebSocket → /ws/chat (STOMP + JWT token)
  └── 3. Subscribe /topic/room/{roomId}
         │
         ├── รับ user message → append to message list
         └── รับ AI reply → append to message list

ส่งข้อความ
  └── Publish ไป /app/chat.send (STOMP)
      → ไม่ต้องรอ response (fire-and-forget)
      → message จะกลับมาผ่าน subscription
```

### Nginx Configuration

เพิ่มใน `gateway/nginx.conf.template`:

```nginx
# WebSocket endpoint
location /ws/ {
    proxy_pass http://chat-service/ws/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $proxy_host;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_read_timeout 86400s;  # Keep WebSocket alive for 24h
}
```

### ลำดับการทำงาน (Phased)

| Phase | งาน | ความเสี่ยง |
|-------|------|-----------|
| **WS-1** | เพิ่ม `spring-boot-starter-websocket` + `WebSocketConfig` | ต่ำ |
| **WS-2** | สร้าง `ChatWebSocketController` + async AI reply | กลาง |
| **WS-3** | เพิ่ม JWT auth interceptor สำหรับ STOMP CONNECT | กลาง |
| **WS-4** | อัปเดต Nginx config สำหรับ WebSocket proxy | ต่ำ |
| **WS-5** | Frontend: สร้าง `chatStore.ts` + STOMP client | กลาง |
| **WS-6** | Frontend: refactor chat page ใช้ WebSocket แทน HTTP POST | สูง |
| **WS-7** | เพิ่ม typing indicator + online status (optional) | ต่ำ |

### Backward Compatibility

- HTTP endpoints (`POST /chat`, `GET /chat/history`) คงไว้ — ทำงานได้ทั้ง 2 mode
- Frontend สามารถ fallback เป็น HTTP ถ้า WebSocket connect ไม่ได้
- Group chat ready: เมื่อมี multiple users ใน room ทุกคน subscribe ได้

### Docker / ACA Considerations

- **docker-compose:** ไม่ต้องแก้ (chat-service port 8080 เหมือนเดิม)
- **Azure Container Apps:** ต้องตั้ง `--transport websocket` ใน ingress config
- **Redis (อนาคต):** ถ้า scale chat-service เป็น multiple replicas ต้องใช้ Redis Pub/Sub เป็น external message broker แทน in-memory (SimpleBroker)

---

## สิ่งที่ยังไม่ได้ทำ (Backlog)

| รายการ | สถานะ |
|--------|-------|
| ~~**CORS production domain**~~ | ✅ ย้าย CORS handling ไปจัดการที่ nginx gateway (`/v1/api/user/`) — ดู [INFRA.md §2.1](INFRA.md#21-cors-handling-ที่-gateway-v1apiuser) |
| **pgvector activation บน Supabase** | SQL พร้อมแล้วใน `database/03_pgvector_schema.sql` — รอรันบน Supabase จริง |
| ~~**Frontend ยังใช้ roomId จาก resolve**~~ | ✅ แก้แล้ว — FE ใช้ room list แยก + local state `selectedRoomId` เป็น source of truth |
| ~~**จำกัด history ก่อนส่ง Prompt**~~ | ✅ เสร็จแล้ว — ส่งแค่ 20 message ล่าสุด |
| ~~**Vector Search (RAG)**~~ | ✅ Implement แล้วผ่าน HuggingFace API + pgvector |
| ~~**AI avatar hardcode**~~ | ✅ แก้แล้ว — รูป AI เก็บใน Supabase Storage, map ผ่าน `ai_context.avatar_url` |
| ~~**Schema rename (chat → chat_app)**~~ | ✅ ย้าย schema + rename `messages` → `chats`, `message_embeddings` → `chat_embeddings` |
| ~~**Split system_text**~~ | ✅ แตกเป็น `role` / `character` / `biography` / `rule` — BE concat ผ่าน `buildSystemPrompt()` |
| ~~**Social layer (like/friend/comment)**~~ | ✅ M:N tables + Card sub-package endpoints ครบ |
| ~~**Blog list + detail**~~ | ✅ `GET /blog/list` + `GET /blog/detail/{aiId}` (แทน `/blog/inquiry` เดิม) |
| ~~**URL cleanup /message → /chat**~~ | ✅ Rename แล้วทั้ง BE + FE (`ApiSandbox.ts`) |
| **Upload poster endpoint** | ยังไม่ได้ทำ — phase 2 (รอ add Supabase storage client ใน BE) |
| **FE UI สำหรับ like/friend/comment/detail/blog** | ยังไม่ได้ทำ — รอ design UI |
