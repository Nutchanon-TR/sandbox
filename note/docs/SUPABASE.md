# Supabase Integration - Current Code Spec

เอกสารนี้สรุปการใช้งาน Supabase จาก source code ปัจจุบัน ทั้ง auth, database, storage และ schema ที่ backend ใช้จริง

---

## Supabase Auth ใน Frontend

ไฟล์:

- `frontend/lib/supabase/client.ts`
- `frontend/lib/supabase/server.ts`
- `frontend/middleware.ts`
- `frontend/app/auth/callback/route.ts`
- `frontend/providers/AuthProvider.tsx`

Client helpers:

| Helper | ใช้ที่ | หน้าที่ |
|---|---|---|
| `createSupabaseBrowser()` | client components/providers | สร้าง browser client ด้วย `createBrowserClient` |
| `createSupabaseServer()` | callback/server-side | สร้าง server client ด้วย `createServerClient` และ cookies |

Auth callback:

1. อ่าน `code` และ `next`
2. เลือก origin จาก `NEXT_PUBLIC_SITE_URL`, forwarded headers หรือ request origin
3. เรียก `exchangeCodeForSession(code)`
4. สำเร็จ redirect ไป `next`
5. fail redirect ไป `/login?error=auth-code-exchange`

Middleware:

- refresh session ด้วย `supabase.auth.getUser()`
- กัน private routes ฝั่ง server
- redirect user ที่ login แล้วออกจาก `/login`

---

## User Sync

หลัง frontend ได้ Supabase session แล้ว `AuthProvider` เรียก:

```text
POST /v1/api/user/sync
```

Body:

```json
{
  "supabaseUid": "<uuid>",
  "email": "user@example.com",
  "username": "User",
  "avatarUrl": "https://..."
}
```

`UserController` ตรวจว่า `JWT.sub` ตรงกับ `supabaseUid` ใน body ก่อน upsert `chat_app.users`

Response:

```json
{
  "userId": 3
}
```

frontend เก็บค่าเป็น `internalUserId`

---

## Database Connection

ทุก backend service ใช้ PostgreSQL ผ่าน JDBC:

```yaml
spring.datasource.driver-class-name: org.postgresql.Driver
spring.datasource.url: jdbc:postgresql://db.xabewjiiewyhhjfekazv.supabase.co:5432/postgres
spring.datasource.username: ${SUPABASE_DB_USERNAME:postgres}
spring.datasource.password: ${SUPABASE_DB_PASSWORD:}
```

บน ACA workflow override เป็น Supavisor transaction pooler:

```text
jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:6543/postgres?prepareThreshold=0
```

ดูรายละเอียดใน `POOLER.md`

---

## Schemas ปัจจุบัน

| Schema | เจ้าของ/ใช้โดย | หมายเหตุ |
|---|---|---|
| `auth` | Supabase | `auth.users` จัดการโดย Supabase |
| `chat_app` | user-service, chatapp, common-auth, bpost FK | identity กลาง + chat/AI tables |
| `users` | user-service | profile เพิ่มเติม |
| `b_post` | bpost-service | social posts/messages/notifications |
| `dinner` | dinner-service | suppliers/orders |

---

## `chat_app` Schema

### `chat_app.users`

| column | type | source |
|---|---|---|
| `id` | `BIGSERIAL PK` | generated |
| `supabase_uid` | `UUID UNIQUE NOT NULL` | Supabase `auth.users.id` |
| `display_name` | `VARCHAR(100) NOT NULL` | user-service sync |
| `avatar_url` | `TEXT` | user-service sync |
| `created_at` | `TIMESTAMPTZ` | entity `@PrePersist` |
| `last_seen_at` | `TIMESTAMPTZ` | added by b-post migration |

### ChatApp tables

| table | columns สำคัญ |
|---|---|
| `rooms` | `id`, `name`, `is_group`, `user_id`, `created_at` |
| `room_members` | `room_id`, `ai_id` |
| `chats` | `id`, `room_id`, `sender_id`, `is_ai`, `content`, `created_at` |
| `chat_embeddings` | `id`, `message_id`, `embedding vector(384)`, `created_at` |
| `ai_context` | `id`, `ai_name`, `avatar_url`, `poster_url`, `role`, `character`, `biography`, `rule` |
| `ai_likes` | `id`, `user_id`, `ai_id`, `created_at` |
| `ai_friends` | `id`, `user_id`, `ai_id`, `created_at` |
| `comments` | `id`, `user_id`, `ai_id`, `content`, `star`, `created_at` |
| `comment_likes` | `id`, `user_id`, `comment_id`, `created_at` |

หมายเหตุ: `chat_embeddings.message_id` join ไป `chat_app.chats.id`

---

## `users` Schema

`users.profiles` ใช้โดย user-service:

| column | type |
|---|---|
| `supabase_uid` | `UUID PK` |
| `username` | `VARCHAR(50) NOT NULL` |
| `email` | `VARCHAR(100) UNIQUE NOT NULL` |
| `role` | `VARCHAR(20) NOT NULL DEFAULT 'USER'` |
| `avatar_url` | text |
| `created_at` | timestamp |

Endpoints:

| Method | Path |
|---|---|
| `GET` | `/v1/api/user/profile/{supabaseUid}` |
| `POST` | `/v1/api/user/profile` |

`ProfileCreateRequestDto` ปัจจุบันรับ `supabaseUid`, `username`, `email`

---

## `b_post` Schema

มี migration ใน:

```text
backend/bpost/src/main/resources/db/migration/V1__b_post_schema.sql
```

Tables:

| table | หน้าที่ |
|---|---|
| `b_post.posts` | post content, image URLs, visibility, soft delete |
| `b_post.comments` | comments ใต้ post |
| `b_post.post_likes` | composite primary key `(post_id, user_id)` |
| `b_post.friendships` | friend requests, unique normalized pair |
| `b_post.conversations` | 1:1 conversations, normalized pair |
| `b_post.messages` | direct messages with content or image |
| `b_post.notifications` | notifications and unread state |

ทุก user FK ชี้กลับไปที่ `chat_app.users(id)`

---

## `dinner` Schema

Dinner service ใช้:

| table | ใช้โดย |
|---|---|
| `dinner.suppliers` | JPA entity `Supplier` |
| `dinner.orders` | native join query ใน `SupplierRepository` |

Endpoint หลัก:

```text
GET /v1/api/dinner/supplier/inquiry?page=1&size=10
```

Response เป็น `PageResponse<SupplierOrderDto>` จาก join `suppliers` + `orders`

---

## Vector Search

ChatApp ใช้ HuggingFace embedding:

```text
intfloat/multilingual-e5-small
```

Flow:

1. save message ลง `chat_app.chats`
2. `EmbeddingService` ส่ง `"passage: " + content` ไป HuggingFace
3. save vector ลง `chat_app.chat_embeddings`
4. ก่อนเรียก AI ส่ง `"query: " + userQuery` ไป embed
5. query similar messages:

```sql
SELECT me.message_id
FROM chat_app.chat_embeddings me
JOIN chat_app.chats m ON m.id = me.message_id
WHERE m.room_id = ?
ORDER BY me.embedding <=> ?::vector
LIMIT ?
```

ถ้า HuggingFace ล้ม จะ log warning แล้ว ChatApp ยังตอบต่อโดยไม่มี vector context

---

## Supabase Storage

Bucket default:

```text
images
```

Config:

```yaml
app.supabase.url: ${SUPABASE_URL:}
app.supabase.service-role-key: ${SUPABASE_SERVICE_ROLE_KEY:}
app.supabase.storage.bucket-name: images
```

`BlobStorageService` upload ผ่าน Supabase Storage REST API:

```text
POST /storage/v1/object/{bucket}/{objectPath}
Authorization: Bearer <SUPABASE_SERVICE_ROLE_KEY>
apikey: <SUPABASE_SERVICE_ROLE_KEY>
```

Endpoints ปัจจุบัน:

| Service | Endpoint | Response |
|---|---|---|
| bpost | `POST /v1/api/b-post/blog/upload-image` | legacy `ByteArrayResource` |
| bpost | `POST /v1/api/b-post/messages/upload-image` | `{ "url": "...", "path": "..." }` |
| bpost | `POST /v1/api/b-post/posts/upload-image` | `{ "url": "...", "path": "..." }` |

Object prefixes:

| Endpoint | Prefix |
|---|---|
| messages upload | `b-post/messages` |
| posts upload | `b-post/posts` |
| legacy blog upload | bucket root |

AI avatar/poster (`ai_context.avatar_url`, `ai_context.poster_url`) เป็น URL/string ใน DB ตอนนี้ยังไม่มี upload endpoint ใน ChatApp

---

## ไม่มี Redis ในโค้ดปัจจุบัน

เอกสารเก่าเคยพูดถึง Redis cache แต่ source ปัจจุบันไม่มี Redis dependency, ไม่มี Redis container ใน docker-compose และ ChatApp history อ่าน DB ตรงผ่าน repository ทุกครั้ง

ถ้าจะเพิ่ม cache ภายหลัง ต้องเพิ่ม dependency/config และ invalidation logic ใหม่
