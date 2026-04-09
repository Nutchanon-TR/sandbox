# ChatApp — เอกสารอธิบายฟีเจอร์และ Spec

> อัปเดตล่าสุดจาก source code จริง

## ภาพรวม

ChatApp คือ Microservice สำหรับแชทกับ AI Assistant ภายในโปรเจกต์ Sandbox
ผู้ใช้ Login ผ่าน Supabase OAuth แล้วสนทนากับ AI ที่ขับเคลื่อนด้วย Groq API (Llama 3)
ระบบบันทึกประวัติแชทไว้ใน PostgreSQL (Supabase), ใช้ Redis สำหรับ Cache, และมีระบบ Vector Search ด้วย HuggingFace Embedding

---

## สถาปัตยกรรม

```
Browser (Next.js)
      │
      ▼
Nginx Gateway (port 80)
      │  /v1/api/chat-app/*
      ▼
chat-service (Spring Boot :8080)
      │
      ├── PostgreSQL (Supabase)    ← เก็บข้อมูลถาวร
      ├── Redis                    ← Cache ประวัติแชท
      ├── Groq API                 ← AI model (Llama 3.3-70b)
      └── HuggingFace API          ← Embedding (multilingual-e5-small)
```

---

## Tech Stack

| Layer | เครื่องมือ | หน้าที่ |
|-------|-----------|----|
| **Frontend** | Next.js 15 (App Router) | UI หน้าแชท |
| **State / Auth** | Supabase SSR (`@supabase/ssr`) | Session management |
| **Backend** | Spring Boot 3.2.5 (Java 21) | Business logic, API |
| **AI Model** | Groq API — `llama-3.3-70b-versatile` | สร้างคำตอบจาก AI |
| **AI Framework** | Spring AI 1.0.0-M1 | ต่อกับ Groq ผ่าน OpenAI-compatible API |
| **Resilience** | Resilience4j | Circuit Breaker + Retry สำหรับ Groq API |
| **Embedding** | HuggingFace API — `multilingual-e5-small` | สร้าง vector จากข้อความ (RAG) |
| **Database** | Supabase PostgreSQL | เก็บ users, rooms, messages, profiles, embeddings |
| **Cache** | Redis 7.2 | Cache ประวัติแชทแยกตาม key |
| **Gateway** | Nginx | Reverse proxy, routing |
| **ORM** | Spring Data JPA (Hibernate) | Object-Relational Mapping |

---

## Database Schema

### `chat` schema

```
chat.users
├── id            BIGSERIAL PK
├── supabase_uid  UUID UNIQUE NOT NULL  → เชื่อม Supabase Auth
├── display_name  VARCHAR(100) NOT NULL
└── created_at    TIMESTAMPTZ

chat.rooms
├── id         BIGSERIAL PK
├── name       VARCHAR(100)             → ใช้เฉพาะ group room
├── is_group   BOOLEAN DEFAULT false
├── created_by BIGINT                   → user_id ที่สร้าง
├── ai_model   VARCHAR(100)             → ใช้เฉพาะ AI room
└── created_at TIMESTAMPTZ

chat.room_members  (junction table)
├── room_id   FK → rooms.id
└── user_id   FK → users.id

chat.messages
├── id         BIGSERIAL PK
├── room_id    FK → rooms.id   NOT NULL
├── sender_id  FK → users.id   NULLABLE  → NULL เมื่อ AI ส่ง
├── is_ai      BOOLEAN DEFAULT false NOT NULL
├── content    TEXT NOT NULL
└── created_at TIMESTAMPTZ

chat.ai_context  (1:1 กับ rooms)
├── id          BIGSERIAL PK
├── room_id     FK → rooms.id  UNIQUE NOT NULL
├── ai_name     VARCHAR(100) DEFAULT 'AI Assistant'
└── system_text TEXT NOT NULL

chat.message_embeddings
├── id         BIGSERIAL PK
├── message_id FK → messages.id  UNIQUE
├── embedding  vector(384)        → multilingual-e5-small มี 384 มิติ
└── created_at TIMESTAMPTZ
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

> **หมายเหตุ:** `chat.users` และ `users.profiles` เป็น 2 table คนละ schema
> - `chat.users` — identity ของผู้ใช้ในระบบแชท (สร้างอัตโนมัติตอน resolve)
> - `users.profiles` — ข้อมูล profile ของผู้ใช้ทั่วไป (username, avatar ฯลฯ)

---

## Controllers และ Endpoints

### `UserController` — `POST /v1/api/chat-app/user/resolve`

แปลง Supabase UUID → chat user ID (สร้างใหม่ถ้ายังไม่มี / sync display_name ถ้าเปลี่ยน)

**Request:**
```json
{
  "supabaseUid": "abc-123-uuid",
  "displayName": "John Doe"
}
```

**Response:**
```json
{
  "userId": 5
}
```

**Logic (UserResolutionService):**
1. แปลง `supabaseUid` string → `UUID`
2. หา `chat.users` ด้วย `supabase_uid`
   - ถ้าเจอ → sync `display_name` ถ้าเปลี่ยน แล้ว return
   - ถ้าไม่เจอ → สร้าง User ใหม่
3. คืน `userId` เพียงอย่างเดียว (**ไม่มี roomId** ใน response แล้ว)

> **เปลี่ยนจากเดิม:** response เดิมคืนทั้ง `userId` และ `roomId` แต่ตอนนี้คืนแค่ `userId` — room ต้องดึงผ่าน `RoomController` แยกต่างหาก

---

### `RoomController`

#### `GET /v1/api/chat-app/room/list/{userId}`

ดึงรายการ room ทั้งหมดของ user

**Response:**
```json
[
  {
    "id": 3,
    "name": "AI Assistant",
    "isGroup": false,
    "aiModel": "llama-3.3-70b-versatile",
    "createdAt": "2025-01-01T10:00:00Z"
  },
  {
    "id": 7,
    "name": "Study Group",
    "isGroup": true,
    "aiModel": null,
    "createdAt": "2025-01-02T12:00:00Z"
  }
]
```

> AI room จะใช้ `ai_name` จาก `ai_context` เป็น display name แทน `rooms.name`

#### `POST /v1/api/chat-app/room/create/{userId}`

สร้าง room ใหม่ — รองรับ 2 ประเภท

**Request (AI Room):**
```json
{
  "name": "My AI Assistant",
  "isGroup": false,
  "aiModel": "llama-3.3-70b-versatile",
  "systemPrompt": "You are a helpful assistant..."
}
```

**Request (Group Room):**
```json
{
  "name": "Study Group",
  "isGroup": true,
  "memberIds": [2, 3, 4]
}
```

**Logic (RoomService):**
- **AI Room:** สร้าง Room (`is_group=false`) → เพิ่ม creator เป็น member → สร้าง `ai_context`
- **Group Room:** สร้าง Room (`is_group=true`) → เพิ่ม creator + memberIds ทั้งหมด

---

### `ChatController`

#### `GET /v1/api/chat-app/message/history/{roomId}`

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

**Cache:** `@Cacheable(value = "chatHistory", key = "#roomId + '_' + #beforeId + '_' + #limit")`  
**Evict:** `@CacheEvict(value = "chatHistory", allEntries = true)` เมื่อมีข้อความใหม่

#### `POST /v1/api/chat-app/message`

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
2. บันทึก user message ลง `chat.messages` (`is_ai=false`)
3. เรียก `EmbeddingService.embedAndSave()` เพื่อสร้าง vector ของข้อความ
4. ดึง `ai_context` ของ room
5. เรียก `callAiAndSaveReply()` ส่ง prompt ให้ Groq

**Logic (callAiAndSaveReply):**
1. เพิ่ม System Message จาก `aiContext.systemText`
2. **Vector Search:** หา 5 messages ที่คล้ายกันมากที่สุดใน room → เพิ่มเป็น context message ที่ 2
3. ดึง **20 messages ล่าสุด** (CONTEXT_LIMIT = 20) → reverse เป็น asc → เพิ่มเป็น history
4. ส่ง prompt ทั้งหมดให้ `GroqAiClient.chat()`
5. บันทึก AI reply ลง `chat.messages` (`is_ai=true`, `sender_id=null`)
6. Embed AI reply ด้วย `EmbeddingService.embedAndSave()`
7. Evict Redis cache (ผ่าน `@CacheEvict` บน method นี้)

---

### `ProfileController`

#### `GET /v1/api/chat-app/profile/{supabaseUid}`

ดึง profile ของผู้ใช้

**Response:**
```json
{
  "supabaseUid": "abc-123-uuid",
  "username": "john_doe",
  "email": "john@example.com",
  "role": "USER",
  "avatarUrl": "https://..."
}
```

#### `POST /v1/api/chat-app/profile`

สร้างหรืออัปเดต profile

**Request:**
```json
{
  "supabaseUid": "abc-123-uuid",
  "username": "john_doe",
  "email": "john@example.com"
}
```

---

## AI Flow (Prompt Building)

```
┌──────────────────────────────────────────────────────────┐
│  Prompt ที่ส่งให้ Groq                                     │
├──────────────────────────────────────────────────────────┤
│ [System]  → system_text จาก ai_context                   │
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
  → save message → embedAndSave("passage: " + content) → INSERT chat.message_embeddings
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

| Repository | Method หลัก |
|------------|------------|
| `UserRepository` | `findBySupabaseUid(UUID)` |
| `RoomRepository` | `findAllByUserId(userId)`, `findPrivateRoomBetween(userId, aiUserId)` |
| `MessageRepository` | `findTopNByRoomId(roomId, pageable)`, `findLatestByRoomId(roomId, pageable)`, `findByRoomIdBeforeId(roomId, beforeId, pageable)` |
| `AiContextRepository` | `findByRoomId(roomId)` |
| `ProfileRepository` | `findById(supabaseUid)` |

---

## Environment Variables ที่จำเป็น

| ตัวแปร | ใช้ที่ | คำอธิบาย |
|--------|--------|---------|
| `GROK_API_KEY` | chat-service | API Key สำหรับ Groq |
| `HUGGINGFACE_API_KEY` | chat-service | API Key สำหรับ HuggingFace Embedding |
| `SUPABASE_DB_USERNAME` | chat-service | Username ต่อ Supabase DB |
| `SUPABASE_DB_PASSWORD` | chat-service | Password ต่อ Supabase DB |
| `REDIS_PASSWORD` | chat-service | Password ของ Redis |
| `SUPABASE_SERVICE_ROLE_KEY` | chat-service | Service role key (สำหรับ Supabase operations) |
| `NEXT_PUBLIC_SUPABASE_URL` | frontend | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | frontend | Supabase anon key |
| `NEXT_PUBLIC_API_URL` | frontend | Base URL ของ Nginx gateway |

---

## สิ่งที่ยังไม่ได้ทำ (Backlog)

| รายการ | สถานะ |
|--------|-------|
| **CORS production domain** | `WebConfig` ยังใส่แค่ localhost — ต้อง add production domain |
| **pgvector activation บน Supabase** | SQL พร้อมแล้วใน `database/03_pgvector_schema.sql` — รอรันบน Supabase จริง |
| **Frontend ยังใช้ roomId จาก resolve** | `page.tsx` ยังส่ง `roomId` ใน request แต่ backend resolve ไม่คืน roomId แล้ว — ต้อง update FE ให้ดึง room list แยก |
| ~~**จำกัด history ก่อนส่ง Prompt**~~ | ✅ เสร็จแล้ว — ส่งแค่ 20 message ล่าสุด |
| ~~**Vector Search (RAG)**~~ | ✅ Implement แล้วผ่าน HuggingFace API + pgvector |
