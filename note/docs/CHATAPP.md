# ChatApp — เอกสารอธิบายฟีเจอร์และ Spec

## ภาพรวม

ChatApp คือ Microservice สำหรับแชทกับ AI Assistant ภายในโปรเจกต์ Sandbox
ผู้ใช้ Login ผ่าน Supabase OAuth แล้วสนทนากับ AI ที่ขับเคลื่อนด้วย Groq API (Llama 3)
ระบบบันทึกประวัติแชทไว้ใน PostgreSQL และมี Redis ช่วย Cache ให้โหลดเร็วขึ้น

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
      ├── PostgreSQL (Supabase) ← เก็บข้อมูลถาวร
      ├── Redis                 ← Cache ประวัติแชท
      └── Groq API              ← AI model (Llama 3.3-70b)
```

---

## Tech Stack

| Layer | เครื่องมือ | หน้าที่ |
|-------|-----------|---------|
| **Frontend** | Next.js 15 (App Router) | UI หน้าแชท |
| **State / Auth** | Supabase SSR (`@supabase/ssr`) | Session management |
| **Backend** | Spring Boot 3.2.5 (Java 21) | Business logic, API |
| **AI Model** | Groq API — `llama-3.3-70b-versatile` | สร้างคำตอบจาก AI |
| **AI Framework** | Spring AI 1.0.0-M1 | ต่อกับ Groq ผ่าน OpenAI-compatible API |
| **Resilience** | Resilience4j | Circuit Breaker + Retry สำหรับ Groq API |
| **Database** | Supabase PostgreSQL | เก็บ users, rooms, messages, ai_context |
| **Cache** | Redis 7.2 | Cache ประวัติแชทแยกตาม roomId |
| **Gateway** | Nginx | Reverse proxy, routing |
| **ORM** | Spring Data JPA (Hibernate) | Object-Relational Mapping |

---

## Database Schema (`chat` schema)

```
chat.users
├── id            BIGSERIAL PK
├── username      VARCHAR(50) UNIQUE
├── email         VARCHAR(100) UNIQUE
├── password_hash VARCHAR(255)
├── role          VARCHAR(20)  → 'USER' | 'AI'
├── supabase_uid  UUID UNIQUE  → เชื่อม Supabase Auth
└── created_at    TIMESTAMPTZ

chat.rooms
├── id         BIGSERIAL PK
├── name       VARCHAR(100)
├── is_group   BOOLEAN
└── created_at TIMESTAMPTZ

chat.room_members  (junction table)
├── room_id   FK → rooms.id
├── user_id   FK → users.id
└── joined_at TIMESTAMPTZ

chat.messages
├── id         BIGSERIAL PK
├── room_id    FK → rooms.id
├── sender_id  FK → users.id
├── content    TEXT
└── created_at TIMESTAMPTZ

chat.ai_context
├── id          BIGSERIAL PK
├── user_id     FK → users.id (AI bot user)
└── system_text TEXT  → System Prompt ของ AI

chat.message_embeddings  (Phase 4 — รอ implement)
├── id         BIGSERIAL PK
├── message_id FK → messages.id
├── embedding  vector(384)
└── created_at TIMESTAMPTZ
```

---

## API Endpoints

### `POST /v1/api/chat-app/user/resolve`

ใช้ตอน Login — แปลง Supabase UUID → chat user ID พร้อมสร้าง room อัตโนมัติ

**Request:**
```json
{
  "supabaseUid": "abc-123-uuid",
  "email": "user@example.com",
  "username": "John Doe"
}
```

**Response:**
```json
{
  "userId": 5,
  "roomId": 3
}
```

**Logic:**
1. หา `chat.users` ด้วย `supabase_uid` → ถ้าไม่เจอ สร้าง user ใหม่
2. หา AI bot user (role = 'AI') → ถ้าไม่เจอ สร้างอัตโนมัติ
3. หา private room ระหว่าง user + AI → ถ้าไม่เจอ สร้าง room + เพิ่ม room_members
4. คืน userId และ roomId

---

### `GET /v1/api/chat-app/message/history/{roomId}`

ดึงประวัติแชทของห้องนั้น เรียงตามเวลา (เก่าสุด → ใหม่สุด)

**Response:**
```json
[
  {
    "id": 1,
    "roomId": 3,
    "senderId": 5,
    "senderUsername": "john",
    "senderRole": "USER",
    "content": "สวัสดี",
    "createdAt": "2025-01-01T10:00:00Z"
  },
  {
    "id": 2,
    "roomId": 3,
    "senderId": 4,
    "senderUsername": "ai_assistant",
    "senderRole": "AI",
    "content": "สวัสดีครับ มีอะไรให้ช่วยไหม",
    "createdAt": "2025-01-01T10:00:05Z"
  }
]
```

**Cache:** Redis key = `chatHistory::roomId` — evict อัตโนมัติเมื่อมีข้อความใหม่

---

### `POST /v1/api/chat-app/message`

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

**ขั้นตอนภายใน:**
1. Validate room + sender มีอยู่จริง
2. บันทึกข้อความของ user ลง `chat.messages`
3. ดึงประวัติการสนทนาทั้งหมดของห้อง
4. สร้าง Prompt = System Message (จาก `ai_context`) + ประวัติ + ข้อความใหม่
5. เรียก Groq API ผ่าน `GroqAiClient` (มี Circuit Breaker ป้องกัน)
6. บันทึกคำตอบของ AI ลง `chat.messages`
7. Evict Redis cache สำหรับห้องนั้น
8. คืน reply

---

## AI Flow (Prompt Building)

```
┌─────────────────────────────────────────┐
│  Prompt ที่ส่งให้ Groq                    │
├─────────────────────────────────────────┤
│ [System]  → system_text จาก ai_context  │
│ [User]    → "สวัสดี"                     │
│ [AI]      → "สวัสดีครับ..."              │
│ [User]    → "ช่วยสรุป Spring Boot หน่อย" │
│  ↑ ประวัติทั้งหมดของห้อง (ไม่มี limit)  │
└─────────────────────────────────────────┘
```

> **หมายเหตุ:** ปัจจุบันโหลดประวัติทั้งหมด (ไม่มี limit) — อาจเกิน context window ถ้าแชทยาวมาก

---

## Resilience (Circuit Breaker)

ใช้ **Resilience4j** ห่อการเรียก Groq API ใน `GroqAiClient`

| ค่า | Setting |
|-----|---------|
| Sliding window | 10 requests |
| เปิด circuit เมื่อ fail | ≥ 50% |
| รอก่อน half-open | 30 วินาที |
| Retry สูงสุด | 3 ครั้ง |
| รอระหว่าง retry | 2 วินาที |
| Slow call threshold | > 10 วินาที |

**Fallback:** เมื่อ circuit เปิดหรือ retry หมด → คืน `"ขออภัย ระบบ AI ไม่สามารถตอบได้ชั่วคราว กรุณาลองใหม่อีกครั้ง"` แทน error 500

---

## Caching (Redis)

| Cache key | ค่า | เมื่อ evict |
|-----------|-----|------------|
| `chatHistory::1` | List ประวัติแชทของ room 1 | เมื่อมีข้อความใหม่ใน room 1 |
| `chatHistory::N` | List ประวัติแชทของ room N | เมื่อมีข้อความใหม่ใน room N |

Redis อยู่บน internal network ไม่ expose port ออกข้างนอก และใช้ password จาก `${REDIS_PASSWORD}`

---

## User Identity (Supabase ↔ Chat DB)

ระบบมีสองโลกที่ต้อง bridge กัน:

```
Supabase Auth           chat.users (DB)
──────────────          ───────────────
id: "abc-uuid"    ←──→  supabase_uid: "abc-uuid"
                         id: 5  ← ใช้ภายใน chat system
```

**Flow เมื่อ Login:**
```
1. Frontend ดึง Supabase session (UUID)
2. เรียก POST /user/resolve
3. Backend หา/สร้าง chat user + room กับ AI
4. Frontend เก็บ userId + roomId ใน state
5. ใช้ค่าเหล่านี้ทุก request ถัดไป
```

---

## Frontend Components

**หน้าแชท:** `frontend/app/chat-app/message/page.tsx`

| State | ประเภท | หน้าที่ |
|-------|--------|---------|
| `messages` | `Message[]` | รายการข้อความที่แสดง |
| `roomId` | `number \| null` | Room ID จาก resolve |
| `currentUserId` | `number \| null` | User ID จาก resolve |
| `isResolving` | `boolean` | Loading ขณะ resolve session |
| `isLoading` | `boolean` | Loading ขณะรอ AI ตอบ |

**Hooks ที่ใช้:**
- `useSupabaseSession()` — ดึง Supabase auth session
- `useNotification()` — แสดง error toast
- `useTheme()` — Dark/Light mode
- `useChangeTitle()` — อัพเดท breadcrumb

---

## Environment Variables ที่จำเป็น

| ตัวแปร | ใช้ที่ | คำอธิบาย |
|--------|--------|---------|
| `GROK_API_KEY` | chat-service | API Key สำหรับ Groq |
| `SUPABASE_DB_USERNAME` | chat-service | Username ต่อ Supabase DB |
| `SUPABASE_DB_PASSWORD` | chat-service | Password ต่อ Supabase DB |
| `REDIS_PASSWORD` | chat-service | Password ของ Redis |
| `NEXT_PUBLIC_SUPABASE_URL` | frontend | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | frontend | Supabase anon key |
| `NEXT_PUBLIC_API_URL` | frontend | Base URL ของ Nginx gateway |

---

## สิ่งที่ยังไม่ได้ทำ (Backlog)

| รายการ | เหตุผล |
|--------|--------|
| **Vector Search (RAG)** | รอตัดสินใจ embedding approach (ONNX vs HuggingFace API) เพราะ ACA free tier มี RAM จำกัด |
| **จำกัด history ก่อนส่ง Prompt** | ปัจจุบันโหลดทั้งหมด อาจเกิน context window ถ้าแชทยาว |
| **CORS production domain** | WebConfig ยังใส่แค่ localhost |
| **pgvector activation** | SQL พร้อมแล้วใน `database/03_pgvector_schema.sql` รอรันบน Supabase |
