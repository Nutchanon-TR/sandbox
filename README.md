# Sandbox

โปรเจกต์นี้เป็น sandbox สำหรับลองฟีเจอร์หลายแบบในสถาปัตยกรรมแบบ microservice โดยแยก concern ระหว่าง frontend, backend service และ data layer ให้ชัด เพื่อใช้ทดลอง flow จริงตั้งแต่ login, dashboard, chat กับ AI ไปจนถึงการเชื่อมต่อ Supabase

> Architecture Diagrams อยู่ที่ `note/arch/v3/` (SVG + Mermaid)

## จุดประสงค์ของโปรเจกต์

- ทดลองออกแบบระบบแบบ microservice แม้ขนาดโปรเจกต์ยังเล็ก
- ทดลอง frontend dashboard ด้วย Next.js + Ant Design
- ทดลอง authentication ด้วย Supabase OAuth
- ทดลอง backend API ด้วย Spring Boot + JPA
- ทดลอง AI chat flow ผ่าน Groq API (Llama 3.3) + Resilience4j Circuit Breaker
- ทดลอง vector search ด้วย pgvector + HuggingFace Embedding
- ทดลอง object storage ผ่าน Supabase Storage
- ทดลอง API Gateway ด้วย Nginx + OAuth2 Proxy
- ทดลอง caching ด้วย Redis

## สถาปัตยกรรมโดยรวม

ระบบถูกแบ่งเป็น 6 ส่วนหลัก

1. **`frontend/`** — Next.js App Router สำหรับ UI, route protection, theme, breadcrumb และเรียก backend ผ่าน Next.js Rewrites (proxy)

2. **`backend/chatapp/`** — Spring Boot service สำหรับ chat history, AI response (Groq), vector search (pgvector + HuggingFace), room management และ user profile

3. **`backend/dinner/`** — Spring Boot service สำหรับ supplier order dashboard + Redis cache

4. **`backend/bpost/`** — Spring Boot service สำหรับอัปโหลดรูปภาพขึ้น Supabase Storage

5. **`gateway/`** — Nginx reverse proxy เป็น single entry point (port 80) + OAuth2 Proxy sidecar สำหรับ JWT validation

6. **`note/`** — เอกสาร, architecture diagrams, bug report และ roadmap

## Tech Stack

- **Frontend:** Next.js 15, React 19, TypeScript, Ant Design, Tailwind, Axios
- **Auth:** Supabase Auth (OAuth)
- **Backend:** Spring Boot 3, Spring Data JPA
- **AI:** Groq API (Llama 3.3 70b) ผ่าน `GroqAiClient` + Resilience4j Circuit Breaker
- **Embedding:** HuggingFace Inference API (`intfloat/multilingual-e5-small`, 384 dim)
- **Database:** PostgreSQL บน Supabase + pgvector extension
- **Cache:** Redis 7.2 (Alpine) — ใช้กับ ChatApp และ Dinner
- **File Storage:** Supabase Storage
- **Gateway:** Nginx 1.25 + OAuth2 Proxy v7.6
- **Containerization:** Docker + Docker Compose
- **CI/CD:** GitHub Actions → GHCR → Azure Container Apps

## Service Boundary

### 1. Frontend

หน้าที่หลัก

- แสดง UI ทั้งระบบ
- ตรวจ session ด้วย Supabase
- redirect ผู้ใช้ที่ยังไม่ login ไป `/login`
- เรียก backend API ผ่าน Next.js Rewrites (proxy `/v1/api/*` ไป backend)
- มี context กลางสำหรับ theme, notification, loading และ breadcrumb

หน้าใช้งานหลักในปัจจุบัน

- `/login` — OAuth login
- `/` — Home
- **B-Post:** `/b-post/blog` (อัปโหลดรูป), `/b-post/socials`, `/b-post/messages`
- **Dinner:** `/dinner/supplier` (supplier order dashboard)
- **Chat App:** `/chat-app/message` (chat กับ AI, มี sub-sidebar เลือกห้อง), `/chat-app/social`

*(บางหน้าอาจจะยังเป็น placeholder รอการพัฒนาในอนาคต)*

### 2. Chat Service (`backend/chatapp`)

รับผิดชอบ

- จัดการห้องแชต (สร้าง, ดึงรายการ)
- ดึงประวัติแชตตาม room (pagination)
- รับข้อความจาก user, บันทึก, สร้าง embedding แล้วค้นหา context ด้วย vector search
- โหลด system prompt จากตาราง `chat.ai_context`
- ส่ง prompt + context ไป Groq API (Llama 3.3)
- บันทึกคำตอบของ AI กลับลงฐานข้อมูล
- Resolve Supabase UID → app user
- จัดการ user profile

endpoint หลัก

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/v1/api/chat-app/message/history/{roomId}` | โหลดประวัติแชต |
| `POST` | `/v1/api/chat-app/message` | ส่งข้อความและรับคำตอบจาก AI |
| `GET` | `/v1/api/chat-app/room/list/{userId}` | ดึงรายการห้องของ user |
| `POST` | `/v1/api/chat-app/room/create/{userId}` | สร้างห้องแชตใหม่ |
| `POST` | `/v1/api/chat-app/user/resolve` | Resolve Supabase UID → app user |
| `GET` | `/v1/api/chat-app/profile/{supabaseUid}` | ดึง user profile |
| `POST` | `/v1/api/chat-app/profile` | สร้าง/อัปเดต user profile |

### 3. Dinner Service (`backend/dinner`)

รับผิดชอบ

- อ่านข้อมูล supplier order จาก schema `dinner`
- ทำ pagination จาก query parameter
- Redis cache สำหรับ supplier orders ที่ถูกดึงบ่อย

endpoint หลัก

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/v1/api/dinner/supplier/inquiry?page=1&size=10` | โหลด supplier orders แบบแบ่งหน้า |

### 4. B-Post Service (`backend/bpost`)

รับผิดชอบ

- รับฝากอัปโหลดไฟล์ภาพขึ้น Supabase Storage ผ่าน service role key

endpoint หลัก

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/v1/api/b-post/blog/upload-image` | อัปโหลดรูปภาพไป Supabase Storage |

### 5. Gateway (`gateway/`)

- Nginx เป็น single entry point (port 80)
- Routing: `/` → frontend, `/v1/api/chat-app/` → chat-service, `/v1/api/dinner/` → dinner-service, `/v1/api/b-post/` → bpost-service
- OAuth2 Proxy sidecar สำหรับ JWT validation (ยัง comment อยู่ใน nginx.conf.template)

## End-to-End Flow

### Flow 1: Login และการกัน route

1. ผู้ใช้เข้า `/login`
2. frontend เรียก `supabase.auth.signInWithOAuth(...)`
3. Supabase redirect กลับมาที่ `/auth/callback`
4. route callback แลก code เป็น session
5. `middleware.ts` ตรวจ user จาก cookie/session
6. ถ้ายังไม่ login และพยายามเข้า route ที่ protected จะถูก redirect ไป `/login`
7. หลัง login แล้ว หน้า `/profile` จะแสดงข้อมูลจาก Supabase session โดยตรง

### Flow 2: Supplier Dashboard

1. ผู้ใช้เปิด `/dinner/supplier`
2. หน้า React เรียก `GET /v1/api/dinner/supplier/inquiry`
3. backend ตรวจ Redis cache ก่อน — ถ้า hit ส่งกลับทันที
4. ถ้า cache miss: query database, join `dinner.suppliers` กับ `dinner.orders`, cache ผลลัพธ์
5. frontend แสดงผลเป็น table, filter, search และ stat card

### Flow 3: AI Chat

1. ผู้ใช้เปิด `/chat-app/message`
2. frontend resolve Supabase UID → app user ผ่าน `/user/resolve`
3. frontend โหลด room list ผ่าน `/room/list/{userId}`
4. เมื่อเลือกห้อง โหลด history ด้วย `/message/history/{roomId}`
5. เมื่อผู้ใช้ส่งข้อความ frontend ยิง `POST /message`
6. backend บันทึกข้อความของ user ลงตาราง `chat.messages`
7. backend สร้าง embedding ผ่าน HuggingFace API แล้วบันทึกลง `chat.message_embeddings`
8. backend ค้นหา context ที่เกี่ยวข้องด้วย vector similarity search (cosine)
9. backend โหลด system prompt จาก `chat.ai_context`
10. backend รวม system prompt + context + chat history เป็น prompt เดียว
11. backend เรียก Groq API (Llama 3.3) ผ่าน Resilience4j Circuit Breaker
12. backend บันทึกข้อความตอบกลับของ AI ลงฐานข้อมูล
13. frontend แสดง reply ในหน้าจอ chat

### Flow 4: Image Upload (B-Post)

1. client ส่ง multipart file ไปที่ `/v1/api/b-post/blog/upload-image`
2. backend สร้างชื่อไฟล์ใหม่ด้วย UUID
3. backend ใช้ service role key ยิง REST ไป Supabase Storage
4. backend ส่งผลลัพธ์กลับ

## โครงสร้างโฟลเดอร์

```text
Sandbox/
|- frontend/                 Next.js app (standalone build)
|- backend/
|  |- bpost/                 Storage image service
|  |- chatapp/               Chat + AI + vector search service
|  \- dinner/                Supplier order service
|- gateway/
|  |- Dockerfile             Nginx image
|  \- nginx.conf.template    Routing config (envsubst)
|- note/
|  |- arch/v3/               Architecture diagrams (SVG + Mermaid)
|  |- docs/                  AUTH, CHATAPP, INFRA, KEYS, SUPABASE docs
|  |- REPORT.md              Bug scan report
|  \- ROADMAP.md             V3 Architecture checklist
|- docker-compose.yml        Full stack orchestration
|- .env                      Environment variables (not committed)
\- .github/workflows/
   \- aca-deploy.yml         CI/CD: Build → GHCR → Azure Container Apps
```

## Environment Variables

### Frontend (`frontend/.env.local`)

```env
NEXT_PUBLIC_SUPABASE_URL=https://xxx.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=...
NEXT_PUBLIC_API_URL=              # ว่าง — ใช้ Next.js rewrites proxy แทน
BACKEND_URL=http://localhost:8080 # internal proxy target (ไม่ expose ให้ browser)
```

### Backend (`.env` ที่ root — ใช้ร่วมกันผ่าน `env_file` ใน docker-compose)

```env
SUPABASE_DB_USERNAME=...
SUPABASE_DB_PASSWORD=...
SUPABASE_SERVICE_ROLE_KEY=...
GROK_API_KEY=...
REDIS_PASSWORD=...
```

## วิธีรัน

### Docker Compose (แนะนำ)

```bash
# สร้าง .env จาก template แล้วใส่ค่า
docker-compose up -d --build
# เข้าใช้งานที่ http://localhost (ผ่าน Nginx gateway)
```

### Local Development (แยก service)

```bash
# Frontend
cd frontend
npm install
npm run dev
# → http://localhost:3000

# Backend (แต่ละ service)
cd backend/chatapp   # หรือ dinner, bpost
./mvnw spring-boot:run
# → http://localhost:8080
```

> **หมายเหตุ:** ทั้ง 3 backend service ใช้ port 8080 เหมือนกัน ถ้ารัน local พร้อมกันต้องกำหนด `server.port` เพิ่มเอง หรือใช้ Docker Compose ซึ่ง Nginx จะ route ให้อัตโนมัติ

## API Summary

| Area | Method | Path | Purpose |
|------|--------|------|---------|
| Chat | `GET` | `/v1/api/chat-app/message/history/{roomId}` | โหลดประวัติแชต |
| Chat | `POST` | `/v1/api/chat-app/message` | ส่งข้อความและรับคำตอบจาก AI |
| Chat | `GET` | `/v1/api/chat-app/room/list/{userId}` | ดึงรายการห้อง |
| Chat | `POST` | `/v1/api/chat-app/room/create/{userId}` | สร้างห้องแชตใหม่ |
| Chat | `POST` | `/v1/api/chat-app/user/resolve` | Resolve Supabase UID → app user |
| Chat | `GET` | `/v1/api/chat-app/profile/{supabaseUid}` | ดึง user profile |
| Chat | `POST` | `/v1/api/chat-app/profile` | สร้าง/อัปเดต profile |
| Supplier | `GET` | `/v1/api/dinner/supplier/inquiry` | โหลด supplier orders แบบแบ่งหน้า |
| B-Post | `POST` | `/v1/api/b-post/blog/upload-image` | อัปโหลดรูปภาพไป Supabase Storage |

## สถานะปัจจุบันของโปรเจกต์

สิ่งที่ใช้งานได้

- Supabase OAuth login + protected routes
- Supplier dashboard ที่ดึงข้อมูลจริง + Redis cache
- AI chat ที่บันทึก history, สร้างห้อง, resolve user จาก Supabase session
- Groq API integration + Circuit Breaker (Resilience4j)
- pgvector extension + embedding schema พร้อมใช้
- Image upload API ผ่าน B-Post → Supabase Storage
- Nginx gateway routing ครบทุก service
- Docker Compose full stack
- CI/CD pipeline (GitHub Actions → GHCR → Azure Container Apps)
- Next.js standalone build

สิ่งที่ยังต้องเก็บงาน

- **EmbeddingService bug:** `double[]` ควรเป็น `double[][]` — embedding ไม่ทำงาน (ดูรายละเอียดที่ `note/REPORT.md`)
- OAuth2 Proxy JWT validation ยัง comment อยู่ใน nginx config
- ACA deploy port mapping ยังใส่ port 80 ทั้งหมดที่ gateway (ควรเป็น 3000/8080/4180)
- เมนูบางหน้าเป็น placeholder (socials, messages, social)
- ยังไม่มี `.env.example` สำหรับ onboarding คนใหม่

## เอกสารเพิ่มเติม

- [Authentication Flow](./note/docs/AUTH.md)
- [ChatApp Feature Design](./note/docs/CHATAPP.md)
- [Infrastructure Setup](./note/docs/INFRA.md)
- [API Keys Configuration](./note/docs/KEYS.md)
- [Supabase Specification](./note/docs/SUPABASE.md)
- [Bug Report](./note/REPORT.md)
- [V3 Roadmap](./note/ROADMAP.md)
