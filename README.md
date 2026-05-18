# Sandbox

Sandbox เป็นโปรเจกต์ทดลองสถาปัตยกรรม microservice ที่ประกอบด้วย Next.js frontend, Spring Boot backend หลาย service, Nginx gateway, Supabase Auth/Postgres/Storage และ flow ทดลองสำหรับ AI chat, social feed, realtime message และ dashboard ข้อมูล

> Architecture diagrams และเอกสารออกแบบอยู่ใน `note/arch/v3/` และ `note/docs/`

## ภาพรวมระบบปัจจุบัน

```text
Browser
  |
  v
gateway/                         Nginx reverse proxy + OAuth2 Proxy auth_request
  |
  +-- frontend/                   Next.js App Router UI
  +-- backend/user/               user sync + profile service
  +-- backend/chatapp/            AI chat + room + legacy SyncHub APIs
  +-- backend/bpost/              social feed + friends + messages + realtime + storage
      ^
      |
backend/common-auth/             shared JWT decoder + current-user resolver

External services:
- Supabase Auth, Postgres, Storage
- Groq-compatible OpenAI API endpoint
- HuggingFace feature-extraction API
```

## Tech Stack

- **Frontend:** Next.js 15, React 19, TypeScript, Ant Design 5, Tailwind CSS 4, Zustand, Axios
- **Auth:** Supabase Auth ผ่าน `@supabase/ssr` และ OAuth callback ที่ `/auth/callback`
- **Realtime:** STOMP over SockJS สำหรับ B-Post messages, notifications และ presence
- **Backend:** Spring Boot 3.2.5, Spring Data JPA/JDBC, Bean Validation, Lombok
- **Shared auth library:** `backend/common-auth` สำหรับ decode Supabase JWT และ map ไปยัง `chat_app.users`
- **AI:** Spring AI OpenAI client ชี้ไป Groq base URL, model `llama-3.3-70b-versatile`, Resilience4j retry/circuit breaker
- **Embedding:** HuggingFace `intfloat/multilingual-e5-small` และ pgvector ใน Supabase Postgres
- **Storage:** Supabase Storage bucket `images`
- **Gateway:** Nginx + OAuth2 Proxy v7.6.0
- **Deployment:** Docker, Docker Compose, GitHub Actions, GHCR, Azure Container Apps

## โครงสร้างโฟลเดอร์

```text
Sandbox/
|- frontend/                  Next.js app, providers, routes, UI components
|- backend/
|  |- common-auth/            shared JWT/current-user library
|  |- user/                   user sync and profile APIs
|  |- chatapp/                chat, AI response, embeddings, room APIs
|  \- bpost/                  posts, comments, friends, messages, websocket APIs
|- gateway/                   Nginx image and routing template
|- note/                      design docs, architecture diagrams, reports
|- docker-compose.yml         local multi-container orchestration
|- .env.example               root env template for backend/gateway/compose
\- .github/workflows/         Azure Container Apps deploy workflow
```

## Frontend Routes

| Route | สถานะ |
| --- | --- |
| `/` | Home page |
| `/login` | Supabase OAuth login |
| `/auth/callback` | callback route สำหรับแลก auth code เป็น session |
| `/b-post/blog` | feed, post composer, comments, likes, notifications |
| `/b-post/socials` | friends, requests, search users, open conversation |
| `/b-post/messages` | conversation list, chat history, image messages, realtime updates |
| `/b-post/profile/[supabaseUid]` | profile/feed ของผู้ใช้ใน B-Post |
| `/chat-app/message` | AI chat with rooms and infinite history |

หมายเหตุ: เมนู `Chat App > Social` ยังอยู่ใน `frontend/constants/Title.tsx` แต่ยังไม่มี `frontend/app/chat-app/social/page.tsx`

## Gateway Routing

`gateway/nginx.conf.template` เป็น single entry point ที่ port `80`

| Path | Target | Auth |
| --- | --- | --- |
| `/` | `frontend:3000` | ใช้ session guard ใน Next.js |
| `/oauth2/*` | `oauth2-proxy:4180` | OAuth2 Proxy endpoints |
| `/v1/api/user/*` | `user-service:8080` | `auth_request /oauth2/auth`, มี CORS สำหรับ localhost |
| `/v1/api/chat-app/*` | `chat-service:8080` | `auth_request /oauth2/auth` |
| `/v1/api/b-post/ws/*` | `bpost-service:8082` | JWT ถูกส่งใน STOMP `CONNECT` header |
| `/v1/api/b-post/*` | `bpost-service:8082` | `auth_request /oauth2/auth` |

Backend services ยังอ่าน `Authorization: Bearer <jwt>` ด้วย `common-auth` เพื่อหา Supabase UID และ internal user id จากตาราง `chat_app.users`

## Backend Services

### User Service (`backend/user`)

ดูแลการ sync Supabase user เข้า app database และ profile API

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/v1/api/user/sync` | sync/resolve Supabase user เป็น internal user |
| `GET` | `/v1/api/user/profile/{supabaseUid}` | อ่าน profile |
| `POST` | `/v1/api/user/profile` | สร้างหรืออัปเดต profile |

### Chat Service (`backend/chatapp`)

ดูแล AI chat, room, history, embeddings และ legacy SyncHub endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/v1/api/chat-app/room/list` | รายการห้องของผู้ใช้จาก JWT |
| `POST` | `/v1/api/chat-app/room/create` | สร้างห้องแชต |
| `GET` | `/v1/api/chat-app/chat/history/{roomId}` | โหลด chat history แบบ cursor |
| `POST` | `/v1/api/chat-app/chat` | ส่งข้อความ, สร้าง embedding, เรียก Groq, บันทึกคำตอบ |
| `GET` | `/v1/api/chat-app/blog/list` | legacy SyncHub AI list |
| `GET` | `/v1/api/chat-app/blog/detail/{aiId}` | legacy SyncHub AI detail |
| `POST/DELETE` | `/v1/api/chat-app/ai/{aiId}/like` | like/unlike AI card |
| `POST/DELETE` | `/v1/api/chat-app/ai/{aiId}/friend` | add/remove AI friend |
| `GET` | `/v1/api/chat-app/user/friends` | list AI friends |
| `GET/POST` | `/v1/api/chat-app/ai/{aiId}/comments` | list/add AI comments |

### B-Post Service (`backend/bpost`)

ดูแล social feed, friendship, direct messages, notifications, presence, image upload และ STOMP websocket

| Area | Method | Path |
| --- | --- | --- |
| Posts | `POST` | `/v1/api/b-post/posts` |
| Posts | `GET` | `/v1/api/b-post/posts/feed` |
| Posts | `GET/PATCH/DELETE` | `/v1/api/b-post/posts/{postId}` |
| Posts | `GET` | `/v1/api/b-post/posts/by-author/{authorId}` |
| Images | `POST` | `/v1/api/b-post/posts/upload-image` |
| Legacy image | `POST` | `/v1/api/b-post/blog/upload-image` |
| Comments | `GET/POST` | `/v1/api/b-post/posts/{postId}/comments` |
| Comments | `PATCH/DELETE` | `/v1/api/b-post/comments/{commentId}` |
| Likes | `POST/DELETE` | `/v1/api/b-post/posts/{postId}/likes` |
| Friends | `GET` | `/v1/api/b-post/friends` |
| Friends | `POST` | `/v1/api/b-post/friends/requests` |
| Friends | `POST` | `/v1/api/b-post/friends/requests/{id}/accept` |
| Friends | `POST` | `/v1/api/b-post/friends/requests/{id}/decline` |
| Friends | `GET` | `/v1/api/b-post/friends/requests/incoming` |
| Friends | `GET` | `/v1/api/b-post/friends/requests/outgoing` |
| Users | `GET` | `/v1/api/b-post/users/search?q=...` |
| Messages | `GET/POST` | `/v1/api/b-post/conversations` |
| Messages | `GET` | `/v1/api/b-post/conversations/{conversationId}/messages` |
| Messages | `POST` | `/v1/api/b-post/conversations/{conversationId}/read` |
| Messages | `POST` | `/v1/api/b-post/messages` |
| Images | `POST` | `/v1/api/b-post/messages/upload-image` |
| Notifications | `GET` | `/v1/api/b-post/notifications` |
| Notifications | `GET` | `/v1/api/b-post/notifications/unread-count` |
| Notifications | `POST` | `/v1/api/b-post/notifications/{id}/read` |
| Presence | `GET` | `/v1/api/b-post/presence/online` |
| WebSocket | `STOMP` | SockJS endpoint `/v1/api/b-post/ws`, app destinations `/app/message.send` และ `/app/message.read` |

## Environment Variables

คัดลอก `.env.example` เป็น `.env` สำหรับ Docker Compose/root backend env แล้วใส่ค่าจริง

```powershell
Copy-Item .env.example .env
```

ค่าหลักที่ backend/gateway ใช้:

| Variable | ใช้โดย |
| --- | --- |
| `SUPABASE_DB_USERNAME` | Spring datasource username |
| `SUPABASE_DB_PASSWORD` | Spring datasource password |
| `SUPABASE_URL` | Supabase REST/Storage base URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase Storage upload |
| `GROK_API_KEY` | Spring AI/Groq client |
| `HUGGINGFACE_API_KEY` | Embedding service |
| `SUPABASE_PROJECT_ID` | OAuth2 Proxy issuer config |
| `OAUTH2_PROXY_CLIENT_ID` | OAuth2 Proxy |
| `OAUTH2_PROXY_CLIENT_SECRET` | OAuth2 Proxy |
| `OAUTH2_PROXY_COOKIE_SECRET` | OAuth2 Proxy cookie secret |
| `CORS_ALLOWED_ORIGIN_GATEWAY` | allowed origin ของ backend services |

Frontend local development ใช้ `frontend/.env.local`

```env
NEXT_PUBLIC_SUPABASE_URL=https://<project-id>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon-or-publishable-key>
NEXT_PUBLIC_SITE_URL=http://localhost:3000
NEXT_PUBLIC_AUTH_REDIRECT_URL=http://localhost:3000

# ปล่อยว่างเพื่อใช้ relative URLs + Next.js rewrites ใน dev
NEXT_PUBLIC_API_URL=

# optional: ให้ user-service ยิง gateway/prod ตรงแทน dev rewrite
NEXT_PUBLIC_USER_API_URL=

# dev rewrite targets ใน frontend/next.config.ts
BACKEND_USER_URL=http://localhost:8080
BACKEND_CHAT_URL=http://localhost:8081
BACKEND_BPOST_URL=http://localhost:8082
```

สำหรับ production Docker image ของ frontend ค่า `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` และ `NEXT_PUBLIC_SITE_URL` ต้องถูกส่งเป็น Docker build args เพราะ Next.js bake ค่า `NEXT_PUBLIC_*` ตอน build

## วิธีรันด้วย Docker Compose

```powershell
Copy-Item .env.example .env
# แก้ .env ให้ครบก่อน
docker compose up -d --build
```

เปิดแอปผ่าน gateway ที่ `http://localhost`

ข้อควรรู้:

- Compose นี้รันเฉพาะ app containers, gateway และ oauth2-proxy; ไม่ได้สร้าง local Postgres/Supabase และปัจจุบันไม่มี Redis service ใน compose
- Docker Compose local ให้ `bpost-service` ฟัง port `8082`; บน ACA workflow override กลับไป `8080`; แล้วให้ Nginx route ด้วย path prefix
- Frontend Docker build ต้องมี Supabase public env เป็น build args ถ้าต้องการ auth ใช้งานจริงใน image

## วิธีรันแบบ Local Development

ติดตั้ง shared library ก่อน เพราะหลาย service depend กับ `common-auth`

```powershell
cd backend\chatapp
.\mvnw.cmd -f ..\common-auth\pom.xml install -DskipTests
```

รัน backend ด้วย profile `local` เพื่อใช้ port แยก:

```powershell
cd backend\user
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# -> http://localhost:8080

cd backend\chatapp
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# -> http://localhost:8081

cd backend\bpost
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# -> http://localhost:8082
```

รัน frontend:

```powershell
cd frontend
npm install
npm run dev
# -> http://localhost:3000
```

ใน dev, `frontend/next.config.ts` จะ rewrite:

- `/v1/api/user/*` -> `BACKEND_USER_URL` หรือ `http://localhost:8080`
- `/v1/api/chat-app/*` -> `BACKEND_CHAT_URL` หรือ `http://localhost:8081`
- `/v1/api/b-post/*` -> `BACKEND_BPOST_URL` หรือ `http://localhost:8082`

## Build และ Test

Frontend:

```powershell
cd frontend
npm run build
npm run test
```

Backend:

```powershell
cd backend\chatapp
.\mvnw.cmd -f ..\common-auth\pom.xml test

.\mvnw.cmd test
```


## สถานะปัจจุบัน

สิ่งที่มีในโค้ดแล้ว:

- Supabase OAuth login, callback และ protected routes ผ่าน Next middleware/provider
- User sync/profile service แยกจาก chat service
- Gateway เปิด `auth_request` สำหรับ REST APIs และปล่อย WebSocket ให้ตรวจ JWT ใน STOMP layer
- AI chat ที่บันทึก history, สร้าง room, ใช้ internal user จาก JWT และเรียก Groq ผ่าน Resilience4j
- HuggingFace embedding response รองรับ nested array แล้ว และบันทึกลง pgvector
- B-Post feed, comments, likes, friends, notifications, conversations, image upload และ realtime presence/message hooks
- Dockerfiles for frontend, user, chatapp, bpost, and gateway
- GitHub Actions deploy แยกแต่ละ container app ไป Azure Container Apps

ข้อจำกัด/งานค้างที่เห็นจากโค้ด:

- `/chat-app/social` ยังไม่มี page implementation
- Docker Compose ไม่ได้ provision local database หรือ Supabase emulator
- Frontend image ต้องการ `NEXT_PUBLIC_*` build args สำหรับ auth จริง
- `common-auth` decode JWT payload โดยไม่ verify signature เอง เพราะ trust boundary อยู่ที่ gateway/oauth2-proxy; ถ้ายิง backend ตรงต้องระวังเรื่องนี้
- มี legacy endpoint บางชุดใน `chatapp` และ `bpost` ที่ยังคงไว้เพื่อ compatibility

## เอกสารเพิ่มเติม

- [Authentication Flow](./note/docs/AUTH.md)
- [ChatApp Feature Design](./note/docs/CHATAPP.md)
- [Infrastructure Setup](./note/docs/INFRA.md)
- [API Keys Configuration](./note/docs/KEYS.md)
- [Supabase Specification](./note/docs/SUPABASE.md)
- [Provider Notes](./note/docs/PROVIDER.md)
- [Pooler Notes](./note/docs/POOLER.md)
- [Bug Report](./note/REPORT.md)
- [Roadmap](./note/ROADMAP.md)
