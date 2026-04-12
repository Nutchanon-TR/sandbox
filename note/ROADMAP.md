# V3 Architecture Roadmap Checklist

แผนงานฉบับนี้สรุปสิ่งที่ต้องดำเนินการ (Checklist) เพื่อให้โปรเจกต์บรรลุเป้าหมายตามสถาปัตยกรรม V3 ที่ร่างไว้ใน `v3.mmd` ได้อย่างสมบูรณ์ แบ่งออกเป็น 7 ระยะ (Phases) จากง่ายไปยากเพื่อเตรียมความพร้อมสู่ระดับ Production 🚀

## Phase 1: Ingress Layer & Gateway Security
เป้าหมาย: นำ Nginx และ OAuth2Proxy มาเป็นประตูด่านหน้า (Gateway) จัดการเส้นทางเครือข่ายและความปลอดภัย
- `[x]` **Setup Nginx:** สร้าง `nginx.conf` กำหนด Routing ให้ครบ (`/` วิ่งไป FE, `/v1/api/chat-app/` ไป Chat, `/v1/api/dinner/` ไป Dinner, `/v1/api/b-post/` ไป B-Post)
- `[x]` **Setup OAuth2Proxy:** uncomment `auth_request` ใน nginx.conf.template แล้ว — รอตั้งค่า Secrets (`OAUTH2_PROXY_CLIENT_ID`, `OAUTH2_PROXY_CLIENT_SECRET`, `OAUTH2_PROXY_COOKIE_SECRET`) และ configure JWKS URL ใน oauth2-proxy ให้ชี้ไป Supabase (`/auth/v1/.well-known/jwks.json`) ดูขั้นตอน Manual M2 ใน `REPORT.md`
- `[ ]` **Inject Headers:** ตั้งค่าให้ OAuth2Proxy นำข้อมูลหลัง Validate ผ่าน (เช่น `X-User-Id`, `X-User-Role`) แปะใส่ Header ส่งเข้าไปให้ Microservices ภายใน — nginx.conf พร้อมแล้ว รอ oauth2-proxy ทำงานจริงก่อน
- `[x]` **Update Frontend API:** แก้ไข Base URL ฝั่งหน้าบ้าน (Frontend) ให้วิ่งยิงผ่าน Nginx แทนการยิงตรงไปที่ Backend แยกพอร์ต

## Phase 2: Caching (Redis Integration)
เป้าหมาย: เพิ่มแคช (Cache) เพื่อลดภาระของ Database และลดเวลาในการโหลด (Latency)
- `[x]` **Spin up Redis Container:** เพิ่ม Image Redis เข้ามาในระบบ
- `[x]` **Integrate Redis with Chat Service:** แก้ไข Spring Boot (Chat) ให้ตรวจสอบแคชก่อนโหลดประวัติแชต หาก Cache Miss (ไม่เจอ) จึงค่อยไปดึง Database และนำผลกลับมาเก็บลงแคช
- `[x]` **Integrate Redis with Dinner Service:** นำแคชไปประยุกต์ใช้เพื่อเก็บผลลัพธ์ Supplier Orders ที่ถูกดึงมาบ่อยๆ (เพื่อเสิร์ฟไวขึ้น)
- `[x]` **Redis Security:** เพิ่ม `--requirepass` ให้ Redis และเอา port 6379 ออกจาก host (เข้าถึงได้เฉพาะใน internal network)
- `[x]` **Fix Cache Invalidation:** เปลี่ยนจาก `@CacheEvict(allEntries = true)` เป็น `evictRoomCache(roomId)` ผ่าน `RedisTemplate.keys("chatHistory::{roomId}_*")` — ล้างเฉพาะห้องที่เปลี่ยนแปลง

## Phase 3: Virtual Machine Sandbox & Build
เป้าหมาย: จำลองสภาพแวดล้อมคล้ายจริงและแก้ไขข้อจำกัดการ Build ออฟไลน์ โดยการรันโปรเจกต์บนระบบเซิร์ฟเวอร์จำลอง (Virtual Machine)

> **SA Note:** VM ไม่จำเป็นถ้าข้ามไป ACA เลย — item ที่ยังต้องทำส่วนใหญ่เสร็จแล้ว หรือย้ายไปทำบน ACA ได้โดยตรง

- `[ ]` **Provision a Virtual Machine:** เตรียมและตั้งค่าระบบปฏิบัติการผ่าน VM พร้อมติดตั้งเครื่องมือพื้นฐาน (`Docker`, `Docker Compose`, `Git`) — **Optional** ถ้า deploy ACA โดยตรง
- `[x]` **Create `.env.example`:** สร้างไฟล์ template ของ environment variables ทุกตัวที่จำเป็น รวม OAuth2 Proxy secrets ครบแล้ว
- `[ ]` **Environment Variables Configuration:** เตรียมไฟล์ `.env` ที่จำเป็น — ปัจจุบัน frontend ใช้ Next.js Rewrites proxy (`NEXT_PUBLIC_API_URL` เป็นค่าว่าง) สำหรับ local dev แต่บน VM/ACA ต้องเปลี่ยน `NEXT_PUBLIC_API_URL` ให้ชี้ไปยัง Public IP/domain
- `[x]` **Backend CORS Update:** แก้ไข `WebConfig.java` ใน Spring Boot ทั้งหมด (×3 services) ให้อ่าน `allowedOrigins` จาก `application.yml` แทน hardcode — เพิ่ม domain จริงได้เลยที่ `app.cors.allowedOrigins` (ดู Manual M6 ใน `REPORT.md`)
- `[ ]` **Network & Firewall Setup:** เปิดพอร์ต HTTP (80) และ HTTPS (443) — ถ้าใช้ ACA ทำผ่าน ingress config แทน firewall
- `[ ]` **Build and Test in VM/ACA:** รัน `docker-compose up -d --build` (local) หรือ push to `main` แล้วให้ CI/CD deploy (ACA)

## Phase 4: AI & Vector Database Completeness
เป้าหมาย: เติมเต็มพลังงานขับเคลื่อนแชตตามแผนการค้นหาเวกเตอร์
- `[x]` **Fix Hardcoded User Context:** แก้ Chat UI (`message/page.tsx`) ที่ตอนนี้ hardcode `ROOM_ID=1` และ `USER_ID=1` ให้ดึงค่าจริงจาก Supabase Session แทน (เพิ่ม `/user/resolve` endpoint + `useSupabaseSession` hook)
- `[x]` **Activate `pgvector`:** เปิดและทดสอบ Extension `pgvector` บนฐานข้อมูล Supabase PostgreSQL (SQL อยู่ใน `database/03_pgvector_schema.sql`)
- `[x]` **Fix EmbeddingService Bug:** แก้ `double[]` → `double[][]` ใน `EmbeddingService.java` — HuggingFace feature-extraction คืน nested array `[[...]]` ไม่ใช่ flat array `[...]` ดู REPORT.md Bug B1
- `[ ]` **Implement Vector Search (E2E Test):** ทดสอบ pipeline ครบวงจรหลังแก้ bug — ส่งข้อความ → embed → บันทึก `message_embeddings` → vector search → context ถึง Groq — ใช้ `intfloat/multilingual-e5-small` (384 dim) ผ่าน HuggingFace Inference API
- `[x]` **Connect to Groq:** รับประกันการตั้งค่า API Call สำหรับใช้โมเดล Llama 3 (Groq API) ให้ทนทานต่อ Request ขาดการเชื่อมต่อ (Circuit Breaker) — ใช้ Resilience4j + `GroqAiClient`

## Phase 5: Observability (New Relic)
เป้าหมาย: ระบบตรวจสอบการทำงาน ข้อผิดพลาด และ Performance ในรูปแบบศูนย์กลาง
- `[ ]` **Setup New Relic Account:** สมัครและตั้งค่า License Key เบื้องต้น — Free tier 100 GB/month สมัครด้วย email นักศึกษา ดูขั้นตอนใน Manual M4 ใน `REPORT.md`
- `[ ]` **Backend APM:** ฝัง New Relic Java Agent (`-javaagent`) เข้ากับ Dockerfile ของ `backend/chatapp`, `backend/dinner` และ `backend/bpost`
- `[ ]` **Frontend APM:** ติดตั้งตัวตรวจสอบ New Relic ฝั่งเบราว์เซอร์ และ Next.js Middleware เพื่อติดตาม Traces และ Logs
- `[ ]` **Gateway Logging:** ส่งต่อ Access logs ของ Nginx และ OAuth2Proxy ไปยังหน้า Dashboard New Relic

## Phase 6: Containerization & Cloud Deployment
เป้าหมาย: นำโปรเจกต์ทั้งหมดขึ้นรันบน Azure Container Apps (ACA) และผูก Cloudflare
- `[x]` **Dockerize Everything:** Dockerfile ครบทุก Component แล้ว (`frontend/Dockerfile`, `backend/chatapp/Dockerfile`, `backend/dinner/Dockerfile`, `backend/bpost/Dockerfile`, `gateway/Dockerfile`) + `docker-compose.yml` ที่ root สำหรับ full stack orchestration
- `[ ]` **Azure Container Apps Setup:** เตรียม Resource Group และสร้าง ACA Environment — ดูขั้นตอนใน Manual M1 ใน `REPORT.md` (ใส่ตัวแปรความลับ/DB URL ไว้ใน ACA Built-in Secrets ให้ปลอดภัย)
- `[x]` **Fix ACA Port Mapping:** แก้ไข `aca-deploy.yml` ให้ใช้ port จริง (`FRONTEND_PORT=3000`, `BACKEND_PORT=8080`, `OAUTH2_PROXY_PORT=4180`) เรียบร้อยแล้ว
- `[ ]` **Setup Cloudflare:** เปิดใช้งาน DNS, WAF (Web Application Firewall) และกำจัดการยิงแบบ Rate Limit ก่อนปล่อย Request ไปหา Nginx — ดูขั้นตอนใน Manual M3 ใน `REPORT.md`
- `[x]` **CI Pipeline (GitHub Actions):** `.github/workflows/aca-deploy.yml` — trigger on push to `main`, build Docker image ทุก service แยก job
- `[x]` **CD Pipeline (Rolling Update):** ใช้ `azure/container-apps-deploy-action@v2` deploy อัตโนมัติไป ACA หลัง build เสร็จ — เพิ่ม `needs` ให้ gateway deploy หลังสุด + `minReplicas: 0` / `maxReplicas: 1` (scale-to-zero) เพื่อประหยัด credit

## Phase 7: Advanced Role Management
เป้าหมาย: สร้างระบบและปกป้องฟีเจอร์จากการบริหารสิทธิ์ (Roles) ของ Supabase
- `[ ]` **Define RLS Policies:** จัดการ Row-level Security ภายในฐานข้อมูล Supabase — ดูตัวอย่าง SQL ใน Manual M5 ใน `REPORT.md`
- `[ ]` **Admin Implementation:** ใช้ตัวแปร `app_metadata.role = admin` ที่ฝังใน JWT Token มากรอง Component ในหน้า Frontend และ Backend ให้ใช้งานฟีเจอร์ลับได้เฉพาะบางระดับผู้ใช้งาน

---

## Progress Summary (อัปเดต 2026-04-12)

| Phase | สถานะ | หมายเหตุ |
|-------|--------|----------|
| 1. Ingress Layer | 🟡 บางส่วน | Nginx ✅, OAuth2Proxy uncomment แล้ว ✅, รอ Secrets + JWKS config |
| 2. Caching (Redis) | ✅ เสร็จ | Cache Invalidation แก้เป็น per-room แล้ว ✅ |
| 3. VM Sandbox | 🟡 บางส่วน | `.env.example` ✅, CORS ✅, รอ `NEXT_PUBLIC_API_URL` + build test |
| 4. AI & Vector DB | 🟡 บางส่วน | pgvector ✅, Groq ✅, EmbeddingService bug แก้แล้ว ✅, รอ E2E test |
| 5. Observability | ⬜ ยังไม่เริ่ม | รอสมัคร New Relic (Manual M4) |
| 6. Containerization | 🟡 บางส่วน | Dockerfile ✅, CI/CD ✅, Port fix ✅, Scale-to-zero ✅, รอ Azure setup + Cloudflare |
| 7. Role Management | ⬜ ยังไม่เริ่ม | รอทำ RLS บน Supabase (Manual M5) |
