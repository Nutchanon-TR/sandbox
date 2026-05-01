# ChatApp - Current Code Spec

เอกสารนี้อัปเดตจาก source code ปัจจุบันของ `backend/chatapp` และ frontend API constants

---

## ภาพรวม

ChatApp เป็น Spring Boot service สำหรับแชทกับ AI companion ผ่าน Groq API และเพิ่ม context ด้วย HuggingFace embedding + pgvector บน Supabase PostgreSQL

เส้นทางหลัก:

```
Frontend -> Nginx gateway -> /v1/api/chat-app/* -> chat-service
```

Auth ใช้ Supabase JWT ผ่าน gateway และ `common-auth`:

- `JwtAuthFilter` รันบน `/v1/api/chat-app/*`
- controller ใช้ `CurrentUser.requireUserId()` สำหรับ action ที่ต้องรู้ user ปัจจุบัน
- ไม่รับ `userId` หรือ `senderId` จาก client สำหรับ write path แล้ว

---

## Tech Stack

| ส่วน | เทคโนโลยี |
|---|---|
| Backend | Spring Boot 3.2.5 |
| Java compile | Java 17 (`pom.xml`) |
| Runtime image | Eclipse Temurin 21 JRE |
| AI chat | Spring AI OpenAI-compatible client ไป Groq |
| Model | `llama-3.3-70b-versatile` |
| Resilience | Resilience4j circuit breaker + retry |
| Embedding | HuggingFace `intfloat/multilingual-e5-small` |
| Database | Supabase PostgreSQL |
| Vector search | pgvector `vector(384)` |

---

## Environment Variables

| ตัวแปร | ใช้ที่ | หมายเหตุ |
|---|---|---|
| `SUPABASE_DB_USERNAME` | local JDBC username | ค่า default คือ `postgres` |
| `SUPABASE_DB_PASSWORD` | JDBC password | ใช้ทุก backend service |
| `SPRING_DATASOURCE_URL` | ACA override | ชี้ Supavisor transaction pooler `:6543` |
| `SPRING_DATASOURCE_USERNAME` | ACA override | format `postgres.<project-ref>` |
| `SUPABASE_URL` | Supabase REST/Storage base URL | config กลางใน app.yml |
| `SUPABASE_SERVICE_ROLE_KEY` | service role key | สำหรับ Supabase operations |
| `GROK_API_KEY` | Groq API key | ชื่อนี้สะกดตามโค้ดปัจจุบัน |
| `HUGGINGFACE_API_KEY` | HuggingFace API key | optional แต่ถ้าไม่มี RAG จะ degrade |
| `CORS_ALLOWED_ORIGIN_GATEWAY` | allowed origins | ใช้ใน `WebConfig` |

---

## Database Schema ที่ ChatApp ใช้

### `chat_app.users`

เขียนโดย user-service, ChatApp อ่านเพื่อ resolve sender

| column | type |
|---|---|
| `id` | `BIGSERIAL PK` |
| `supabase_uid` | `UUID UNIQUE NOT NULL` |
| `display_name` | `VARCHAR(100) NOT NULL` |
| `avatar_url` | `TEXT` |
| `created_at` | `TIMESTAMPTZ` |
| `last_seen_at` | `TIMESTAMPTZ`, เพิ่มโดย b-post migration |

### Chat tables

| table | columns สำคัญ |
|---|---|
| `chat_app.rooms` | `id`, `name`, `is_group`, `user_id`, `created_at` |
| `chat_app.room_members` | `room_id`, `ai_id` |
| `chat_app.chats` | `id`, `room_id`, `sender_id`, `is_ai`, `content`, `created_at` |
| `chat_app.chat_embeddings` | `id`, `message_id`, `embedding vector(384)`, `created_at` |
| `chat_app.ai_context` | `id`, `ai_name`, `avatar_url`, `poster_url`, `role`, `character`, `biography`, `rule` |

หมายเหตุ: ถึง table ชื่อ `chat_embeddings` แต่ foreign key column ในโค้ดปัจจุบันคือ `message_id` และ join ไปที่ `chat_app.chats.id`

### Social tables

| table | หน้าที่ |
|---|---|
| `chat_app.ai_likes` | user like AI |
| `chat_app.ai_friends` | user friend AI |
| `chat_app.comments` | comment + `star` บน AI profile |
| `chat_app.comment_likes` | like comment |

---

## Room Endpoints

Base path: `/v1/api/chat-app`

| Method | Path | Auth identity |
|---|---|---|
| `GET` | `/room/list` | `CurrentUser.requireUserId()` |
| `POST` | `/room/create` | `CurrentUser.requireUserId()` |

`POST /room/create` request:

```json
{
  "name": "Kealith",
  "isGroup": false,
  "systemPrompt": "You are a helpful assistant."
}
```

Response `RoomDto`:

```json
{
  "id": 3,
  "name": "Kealith",
  "isGroup": false,
  "aiAvatarUrl": "https://.../avatar.png",
  "createdAt": "2026-05-01T10:00:00Z"
}
```

สร้าง room แล้ว `RoomService` จะสร้าง `ai_context` และ insert `room_members(room_id, ai_id)` ให้ด้วย

---

## Chat Endpoints

| Method | Path | รายละเอียด |
|---|---|---|
| `GET` | `/chat/history/{roomId}?beforeId=&limit=20` | cursor pagination, คืนข้อความเรียงเก่าไปใหม่ |
| `POST` | `/chat` | ส่งข้อความให้ AI, user id มาจาก JWT |

`POST /chat` request ปัจจุบันไม่มี `senderId`:

```json
{
  "roomId": 3,
  "message": "ช่วยสรุป Spring Boot ให้หน่อย"
}
```

Response:

```json
{
  "reply": "..."
}
```

History response:

```json
{
  "messages": [
    {
      "id": 1,
      "roomId": 3,
      "senderId": 5,
      "senderName": "John",
      "isAi": false,
      "content": "สวัสดี",
      "createdAt": "2026-05-01T10:00:00Z"
    }
  ],
  "hasMore": false
}
```

ข้อควรระวัง: `GET /chat/history/{roomId}` ปัจจุบันไม่ได้เช็คว่า room นี้เป็นของ caller หรือไม่ใน controller/service

---

## AI Prompt Flow

`ChatService.getAiResponse(callerId, request)` ทำงานตามลำดับ:

1. validate `roomId`
2. ใช้ `callerId` จาก `CurrentUser` หา `chat_app.users`
3. save user message ลง `chat_app.chats`
4. embed user message ด้วย `EmbeddingService.embedAndSave()`
5. หา `ai_id` จาก `room_members`
6. build prompt จาก `AiContext.buildSystemPrompt()`
7. ค้นหา similar messages 5 รายการด้วย pgvector
8. ดึง history ล่าสุด 20 messages
9. เรียก `GroqAiClient.chat(prompt)`
10. save AI reply และ embed reply

Resilience4j setting ปัจจุบัน:

| setting | ค่า |
|---|---|
| sliding window | 10 |
| minimum calls | 5 |
| failure threshold | 50% |
| slow call threshold | 80% |
| slow call duration | 10s |
| open wait | 30s |
| retry attempts | 3 |
| retry wait | 2s |

Fallback message:

```text
ขออภัย ระบบ AI ไม่สามารถตอบได้ชั่วคราว กรุณาลองใหม่อีกครั้ง
```

---

## Social และ Discovery Endpoints

Base path: `/v1/api/chat-app`

### AI like/friend

| Method | Path |
|---|---|
| `POST` | `/ai/{aiId}/like` |
| `DELETE` | `/ai/{aiId}/like` |
| `POST` | `/ai/{aiId}/friend` |
| `DELETE` | `/ai/{aiId}/friend` |
| `GET` | `/user/friends` |

### AI comments

| Method | Path | Body |
|---|---|---|
| `POST` | `/ai/{aiId}/comments` | `{ "content": "...", "star": 5 }` |
| `GET` | `/ai/{aiId}/comments` | - |
| `DELETE` | `/comments/{commentId}` | - |
| `POST` | `/comments/{commentId}/like` | - |
| `DELETE` | `/comments/{commentId}/like` | - |

`CommentDto` ปัจจุบันมี `likeCount` และ `star`

### Discovery

| Method | Path | Response |
|---|---|---|
| `GET` | `/blog/list` | list ของ `AiContextUserDto` |
| `GET` | `/blog/detail/{aiId}` | `AiDetailDto` |

`/blog/list` คืน `aiId`, `aiName`, `avatarUrl`, `posterUrl`, `userId`, `userDisplayName`, `roomId`, `likeCount`, `friendCount`, `commentCount`

---

## Frontend Mapping

ไฟล์ `frontend/constants/api/ApiSandbox.ts` map ChatApp endpoint ปัจจุบันเป็น:

| Constant | Method/Path |
|---|---|
| `CHAT_APP_MESSAGE` | `POST /v1/api/chat-app/chat` |
| `CHAT_APP_HISTORY` | `GET /v1/api/chat-app/chat/history/{roomId}` |
| `CHAT_APP_ROOM_LIST` | `GET /v1/api/chat-app/room/list` |

ChatApp ยังเป็น HTTP request-response ไม่มี WebSocket ใน `backend/chatapp` ตอนนี้ WebSocket ที่มีจริงอยู่ใน `backend/bpost`

---

## Backlog / Known Gaps จากโค้ดปัจจุบัน

| รายการ | สถานะ |
|---|---|
| ตัด `userId`/`senderId` จาก ChatApp API | เสร็จแล้ว |
| Vector search ด้วย HuggingFace + pgvector | เสร็จแล้ว |
| Comment like (`comment_likes`) | เสร็จแล้ว |
| ChatApp WebSocket | ยังไม่มีใน service นี้ |
| Upload AI poster/avatar endpoint | ยังไม่มี |
| Ownership check ใน `GET /chat/history/{roomId}` | ควรเพิ่มถ้าต้องการกันอ่านห้องคนอื่น |
