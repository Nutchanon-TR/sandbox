# ข้อมูลจำเพาะของการทำงานเชื่อมต่อกับ Supabase

เอกสารฉบับนี้อธิบายรายละเอียดเกี่ยวกับลอจิก สถาปัตยกรรม และข้อมูลจำเพาะที่เกี่ยวข้องกับการเชื่อมต่อระบบ Supabase ภายในโปรเจกต์ Sandbox ซึ่งครอบคลุมทั้งในส่วนของ Frontend (Next.js) และ Backend (Spring Boot)

## 1. การเชื่อมต่อส่วนหน้า (Frontend Integration - Next.js)

ฝั่ง Frontend มีการใช้ `@supabase/ssr` เพื่อรองรับการทำ Server-Side Rendering (SSR) ของระบบการยืนยันตัวตน (Authentication) เพื่อความปลอดภัยสูงสุด ร่วมกับการใช้ `@supabase/supabase-js` สำหรับการทำงานที่เกี่ยวข้องกับฝั่งเบราว์เซอร์

### 1.1 Auth Clients (ตัวจัดการฝั่งไคลเอ็นต์)
Supabase จำเป็นต้องอาศัยตัวจัดการเริ่มต้น (client initializers) ที่แตกต่างกันขึ้นอยู่กับบริบทและสถานที่การเรนเดอร์:

- **Browser Client (`utils/supabase/client.ts`)**
  เรียกใช้ฟังก์ชัน `createBrowserClient(URL, ANON_KEY)` ซึ่งจำเป็นต้องพึ่งพาคุกกี้บนเบราว์เซอร์ที่มีการตั้งค่าไว้ก่อนแล้ว มักจะถูกนำไปใช้ในฝั่ง Client Components ขนานแท้
- **Server Client (`utils/supabase/server.ts`)**
  เรียกใช้ฟังก์ชัน `createServerClient(URL, ANON_KEY, { cookies })` เนื่องจากบนฮุคของ Server Components หรือ API Route ไม่สามารถเขียนคุกกี้โดยตรงได้ จึงจำเป็นต้องอ่านและดึงข้อมูลจากการเรียกใช้คำสั่ง `cookies().getAll()` ตามโครงสร้างใหม่ของ Next.js

### 1.2 Auth State Hook (`hooks/useSupabaseSession.ts`)
เป็น Custom React hook (`useSupabaseSession`) ที่สร้างไว้เพื่อแชร์ข้อมูลและตรวจสอบเซสชันผู้ใช้งานเพื่อใช้ร่วมกับไคลเอ็นต์ในหน้าที่ต้องการ
- **Logic (ลอจิกการทำงาน):** ตัว hook จะเรียก `getSession()` ทันทีตอนที่เรนเดอร์คอมโพเนนต์ครั้งแรก และทำการ subscribe ติดตามค่าตัวแปรจากเหตุการณ์ `onAuthStateChange` เพื่อให้คอยฟังว่าโทเค็นหมดอายุหรือมีการเปลี่ยนแปลงการสถานะเข้าสู่ระบบ/ออกจากระบบหรือไม่
- **ค่าที่คืนกลับ:**
  - `data` (เก็บ Session object ของยูสเซอร์ล่าสุด หากไม่มีสิทธิจะคืนค่ากลับมาเป็น `null`)
  - `status` (สถานะการโหลดข้อมูลปัจจุบัน สามารถมีค่าเป็น `"loading" | "authenticated" | "unauthenticated"`)
  - `supabase` (อินสแตนซ์ของ browser client ที่สามารถดึงมาเพื่อนำไปเรียกใช้คำสั่งอื่นๆ หรือ logout ต่อไป)

### 1.3 การจัดการและปกป้องพาธ (`middleware.ts`)
Next.js Middleware จะใช้สำหรับดักจับการรีเควสต์ทุกครั้งก่อนโหลดหน้าใหม่ (Edge) เพื่อปกป้อง Private Routes จากเข้าถึงที่ไม่ได้รับอนุญาต
- **Session Refresh:** อ่านตรวจสอบคุกกี้ เรียกใช้งาน `createServerClient` ดักอิมพลีเมนต์การรีเควสต์ แล้วต่ออายุค่าเซสชันและทำการรันคำสั่ง `supabase.auth.getUser()`
- **Protected Paths:** ทุกๆ พาธการใช้งานภายในระบบ ยกเว้นหน้า `/login`, `/auth`, และ `/` จะเป็นพื้นที่ป้องกัน หากผู้ใช้งานที่ไม่ได้ล็อกอินหลงเข้ามาจะถูก redirect ไปยังเตะไปที่หน้า `/login`
- **Guest Paths:** หากผู้ใช้งานที่ทำการล็อกอินไว้แล้ว พยายามจะเข้าหน้าระบบเชิญอย่าง `/login` ระบบจะอัตโนมัติ redirect พาผู้ใช้งานย้อนไปที่หน้า `/` ทันที

### 1.4 OAuth Callback (`app/auth/callback/route.ts`)
มักถูกใช้งานเป็น endpoint API โดยเฉพาะที่ใช้ยืนยันจัดการรหัสผ่านประเภท PKCE เพื่อเข้าสู่ระบบ (เช่นกลับมาจาก Google OAuth, Magic Link เป็นต้น) 
- **ข้อควรรู้เรื่องชื่อ Route:** ชื่อโฟลเดอร์ในโปรเจกต์ไม่จำเป็นต้องเป็น `auth/callback` เสมอไป สามารถตั้งชื่อเป็นอะไรก็ได้ตามความเหมาะสม (เช่น `login/verify` หรือ `confirm`) แต่มีข้อแม้คือ **คุณต้องนำ URL พาธที่ตั้งใหม่นี้ ไปอัปเดตช่อง Redirect URI ในหน้าตั้งค่าของ Supabase Dashboard ให้ตรงกันด้วย**
- **ขั้นตอนการทำงาน:**
  - ดึงรหัสพารามิเตอร์ `code` และตัวแปรพาธ `next` (ที่จะทำการ redirect ต่อไป) จาก URL parameters
  - รับไปดำเนินการต่อด้วยคำสั่ง `supabase.auth.exchangeCodeForSession(code)` แลกเป็นเซสชันลงบน Server client
  - กรณีสำเร็จ จะปั๊มเป็นคุกกี้การรับรองล็อกอินอัตโนมัติลงใน Headers แล้วเด้งเปลี่ยนผู้ใช้ไปยังหน้าที่ระบุในสเตตัสพารามิเตอร์ `next`
  - ในกรณีล้มเหลว จะวิ่งไปที่หน้า `/login?error=auth-code-exchange` ทันที

---

## 2. การเชื่อมต่อส่วนหลัง (Backend Integration - Spring Boot)

ฝั่งลอจิกแอปพลิเคชันแบ็กเอนด์ (เช่น Microservice ของ `chat`, `dinner` และ `bpost`) นำฐานข้อมูล Postgres ของทาง Supabase มาเชื่อมกันตรงๆ แบบ Native Connection และพึ่งพากระบวนการร้องขอผ่าน REST API ภายนอกเพื่อย้ายไฟล์แนบเข้าไปยัง Supabase Storage (หน้าที่หลักในส่วนการอัปโหลดนี้คือเซอร์วิส `bpost`)

### 2.1 Database (PostgreSQL)
สืบเนื่องจากตัว Supabase ซัพพอร์ตการต่อเข้าฐานข้อมูล Postgres ดั้งเดิม การตั้งค่าปรับแต่งบนโปรเจ็ค Spring จึงสามารถเรียกใช้การประมวลผลเชื่อมไปยังฐานข้อมูล Supabase ได้ใต้ตัวเชื่อมโปรโตคอล JDBC แบบตรงไปตรงมา
- **Configuration** (`application.yml`):
  ```yaml
  datasource:
    url: jdbc:postgresql://db.xabewjiiewyhhjfekazv.supabase.co:5432/postgres
    username: ${SUPABASE_DB_USERNAME}
    password: ${SUPABASE_DB_PASSWORD}
  ```
- **การประยุกต์ใช้งาน:** การจัดเก็บและปรับปรุงมักจะถูกครอบคุมเอาไว้ทำผ่านตัว Spring Data JPA ร่วมกันกับ Hibernate ซึ่งโหมดสำหรับอัปเดตสกีมาข้อมูลอย่าง `DDL Auto` ก็จะตั้งตัวเลือกเป็น `none` เป็นส่วนใหญ่ เนื่องจากขั้นตอนการแก้ไขสกีมา จะถูกให้ทำผ่านตัวจัดการเครื่องมือฐานข้อมูลเฉพาะอื่นๆ แทน

### 2.2 โกดังเก็บไฟล์/Blobs (`BlobStorageService.java`)
ตัวแบ็กเอนด์จำเป็นที่จะต้องสามารถรับทำหน้าที่เพิ่มอัปโหลดรูปและอื่นๆ ลงโกดังของ Supabase Storage ได้ด้วยเช่นกัน
- **REST Implementation:** ฟีเจอร์นี้ จะแตกต่างจากการพับลิกผ่าน Java SDKs ตรงกันข้ามโดยในระบบของโปรเจกต์นี้จะยิงคำขอ Post ตรงไปยัง API โดยตรงด้วยตัว `RestTemplate` ของ Spring เอง
- **Upload Flow (ขั้นตอนอัปโหลด):**
  - มีการรับไฟล์ที่อัปโหลดและส่งคำขอไปยังฟอร์มที่ระบุไว้ คือ `[SUPABASE_URL]/storage/v1/object/[BUCKET_NAME]/[FILE_NAME]`.
  - เพิ่ม Headers เฉพาะที่ต้องใช้ในการคุ้มครองระบบ คือ:
    - `Authorization: Bearer ${SUPABASE_SERVICE_ROLE_KEY}`
    - `apikey: ${SUPABASE_SERVICE_ROLE_KEY}`
  - และท้ายที่สุดจะส่งผลลัพธ์ย้อนเป็นรูปแบบในชนิด File ByteArray กลับคืนมา (ซึ่งมีที่มาจากความตั้งใจเดิมตอนใช้งานกับตัว Azure มาก่อน)
- **Public URL Generation:**
  - สร้างคืนจำลองสตริง URL ใหม่เป็นรูปแบบหน้าที่พร้อมสำหรับการนำไปเปิดแสดงผลผ่าน CDN:
    `[SUPABASE_URL]/storage/v1/object/public/[BUCKET_NAME]/[FILE_NAME]`

---

## 3. สภาพแวดล้อมระบบที่ต้องใช้งานและตัวแปรตั้งค่า (Environment Variables)

เพื่อให้ตัวระบบและลอจิกดังกล่าวนี้สามารถขับเคลื่อนทำงานให้ได้อย่างเต็มประสิทธิภาพ การตั้งค่ายืนยันในระบบต้องการตัวแปรสภาพแวดล้อมเป็นดังตัวแปรต่างๆ ดังนี้ในทั้งส่วนหน้าและหลังบ้าน:

**Frontend (.env ตัวแปรหลัก):**
- `NEXT_PUBLIC_SUPABASE_URL` (ขึ้นต้นรูปแบบเริ่มด้วย https://xabewjiiewyhhjfekazv.supabase.co)
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`

**Backend (ตัวแปรสำหรับตั้งค่าใน Runtime ของ Spring Boot):**
- `SUPABASE_DB_USERNAME`
- `SUPABASE_DB_PASSWORD`
- `SUPABASE_SERVICE_ROLE_KEY`
