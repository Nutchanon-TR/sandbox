# V3 Architecture Roadmap Checklist

แผนงานฉบับนี้สรุปสิ่งที่ต้องดำเนินการ (Checklist) เพื่อให้โปรเจกต์บรรลุเป้าหมายตามสถาปัตยกรรม V3 ที่ร่างไว้ใน `v3.mmd` ได้อย่างสมบูรณ์ แบ่งออกเป็น 6 ระยะ (Phases) จากง่ายไปยากเพื่อเตรียมความพร้อมสู่ระดับ Production 🚀

## Phase 1: Ingress Layer & Gateway Security
เป้าหมาย: นำ Nginx และ OAuth2Proxy มาเป็นประตูด่านหน้า (Gateway) จัดการเส้นทางเครือข่ายและความปลอดภัย
- `[ ]` **Setup Nginx:** สร้าง `nginx.conf` กำหนด Routing ให้ครบ (`/` วิ่งไป FE, `/api/chat` ไป Chat, `/api/dinner` ไป Dinner, `/api/bpost` ไป B-Post)
- `[ ]` **Setup OAuth2Proxy:** คอนฟิก OAuth2Proxy เป็น Sidecar คอยดักรับ JWT จาก Request เพื่อนำไป Validate ลายเซ็นต์ที่ Supabase JWKS Endpoint
- `[ ]` **Inject Headers:** ตั้งค่าให้ OAuth2Proxy นำข้อมูลหลัง Validate ผ่าน (เช่น `X-User-Id`, `X-User-Role`) แปะใส่ Header ส่งเข้าไปให้ Microservices ภายใน
- `[ ]` **Update Frontend API:** แก้ไข Base URL ฝั่งหน้าบ้าน (Frontend) ให้วิ่งยิงผ่าน Nginx แทนการยิงตรงไปที่ Backend แยกพอร์ต 

## Phase 2: Caching (Redis Integration)
เป้าหมาย: เพิ่มแคช (Cache) เพื่อลดภาระของ Database และลดเวลาในการโหลด (Latency)
- `[x]` **Spin up Redis Contianer:** เพิ่ม Image Redis เข้ามาในระบบ 
- `[x]` **Integrate Redis with Chat Service:** แก้ไข Spring Boot (Chat) ให้ตรวจสอบแคชก่อนโหลดประวัติแชต หาก Cache Miss (ไม่เจอ) จึงค่อยไปดึง Database และนำผลกลับมาเก็บลงแคช
- `[x]` **Integrate Redis with Dinner Service:** นำแคชไปประยุกต์ใช้เพื่อเก็บผลลัพธ์ Supplier Orders ที่ถูกดึงมาบ่อยๆ (เพื่อเสิร์ฟไวขึ้น)

## Phase 2.5: Virtual Machine Sandbox & Build
เป้าหมาย: จำลองสภาพแวดล้อมคล้ายจริงและแก้ไขข้อจำกัดการ Build ออฟไลน์ โดยการรันโปรเจกต์บนระบบเซิร์ฟเวอร์จำลอง (Virtual Machine)
- `[ ]` **Provision a Virtual Machine:** เตรียมและตั้งค่าระบบปฏิบัติการผ่าน VM พร้อมติดตั้งเครื่องมือพื้นฐาน (`Docker`, `Docker Compose`, `Git`)
- `[ ]` **Environment Variables Configuration:** เตรียมไฟล์ `.env` ที่จำเป็น (เช่น ตัวแปร Supabase) และ **สำคัญที่สุดคือการเปลี่ยน `NEXT_PUBLIC_API_URL` ให้ชี้ไปยัง Public IP ของ VM แทนที่จะเป็น `localhost`** เพื่อให้เบราว์เซอร์ผู้ใช้ยิง Request เข้า Nginx บน VM ได้ถูกต้อง
- `[ ]` **Backend CORS Update:** แก้ไขไฟล์ `application.yml` ใน Spring Boot ทั้งหมดโดยเพิ่ม Public IP/Domain ของ VM เข้าไปใน `allowedOrigins` เพื่อป้องกันสิทธิ์การเข้าถึง (CORS Error)
- `[ ]` **Network & Firewall Setup:** เปิดพอร์ต (Port Forwarding / Inbound Rules) ยกตัวอย่างเช่น HTTP (80) และ HTTPS (443) บน Firewall ของผู้ให้บริการ VM
- `[ ]` **Build and Test in VM:** โคลนโค้ดลง VM และนำร่องประมวลผลคำสั่ง `docker-compose up -d --build` เพื่อรัน Production-like Environment

## Phase 3: AI & Vector Database Completeness
เป้าหมาย: เติมเต็มพลังงานขับเคลื่อนแชตตามแผนการค้นหาเวกเตอร์
- `[ ]` **Activate `pgvector`:** เปิดและทดสอบ Extension `pgvector` บนฐานข้อมูล Supabase PostgreSQL
- `[ ]` **Implement Vector Search:** เขียนฟีเจอร์สำหรับค้นหาเนื้อหาหรือบริบทแบบ Vector ใน Chat Service
- `[ ]` **Connect to Groq:** รับประกันการตั้งค่า API Call สำหรับใช้โมเดล Llama 3 (Groq API) ให้ทนทานต่อ Request ขาดการเชื่อมต่อ (Circuit Breaker)

## Phase 4: Observability (New Relic)
เป้าหมาย: ระบบตรวจสอบการทำงาน ข้อผิดพลาด และ Performance ในรูปแบบศูนย์กลาง 
- `[ ]` **Setup New Relic Account:** สมัครและตั้งค่า License Key เบื้องต้น
- `[ ]` **Backend APM:** ฝัง New Relic Agent เข้ากับ `backend/chat`, `backend/dinner` และ `backend/bpost`
- `[ ]` **Frontend APM:** ติดตั้งตัวตรวจสอบ New Relic ฝั่งเบราว์เซอร์ และ Next.js Middleware เพื่อติดตาม Traces และ Logs
- `[ ]` **Gateway Logging:** ส่งต่อ Access logs ของ Nginx และ OAuth2Proxy ไปยังหน้า Dashboard New Relic

## Phase 5: Containerization & Cloud Deployment
เป้าหมาย: นำโปรเจกต์ทั้งหมดขึ้นรันบน Azure Container Apps (ACA) และผูก Cloudflare
- `[ ]` **Dockerize Everything:** เขียนและทดสอบ Dockerfile ของทุกๆ Component ให้ครบถ้วน ทำงานบนระบบ Local แบบจำลองได้แบบไร้รอยต่อ
- `[ ]` **Azure Container Apps Setup:** เตรียม Resource Group และสร้าง ACA Environment (ใส่ตัวแปรความลับ/DB URL ไว้ใน ACA Built-in Secrets ให้ปลอดภัย)
- `[ ]` **Setup Cloudflare:** เปิดใช้งาน DNS, WAF (Web Application Firewall) และกำจัดการยิงแบบ Rate Limit ก่อนปล่อย Request ไปหา Nginx
- `[ ]` **CI Pipeline (GitHub Actions):** ร่างสคริปต์ให้ GHA ดักฟังการอัปเดตโค้ด ทำการ Build และ Push Docker Image ขึ้นไปฝากบน GHCR
- `[ ]` **CD Pipeline (Rolling Update):** ฝังคำสั่งใน GHA ให้ส่งสัญญาณอัปเดตไปหา ACA แบบ Rolling Deploy อัตโนมัติเมื่อมีเวอร์ชันใหม่เสร็จสิ้น

## Phase 6: Advanced Role Management
เป้าหมาย: สร้างระบบและปกป้องฟีเจอร์จากการบริหารสิทธิ์ (Roles) ของ Supabase
- `[ ]` **Define RLS Policies:** จัดการ Row-level Security ภายในฐานข้อมูล Supabase ให้ออกสิทธิ์ตาม Session ล็อกอิน
- `[ ]` **Admin Implementation:** ใช้ตัวแปร `app_metadata.role = admin` ที่ฝังใน JWT Token มากรอง Component ในหน้า Frontend และ Backend ให้ใช้งานฟีเจอร์ลับได้เฉพาะบางระดับผู้ใช้งาน

## Question
- **Nginx แปลกๆ ต้องดักทุกตัวเลยหรอ เราไปดักที่เดียวไม่ได้หรอ**