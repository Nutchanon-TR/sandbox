# V3 Architecture Roadmap Checklist

แผนงานฉบับนี้สรุปสิ่งที่ต้องดำเนินการ (Checklist) เพื่อให้โปรเจกต์บรรลุเป้าหมายตามสถาปัตยกรรม V3 ที่ร่างไว้ใน `v3.mmd` ได้อย่างสมบูรณ์ แบ่งออกเป็น 7 ระยะ (Phases) จากง่ายไปยากเพื่อเตรียมความพร้อมสู่ระดับ Production 🚀

## Phase 1: Ingress Layer & Gateway Security
เป้าหมาย: นำ Nginx และ OAuth2Proxy มาเป็นประตูด่านหน้า (Gateway) จัดการเส้นทางเครือข่ายและความปลอดภัย
- `[x]` **Setup Nginx:** สร้าง `nginx.conf` กำหนด Routing ให้ครบ (`/` วิ่งไป FE, `/v1/api/chat-app/` ไป Chat, `/v1/api/dinner/` ไป Dinner, `/v1/api/b-post/` ไป B-Post)
- `[ ]` **Setup OAuth2Proxy:** คอนฟิก OAuth2Proxy เป็น Sidecar คอยดักรับ JWT จาก Request เพื่อนำไป Validate ลายเซ็นต์ที่ Supabase JWKS Endpoint (ตอนนี้ auth_request ยัง comment อยู่)
- `[ ]` **Inject Headers:** ตั้งค่าให้ OAuth2Proxy นำข้อมูลหลัง Validate ผ่าน (เช่น `X-User-Id`, `X-User-Role`) แปะใส่ Header ส่งเข้าไปให้ Microservices ภายใน
- `[x]` **Update Frontend API:** แก้ไข Base URL ฝั่งหน้าบ้าน (Frontend) ให้วิ่งยิงผ่าน Nginx แทนการยิงตรงไปที่ Backend แยกพอร์ต

## Phase 2: Caching (Redis Integration)
เป้าหมาย: เพิ่มแคช (Cache) เพื่อลดภาระของ Database และลดเวลาในการโหลด (Latency)
- `[x]` **Spin up Redis Container:** เพิ่ม Image Redis เข้ามาในระบบ
- `[x]` **Integrate Redis with Chat Service:** แก้ไข Spring Boot (Chat) ให้ตรวจสอบแคชก่อนโหลดประวัติแชต หาก Cache Miss (ไม่เจอ) จึงค่อยไปดึง Database และนำผลกลับมาเก็บลงแคช
- `[x]` **Integrate Redis with Dinner Service:** นำแคชไปประยุกต์ใช้เพื่อเก็บผลลัพธ์ Supplier Orders ที่ถูกดึงมาบ่อยๆ (เพื่อเสิร์ฟไวขึ้น)
- `[x]` **Redis Security:** เพิ่ม `--requirepass` ให้ Redis และเอา port 6379 ออกจาก host (เข้าถึงได้เฉพาะใน internal network)

> **⚠️ Warning — Cache Invalidation ยังหยาบ:**
> `ChatService.getAiResponse()` ใช้ `@CacheEvict(value = "chatHistory", allEntries = true)` ซึ่งจะล้าง cache ของ **ทุกห้อง** เมื่อมีข้อความใหม่ในห้องใดห้องหนึ่ง ตอนนี้พอรับได้เพราะเป็น 1 user : 1 room แต่ถ้า scale ขึ้นต้องเปลี่ยนเป็นล้างเฉพาะ room ที่เปลี่ยนแปลง (ใช้ `RedisTemplate` ลบ key ด้วย pattern `chatHistory::roomId_*` แทน)

## Phase 3: Virtual Machine Sandbox & Build
เป้าหมาย: จำลองสภาพแวดล้อมคล้ายจริงและแก้ไขข้อจำกัดการ Build ออฟไลน์ โดยการรันโปรเจกต์บนระบบเซิร์ฟเวอร์จำลอง (Virtual Machine)
- `[ ]` **Provision a Virtual Machine:** เตรียมและตั้งค่าระบบปฏิบัติการผ่าน VM พร้อมติดตั้งเครื่องมือพื้นฐาน (`Docker`, `Docker Compose`, `Git`)
- `[ ]` **Create `.env.example`:** สร้างไฟล์ template ของ environment variables ทุกตัวที่จำเป็น (ไม่ใส่ค่าจริง) เพื่อให้คนใหม่รู้ว่าต้องกำหนดค่าอะไรบ้าง (`SUPABASE_DB_USERNAME`, `REDIS_PASSWORD`, `GROK_API_KEY` ฯลฯ)
- `[ ]` **Environment Variables Configuration:** เตรียมไฟล์ `.env` ที่จำเป็น (เช่น ตัวแปร Supabase) — ปัจจุบัน frontend ใช้ Next.js Rewrites proxy (`NEXT_PUBLIC_API_URL` เป็นค่าว่าง) สำหรับ local dev แต่บน VM ต้องเปลี่ยน `NEXT_PUBLIC_API_URL` ให้ชี้ไปยัง Public IP ของ VM (หรือ domain) เพื่อให้เบราว์เซอร์ยิง Request เข้า Nginx บน VM ได้ถูกต้อง
- `[ ]` **Backend CORS Update:** แก้ไขไฟล์ `application.yml` ใน Spring Boot ทั้งหมดโดยเพิ่ม Public IP/Domain ของ VM เข้าไปใน `allowedOrigins` เพื่อป้องกันสิทธิ์การเข้าถึง (CORS Error)
- `[ ]` **Network & Firewall Setup:** เปิดพอร์ต (Port Forwarding / Inbound Rules) ยกตัวอย่างเช่น HTTP (80) และ HTTPS (443) บน Firewall ของผู้ให้บริการ VM
- `[ ]` **Build and Test in VM:** โคลนโค้ดลง VM และนำร่องประมวลผลคำสั่ง `docker-compose up -d --build` เพื่อรัน Production-like Environment

## Phase 4: AI & Vector Database Completeness
เป้าหมาย: เติมเต็มพลังงานขับเคลื่อนแชตตามแผนการค้นหาเวกเตอร์
- `[x]` **Fix Hardcoded User Context:** แก้ Chat UI (`message/page.tsx`) ที่ตอนนี้ hardcode `ROOM_ID=1` และ `USER_ID=1` ให้ดึงค่าจริงจาก Supabase Session แทน (เพิ่ม `/user/resolve` endpoint + `useSupabaseSession` hook)
- `[x]` **Activate `pgvector`:** เปิดและทดสอบ Extension `pgvector` บนฐานข้อมูล Supabase PostgreSQL (SQL อยู่ใน `database/03_pgvector_schema.sql`)
- `[ ]` **Implement Vector Search:** เขียนฟีเจอร์สำหรับค้นหาเนื้อหาหรือบริบทแบบ Vector ใน Chat Service — ตัดสินใจใช้ `intfloat/multilingual-e5-small` (384 dim, รองรับไทย+อังกฤษ) เรียกผ่าน HuggingFace Inference API, embed ตอน insert message แล้ว query ด้วย cosine similarity (`<=>`) เพื่อส่ง context ให้ Groq — **โค้ด `EmbeddingService` เขียนแล้วแต่มี bug: `double[]` ควรเป็น `double[][]` ทำให้ embedding ไม่ทำงาน (ดู `note/REPORT.md`)**
- `[x]` **Connect to Groq:** รับประกันการตั้งค่า API Call สำหรับใช้โมเดล Llama 3 (Groq API) ให้ทนทานต่อ Request ขาดการเชื่อมต่อ (Circuit Breaker) — ใช้ Resilience4j + `GroqAiClient`

## Phase 5: Observability (New Relic)
เป้าหมาย: ระบบตรวจสอบการทำงาน ข้อผิดพลาด และ Performance ในรูปแบบศูนย์กลาง
- `[ ]` **Setup New Relic Account:** สมัครและตั้งค่า License Key เบื้องต้น
- `[ ]` **Backend APM:** ฝัง New Relic Agent เข้ากับ `backend/chatapp`, `backend/dinner` และ `backend/bpost`
- `[ ]` **Frontend APM:** ติดตั้งตัวตรวจสอบ New Relic ฝั่งเบราว์เซอร์ และ Next.js Middleware เพื่อติดตาม Traces และ Logs
- `[ ]` **Gateway Logging:** ส่งต่อ Access logs ของ Nginx และ OAuth2Proxy ไปยังหน้า Dashboard New Relic

## Phase 6: Containerization & Cloud Deployment
เป้าหมาย: นำโปรเจกต์ทั้งหมดขึ้นรันบน Azure Container Apps (ACA) และผูก Cloudflare
- `[x]` **Dockerize Everything:** Dockerfile ครบทุก Component แล้ว (`frontend/Dockerfile`, `backend/chatapp/Dockerfile`, `backend/dinner/Dockerfile`, `backend/bpost/Dockerfile`, `gateway/Dockerfile`) + `docker-compose.yml` ที่ root สำหรับ full stack orchestration
- `[ ]` **Azure Container Apps Setup:** เตรียม Resource Group และสร้าง ACA Environment (ใส่ตัวแปรความลับ/DB URL ไว้ใน ACA Built-in Secrets ให้ปลอดภัย)
- `[ ]` **Fix ACA Port Mapping:** แก้ไข `aca-deploy.yml` ให้ใช้ port จริง (frontend=3000, backends=8080, oauth2-proxy=4180) — ปัจจุบัน gateway ยังส่ง `FRONTEND_PORT=80 BACKEND_PORT=80 OAUTH2_PROXY_PORT=80` ซึ่งผิด เพราะ ACA ใช้ internal FQDN แทน Docker network
- `[ ]` **Setup Cloudflare:** เปิดใช้งาน DNS, WAF (Web Application Firewall) และกำจัดการยิงแบบ Rate Limit ก่อนปล่อย Request ไปหา Nginx
- `[x]` **CI Pipeline (GitHub Actions):** `.github/workflows/aca-deploy.yml` — trigger on push to `main`, build Docker image ทุก service แล้ว push ขึ้น GHCR
- `[x]` **CD Pipeline (Rolling Update):** ใช้ `azure/container-apps-deploy-action@v2` deploy อัตโนมัติไป ACA หลัง build เสร็จ (ต้องตั้ง GitHub Secrets: `AZURE_CREDENTIALS`, `RESOURCE_GROUP` ก่อนใช้งานจริง)

## Phase 7: Advanced Role Management
เป้าหมาย: สร้างระบบและปกป้องฟีเจอร์จากการบริหารสิทธิ์ (Roles) ของ Supabase
- `[ ]` **Define RLS Policies:** จัดการ Row-level Security ภายในฐานข้อมูล Supabase ให้ออกสิทธิ์ตาม Session ล็อกอิน
- `[ ]` **Admin Implementation:** ใช้ตัวแปร `app_metadata.role = admin` ที่ฝังใน JWT Token มากรอง Component ในหน้า Frontend และ Backend ให้ใช้งานฟีเจอร์ลับได้เฉพาะบางระดับผู้ใช้งาน

---

## Progress Summary (อัปเดต 2026-04-12)

| Phase | สถานะ | หมายเหตุ |
|-------|--------|----------|
| 1. Ingress Layer | 🟡 บางส่วน | Nginx ✅, OAuth2Proxy ยัง comment อยู่ |
| 2. Caching (Redis) | ✅ เสร็จ | Cache Invalidation ยังหยาบ (evict all) |
| 3. VM Sandbox | ⬜ ยังไม่เริ่ม | โค้ดพร้อม, รอเตรียม VM |
| 4. AI & Vector DB | 🟡 บางส่วน | pgvector ✅, Groq ✅, EmbeddingService มี bug |
| 5. Observability | ⬜ ยังไม่เริ่ม | รอสมัคร New Relic |
| 6. Containerization | 🟡 บางส่วน | Dockerfile ✅, CI/CD ✅, รอ Azure + แก้ port |
| 7. Role Management | ⬜ ยังไม่เริ่ม | — |
