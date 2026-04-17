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
├── system_text TEXT NOT NULL
└── avatar_url  TEXT NULLABLE  → URL รูป avatar ของ AI (เก็บใน Supabase Storage)

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
> - `chat.users` — identity ของผู้ใช้ในระบบแชท (write โดย user-service, ChatApp อ่าน read-only)
> - `users.profiles` — ข้อมูล profile ของผู้ใช้ทั่วไป (จัดการโดย user-service)

---

## Controllers และ Endpoints

> **หมายเหตุ:** User management (UserController, ProfileController, UserResolutionService, ProfileService)
> ถูกย้ายไปที่ **user-service** (`backend/user/`) แล้ว — ดู endpoint ใหม่ที่ `/v1/api/user/`
> ChatApp ยังคง read-only access ถึง `chat.users` ผ่าน JPA สำหรับดึงข้อมูล sender ในแชท

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
    "aiModel": "llama-3.3-70b-versatile",
    "aiAvatarUrl": "https://<supabase>/storage/v1/object/public/images/ai-avatars/kealith.png",
    "createdAt": "2025-01-01T10:00:00Z"
  },
  {
    "id": 7,
    "name": "Study Group",
    "isGroup": true,
    "aiModel": null,
    "aiAvatarUrl": null,
    "createdAt": "2025-01-02T12:00:00Z"
  }
]
```

> - AI room จะใช้ `ai_name` จาก `ai_context` เป็น display name แทน `rooms.name`
> - `aiAvatarUrl` ดึงจาก `ai_context.avatar_url` — ถ้าเป็น group room หรือยังไม่ได้ตั้งรูปจะเป็น `null`

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
- **AI Room:** สร้าง Room (`is_group=false`) → เพิ่ม creator เป็น member → สร้าง `ai_context` (พร้อม `avatar_url` ถ้ามี)
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

> **ProfileController** ถูกย้ายไปที่ user-service แล้ว — endpoint ใหม่:
> - `GET /v1/api/user/profile/{supabaseUid}`
> - `POST /v1/api/user/profile`

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
| `UserRepository` (read-only) | `findBySupabaseUid(UUID)`, `findById(Long)` — อ่านข้อมูล sender (write ย้ายไป user-service) |
| `RoomRepository` | `findAllByUserId(userId)`, `findPrivateRoomBetween(userId, aiUserId)` |
| `MessageRepository` | `findTopNByRoomId(roomId, pageable)`, `findLatestByRoomId(roomId, pageable)`, `findByRoomIdBeforeId(roomId, beforeId, pageable)` |
| `AiContextRepository` | `findByRoomId(roomId)` |

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

## Supabase Storage

รูป avatar ของ AI เก็บใน Supabase Storage bucket `images` (public)

| ไฟล์ | Path ใน Storage | ใช้โดย |
|------|----------------|--------|
| AI avatar (Kealith) | `images/ai-avatars/kealith.png` | `ai_context.avatar_url` → Frontend แสดงรูป AI ในหน้าแชท |

**Public URL format:** `https://<project>.supabase.co/storage/v1/object/public/images/ai-avatars/<name>.png`

> Frontend ดึง `aiAvatarUrl` จาก response ของ `GET /room/list/{userId}` — ถ้า `null` จะ fallback เป็น `/ai_avatar.png` (static)

---

## WebSocket — Real-time Chat Plan

### ปัญหาปัจจุบัน

ตอนนี้ ChatApp ใช้ **HTTP request-response** ทั้งหมด:
- ส่งข้อความ → `POST /v1/api/chat-app/message` → รอ AI reply → return
- ดึงประวัติ → `GET /v1/api/chat-app/message/history/{roomId}`

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

`GET /v1/api/chat-app/message/history/{roomId}` ยังคงใช้ HTTP GET เดิม
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
  ├── 1. GET /message/history/{roomId} → load ประวัติเก่า (HTTP)
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

- HTTP endpoints (`POST /message`, `GET /history`) คงไว้ — ทำงานได้ทั้ง 2 mode
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
