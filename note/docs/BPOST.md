# B-Post - Current Code Spec

เอกสารนี้สรุป `bpost-service` จาก source code ปัจจุบัน: social feed, friends, direct messages, notifications, presence และ websocket realtime

---

## ภาพรวม

B-Post เป็น microservice แนว social network ภายใน Sandbox ใช้ identity กลางจาก `chat_app.users` และเก็บข้อมูลของตัวเองใน schema `b_post`

เส้นทางหลัก:

```text
Frontend -> Nginx gateway -> /v1/api/b-post/* -> bpost-service
```

Auth:

- gateway ตรวจ Bearer JWT ผ่าน oauth2-proxy
- `JwtAuthFilter` จาก `common-auth` รันบน `/v1/api/b-post/*`
- controller ใช้ `CurrentUser.requireUserId()` เพื่อ resolve internal user id จาก `chat_app.users`
- websocket path `/v1/api/b-post/ws/` ไม่ผ่าน `auth_request`; STOMP CONNECT ส่ง JWT เอง

---

## Tech Stack

| ส่วน | เทคโนโลยี |
|---|---|
| Backend | Spring Boot 3.2.5 |
| Java compile | Java 17 (`pom.xml`) |
| Runtime image | Eclipse Temurin 21 JRE |
| Database | Supabase PostgreSQL |
| ORM | Spring Data JPA + native queries |
| Auth helper | `common-auth` |
| Realtime | Spring WebSocket + STOMP + SockJS |
| Storage | Supabase Storage REST API |

---

## Environment Variables

| ตัวแปร | ใช้ที่ | หมายเหตุ |
|---|---|---|
| `SUPABASE_DB_USERNAME` | local datasource username | default `postgres` |
| `SUPABASE_DB_PASSWORD` | datasource password | ใช้ต่อ PostgreSQL |
| `SPRING_DATASOURCE_URL` | ACA override | Supavisor pooler `:6543` |
| `SPRING_DATASOURCE_USERNAME` | ACA override | format `postgres.<project-ref>` |
| `SUPABASE_URL` | `BlobStorageService` | base URL สำหรับ Storage REST API |
| `SUPABASE_SERVICE_ROLE_KEY` | `BlobStorageService` | service role key สำหรับ upload |
| `CORS_ALLOWED_ORIGIN_GATEWAY` | CORS และ websocket allowed origins | default local/prod gateway |
| `GROK_API_KEY` | inherited app.yml config | มีใน config pattern แต่ B-Post ไม่ได้ใช้ AI flow หลัก |

Config สำคัญใน `backend/bpost/src/main/resources/application.yml`:

```yaml
app:
  api:
    prefix:
      b-post: v1/api/b-post
  supabase:
    storage:
      bucket-name: images
  websocket:
    endpoint: /v1/api/b-post/ws
```

---

## Database Schema

Migration อยู่ที่:

```text
backend/bpost/src/main/resources/db/migration/V1__b_post_schema.sql
```

ทุก FK ของ user ชี้ไป `chat_app.users(id)`

| Table | หน้าที่ |
|---|---|
| `b_post.posts` | post content, image URLs, visibility, soft delete |
| `b_post.comments` | comments ใต้ post, soft delete |
| `b_post.post_likes` | composite key `(post_id, user_id)` |
| `b_post.friendships` | friend request/accepted/declined, normalized pair |
| `b_post.conversations` | 1:1 conversation, normalized pair `user_a_id < user_b_id` |
| `b_post.messages` | direct messages, content หรือ image อย่างน้อยหนึ่งอย่าง |
| `b_post.notifications` | notifications + unread state |

Migration ยังเพิ่ม column:

```sql
ALTER TABLE chat_app.users
  ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
```

ใช้สำหรับ presence/last seen ใน `UserSummaryDto`

---

## Common Response DTOs

### `PageResponse<T>`

```json
{
  "items": [],
  "hasMore": false,
  "nextCursor": null
}
```

### `UserSummaryDto`

```json
{
  "id": 3,
  "supabaseUid": "...",
  "displayName": "John",
  "avatarUrl": "https://...",
  "lastSeenAt": "2026-05-01T10:00:00Z"
}
```

---

## Posts

Base path: `/v1/api/b-post/posts`

| Method | Path | รายละเอียด |
|---|---|---|
| `POST` | `/posts` | สร้าง post |
| `GET` | `/posts/feed?beforeId=&limit=20` | feed ของตัวเอง + friends |
| `GET` | `/posts/by-author/{authorId}?beforeId=&limit=20` | post ของ author |
| `GET` | `/posts/{postId}` | get active post |
| `PATCH` | `/posts/{postId}` | edit เฉพาะเจ้าของ |
| `DELETE` | `/posts/{postId}` | soft delete เฉพาะเจ้าของ |
| `POST` | `/posts/{postId}/likes` | like |
| `DELETE` | `/posts/{postId}/likes` | unlike |

Create/update request:

```json
{
  "content": "hello",
  "imageUrls": ["https://..."],
  "visibility": "FRIENDS"
}
```

`PostDto`:

```json
{
  "id": 10,
  "author": { "id": 3, "displayName": "John" },
  "content": "hello",
  "imageUrls": [],
  "visibility": "FRIENDS",
  "createdAt": "2026-05-01T10:00:00Z",
  "editedAt": null,
  "likeCount": 1,
  "commentCount": 2,
  "likedByMe": false
}
```

Feed logic:

- `limit` ถูก clamp เป็น 1-50
- ดึง post ของตัวเอง + accepted friends
- cursor ใช้ `beforeId`
- soft-deleted post ถูก exclude

---

## Comments

Base path: `/v1/api/b-post`

| Method | Path | รายละเอียด |
|---|---|---|
| `POST` | `/posts/{postId}/comments` | add comment |
| `GET` | `/posts/{postId}/comments?beforeId=&limit=30` | list comments |
| `PATCH` | `/comments/{commentId}` | edit เฉพาะ author |
| `DELETE` | `/comments/{commentId}` | soft delete เฉพาะ author |

Request:

```json
{
  "content": "nice post"
}
```

`CommentDto`:

```json
{
  "id": 7,
  "postId": 10,
  "author": { "id": 3, "displayName": "John" },
  "content": "nice post",
  "createdAt": "2026-05-01T10:00:00Z",
  "editedAt": null
}
```

เมื่อ comment สำเร็จ จะ push notification type `POST_COMMENT` ไปหาเจ้าของ post ถ้าไม่ใช่ self-action

---

## Friends

Base path: `/v1/api/b-post`

| Method | Path | Body/Query |
|---|---|---|
| `POST` | `/friends/requests` | `{ "addresseeId": 4 }` |
| `POST` | `/friends/requests/{id}/accept` | - |
| `POST` | `/friends/requests/{id}/decline` | - |
| `GET` | `/friends` | - |
| `GET` | `/friends/requests/incoming` | - |
| `GET` | `/friends/requests/outgoing` | - |
| `GET` | `/users/search?q=ann` | query text |

Rules:

- friend ตัวเองไม่ได้
- duplicate pair กันด้วย normalized unique index
- request ที่มีอยู่แล้วจะคืน record เดิม
- เฉพาะ addressee ตอบ accept/decline ได้
- status มี `PENDING`, `ACCEPTED`, `DECLINED`

เมื่อส่ง request จะ push notification `FRIEND_REQUEST`

เมื่อ accept จะ push notification `FRIEND_ACCEPT`

---

## Direct Messages

Base path: `/v1/api/b-post`

| Method | Path | รายละเอียด |
|---|---|---|
| `GET` | `/conversations` | list conversation ของ caller |
| `POST` | `/conversations` | open/create conversation กับ friend |
| `GET` | `/conversations/{conversationId}/messages?beforeId=&limit=30` | history |
| `POST` | `/conversations/{conversationId}/read` | mark all unread messages read |
| `POST` | `/messages` | send message |
| `POST` | `/messages/upload-image` | upload image แล้วคืน URL |

Open conversation:

```json
{
  "userId": 4
}
```

Send message:

```json
{
  "conversationId": 1,
  "recipientId": null,
  "content": "hi",
  "imageUrl": null
}
```

ถ้าไม่มี `conversationId` ต้องส่ง `recipientId` เพื่อ auto-create conversation โดยต้องเป็น friends ก่อน

Message rules:

- คุยกับตัวเองไม่ได้
- ต้องเป็น friends ถึงจะเปิด conversation ได้
- sender ต้องเป็น participant ของ conversation
- message ต้องมี `content` หรือ `imageUrl`
- history คืน newest-first; frontend reverse เองถ้าต้องการ chronological display

Realtime side effect:

- ส่ง message แล้ว push ไป `/user/queue/messages` ให้ recipient
- echo กลับ sender เพื่อ sync หลาย tab
- push notification type `MESSAGE`

---

## Notifications

Base path: `/v1/api/b-post/notifications`

| Method | Path | รายละเอียด |
|---|---|---|
| `GET` | `/notifications?unreadOnly=false&limit=30` | list notifications |
| `GET` | `/notifications/unread-count` | unread count |
| `POST` | `/notifications/{id}/read` | mark read เฉพาะของตัวเอง |

Notification types:

| Type | Trigger |
|---|---|
| `POST_LIKE` | like post |
| `POST_COMMENT` | comment post |
| `FRIEND_REQUEST` | send friend request |
| `FRIEND_ACCEPT` | accept friend request |
| `MESSAGE` | send direct message |

`NotificationService.push(...)` จะไม่ notify ถ้า `recipientId == actorId`

---

## Presence และ WebSocket

REST:

| Method | Path | Response |
|---|---|---|
| `GET` | `/v1/api/b-post/presence/online` | set ของ online user ids |

WebSocket/STOMP:

| ค่า | ปัจจุบัน |
|---|---|
| Endpoint | `/v1/api/b-post/ws` |
| SockJS | enabled |
| App prefix | `/app` |
| Broker prefixes | `/topic`, `/queue` |
| User prefix | `/user` |

Client connect:

```text
Authorization: Bearer <Supabase JWT>
```

Inbound STOMP:

| Destination | Payload |
|---|---|
| `/app/message.send` | `MessageSendRequest` |
| `/app/message.read` | `{ "conversationId": 1 }` |

Outbound STOMP:

| Destination | รายละเอียด |
|---|---|
| `/user/queue/messages` | message ส่วนตัว |
| `/user/queue/notifications` | notification ส่วนตัว |
| `/topic/presence` | `{ userId, online }` |

Presence เป็น in-memory set (`ConcurrentHashMap.newKeySet`) ดังนั้นถ้า scale หลาย replicas จะไม่ shared ระหว่าง instances

---

## Supabase Storage

Upload ใช้ `BlobStorageService` ผ่าน Supabase Storage REST API และ bucket default `images`

| Endpoint | Prefix | Response |
|---|---|---|
| `POST /v1/api/b-post/messages/upload-image` | `b-post/messages` | `{ "url": "...", "path": "..." }` |
| `POST /v1/api/b-post/posts/upload-image` | `b-post/posts` | `{ "url": "...", "path": "..." }` |
| `POST /v1/api/b-post/blog/upload-image` | bucket root | legacy `ByteArrayResource` |

Request field:

```text
imageFile
```

Object path format:

```text
<prefix>/<uuid>_<originalFilename>
```

---

## Frontend Integration

API constants อยู่ที่ `frontend/constants/api/ApiSandbox.ts`

B-Post realtime provider อยู่ที่:

```text
frontend/providers/BPostRealtimeProvider.tsx
```

Route group `/b-post` wrap provider ใน:

```text
frontend/app/b-post/layout.tsx
```

Pages ปัจจุบัน:

| Path | ไฟล์ |
|---|---|
| `/b-post/blog` | `frontend/app/b-post/blog/page.tsx` |
| `/b-post/socials` | `frontend/app/b-post/socials/page.tsx` |
| `/b-post/messages` | `frontend/app/b-post/messages/page.tsx` |
| `/b-post/profile/[supabaseUid]` | `frontend/app/b-post/profile/[supabaseUid]/page.tsx` |

---

## Known Gaps / Notes

| เรื่อง | สถานะ |
|---|---|
| Visibility enforcement | feed ใช้ตัวเอง + friends แต่ `by-author` และ `get` ยังไม่ได้ enforce visibility ชัดเจน |
| Presence scale-out | online set เป็น memory ต่อ instance |
| Legacy upload endpoint | `/blog/upload-image` ยังคืน `ByteArrayResource`, endpoints ใหม่คืน URL/path |
| Docker/CI | Dockerfile ต้อง build ด้วย context `./backend` เพราะ copy `common-auth`; ดู `INFRA.md` |
