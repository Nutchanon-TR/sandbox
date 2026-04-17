# ข้อมูลจำเพาะของการทำงานเชื่อมต่อกับ Supabase

เอกสารฉบับนี้อธิบายรายละเอียดเกี่ยวกับลอจิก สถาปัตยกรรม และข้อมูลจำเพาะที่เกี่ยวข้องกับการเชื่อมต่อระบบ Supabase ภายในโปรเจกต์ Sandbox ซึ่งครอบคลุมทั้งในส่วนของ Frontend (Next.js) และ Backend (Spring Boot)

---

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

ฝั่งลอจิกแอปพลิเคชันแบ็กเอนด์ (เช่น Microservice ของ `chat`, `dinner`, `bpost` และ `user`) นำฐานข้อมูล Postgres ของทาง Supabase มาเชื่อมกันตรงๆ แบบ Native Connection และพึ่งพากระบวนการร้องขอผ่าน REST API ภายนอกเพื่อย้ายไฟล์แนบเข้าไปยัง Supabase Storage (หน้าที่หลักในส่วนการอัปโหลดนี้คือเซอร์วิส `bpost`)

> **หมายเหตุ:** `user-service` จัดการ write path ของ `chat.users` และ `users.profiles` — ChatApp คง read-only JPA access ถึง `chat.users` เท่านั้น

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

---

## 4. Database Schema ทั้งหมดในโปรเจกต์

Supabase PostgreSQL ของโปรเจกต์นี้แบ่งออกเป็น **4 schema** ตามความรับผิดชอบของแต่ละ service:

| Schema | เจ้าของ | คำอธิบาย |
|--------|---------|-----------|
| `public` | ทุก service | ข้อมูล user กลาง (auth + profile) |
| `chat` | chatapp BE | ห้องแชต, ข้อความ, AI context, embeddings |
| `dinner` | dinner BE | Supplier และ Orders |
| `users` | chatapp BE | Profile ขยายของ user |

---

### 4.1 Schema: `public`

#### `public.users` — User กลางของทั้งระบบ
```sql
CREATE TABLE public.users (
  id               bigserial       PRIMARY KEY,
  supabase_uid     uuid            NOT NULL UNIQUE,   -- FK → auth.users.id
  display_name     varchar(100)    NOT NULL,
  allowed_services text[]          NOT NULL DEFAULT '{all}',  -- Phase 7: service access control
  created_at       timestamptz     DEFAULT now(),

  CONSTRAINT chk_allowed_services
    CHECK (allowed_services <@ ARRAY['all','chat_app','bpost','dinner','user']::text[])
);
```

> **หมายเหตุ:** `allowed_services` เพิ่มใน Phase 7 — ดูรายละเอียดการ sync กับ JWT ใน `AUTH.md` ส่วน 3

---

### 4.2 Schema: `users`

#### `users.profiles` — Profile ขยายสำหรับ chat service
```sql
CREATE TABLE users.profiles (
  supabase_uid   uuid          PRIMARY KEY,           -- FK → auth.users.id
  username       varchar(50)   NOT NULL,
  email          varchar(100)  NOT NULL UNIQUE,
  role           varchar(20)   NOT NULL DEFAULT 'USER',
  avatar_url     text,
  created_at     timestamptz   DEFAULT now()
);
```

> ใช้ `supabase_uid` เป็น PK โดยตรง ไม่มี serial id แยก — เชื่อม 1:1 กับ `auth.users`

---

### 4.3 Schema: `chat`

#### `chat.users` — Local user reference ใน chat service
```sql
CREATE TABLE chat.users (
  id            bigserial     PRIMARY KEY,
  supabase_uid  uuid          NOT NULL UNIQUE,       -- FK → auth.users.id
  display_name  varchar(100)  NOT NULL,
  created_at    timestamptz   DEFAULT now()
);
```

> แยกจาก `public.users` เพื่อให้ chat schema อิสระ — `supabase_uid` ใช้ resolve user จาก JWT

#### `chat.ai_context` — การตั้งค่า AI ต่อห้อง
```sql
CREATE TABLE chat.ai_context (
  id           bigserial    PRIMARY KEY,
  ai_name      varchar(100) NOT NULL DEFAULT 'AI Assistant',
  system_text  text         NOT NULL,               -- System prompt สำหรับ Groq/Llama
  avatar_url   text
);
```

#### `chat.rooms` — ห้องแชต
```sql
CREATE TABLE chat.rooms (
  id          bigserial     PRIMARY KEY,
  name        varchar(100),                         -- NULL สำหรับ AI room (ใช้ ai_name แทน)
  is_group    boolean       NOT NULL DEFAULT false,
  created_by  bigint        REFERENCES chat.users(id),
  ai_model    varchar(100),                         -- เช่น "llama-3.3-70b-versatile"
  created_at  timestamptz   DEFAULT now()
);
```

#### `chat.room_members` — สมาชิกในห้อง (ทั้ง user และ AI)
```sql
CREATE TABLE chat.room_members (
  id       bigserial PRIMARY KEY,
  room_id  bigint    NOT NULL REFERENCES chat.rooms(id),
  user_id  bigint    REFERENCES chat.users(id),     -- NULL ถ้า AI member
  ai_id    bigint    REFERENCES chat.ai_context(id) -- NULL ถ้า human member
  -- หมายเหตุ: แต่ละแถวต้องมีค่าอย่างใดอย่างหนึ่ง (user_id หรือ ai_id)
);
```

> **Pattern:** AI room มี 1 แถว `user_id = <userId>` + 1 แถว `ai_id = <aiContextId>`
> Group room มีหลายแถว `user_id` ตามจำนวนสมาชิก

#### `chat.messages` — ข้อความในห้องแชต
```sql
CREATE TABLE chat.messages (
  id          bigserial    PRIMARY KEY,
  room_id     bigint       NOT NULL REFERENCES chat.rooms(id),
  sender_id   bigint       REFERENCES chat.users(id),  -- NULL เมื่อ AI เป็นผู้ส่ง
  is_ai       boolean      NOT NULL DEFAULT false,
  content     text         NOT NULL,
  created_at  timestamptz  DEFAULT now()
);
```

> `sender_id = NULL` + `is_ai = true` หมายความว่าข้อความนั้นมาจาก AI

#### `chat.message_embeddings` — Vector embedding ของแต่ละข้อความ
```sql
CREATE TABLE chat.message_embeddings (
  id          bigserial  PRIMARY KEY,
  message_id  bigint     NOT NULL UNIQUE REFERENCES chat.messages(id),
  embedding   vector(384),                          -- pgvector: 384 dim จาก multilingual-e5-small
  created_at  timestamptz DEFAULT now()
);

-- HNSW Index สำหรับ cosine similarity search (สร้างแล้ว)
CREATE INDEX idx_message_embeddings_vector
  ON chat.message_embeddings
  USING hnsw (embedding vector_cosine_ops);
```

---

### 4.4 Schema: `dinner`

#### `dinner.suppliers` — ข้อมูลผู้จัดจำหน่าย
```sql
CREATE TABLE dinner.suppliers (
  supplier_id    integer       PRIMARY KEY,
  supplier_name  nvarchar(100) NOT NULL,
  contact_person nvarchar(50)  NOT NULL,
  phone          varchar(20)   NOT NULL,
  email          varchar(100),
  address        ntext         NOT NULL,
  created_at     datetime      DEFAULT getdate()   -- MS SQL origin: ปัจจุบันใช้ Supabase PostgreSQL
);
```

> **หมายเหตุ:** Entity ใช้ `@Nationalized` และ `getdate()` ซึ่งมาจาก MS SQL Server ต้นทาง — บน PostgreSQL ให้ใช้ `now()` แทนถ้า recreate

#### `dinner.orders` — คำสั่งซื้อ (อ้างอิงจาก join query)
```sql
CREATE TABLE dinner.orders (
  order_id       integer      PRIMARY KEY,
  supplier_id    integer      NOT NULL REFERENCES dinner.suppliers(supplier_id),
  order_date     date,
  delivery_date  date,
  status         varchar,
  notes          text
);
```

> Schema นี้ไม่มี JPA Entity โดยตรง — ใช้ผ่าน native query JOIN ใน `SupplierRepository`

---

## 5. Vector Search & Embedding Pipeline

### 5.1 Extension ที่ต้องเปิดใน Supabase
```sql
CREATE EXTENSION IF NOT EXISTS vector;  -- pgvector สำหรับ vector(384)
```
> ตรวจสอบแล้ว: ติดตั้งแล้วในโปรเจกต์นี้ ✅

### 5.2 Model ที่ใช้
| ส่วน | รายละเอียด |
|------|-----------|
| Model | `intfloat/multilingual-e5-small` |
| Provider | HuggingFace Inference API |
| Endpoint | `https://router.huggingface.co/hf-inference/models/intfloat/multilingual-e5-small/pipeline/feature-extraction` |
| Output dim | 384 |
| Response format | `double[][]` (nested array — `body[0]` คือ vector จริง) |
| Input prefix | `"passage: "` สำหรับ save, `"query: "` สำหรับ search |

### 5.3 Pipeline การทำงาน (E2E)

```
User ส่งข้อความ
    │
    ▼
ChatService.getAiResponse()
    ├─ บันทึก Message → chat.messages
    ├─ EmbeddingService.embedAndSave()
    │       ├─ เรียก HuggingFace API (prefix: "passage: ")
    │       ├─ แปลง double[][] → float[]
    │       └─ INSERT INTO chat.message_embeddings (embedding::vector)
    │
    ├─ EmbeddingService.searchSimilarMessages()
    │       ├─ embed query (prefix: "query: ")
    │       └─ SELECT ... ORDER BY embedding <=> ?::vector LIMIT 5
    │           (cosine distance via HNSW index)
    │
    ├─ สร้าง Prompt: [SystemMessage] + [ContextMessages] + [RecentHistory]
    ├─ GroqAiClient.chat(prompt) → Llama 3.3 70B
    │
    ├─ บันทึก AI reply → chat.messages (is_ai=true, sender_id=NULL)
    └─ EmbeddingService.embedAndSave(aiReply)
```

### 5.4 Vector Search Query
```sql
SELECT me.message_id
FROM chat.message_embeddings me
JOIN chat.messages m ON m.id = me.message_id
WHERE m.room_id = ?
ORDER BY me.embedding <=> ?::vector   -- <=> คือ cosine distance operator
LIMIT 5;
```

---

## 6. Supabase Storage

### 6.1 Bucket
| ชื่อ Bucket | Service | ประเภท | ใช้กับ |
|------------|---------|--------|--------|
| `images` | bpost | Public | รูปภาพสำหรับ Blog post |

### 6.2 Upload Flow (bpost service)
```
POST /v1/api/b-post/blog/upload-image
    │
    ▼
BlobStorageService.uploadImage(file)
    ├─ สร้าง uniqueFilename = UUID + "_" + originalFilename
    ├─ PUT ${SUPABASE_URL}/storage/v1/object/images/${uniqueFilename}
    │     Headers:
    │       Authorization: Bearer ${SUPABASE_SERVICE_ROLE_KEY}
    │       apikey: ${SUPABASE_SERVICE_ROLE_KEY}
    │       Content-Type: <file.contentType>
    │
    └─ Public URL = ${SUPABASE_URL}/storage/v1/object/public/images/${uniqueFilename}
```

> **หมายเหตุ:** ปัจจุบัน response กลับเป็น `ByteArrayResource` (พฤติกรรมเดิมจาก Azure Blob) — ถ้าต้องการแค่ URL ให้แก้ให้คืน `{ "url": publicUrl }` แทน

---

## 7. Redis Cache Layer

Redis ทำหน้าที่เป็น cache สำหรับ 2 service:

| Cache Key Pattern | Service | TTL | ข้อมูลที่ cache |
|-------------------|---------|-----|----------------|
| `chatHistory::{roomId}_{beforeId}_{limit}` | chatapp | default | ประวัติแชตแต่ละห้อง |
| `supplierOrders::{pageNumber},{pageSize}` | dinner | default | รายการ supplier+orders |

**Cache Invalidation (chat):**
```java
// ล้างเฉพาะ key ที่ขึ้นต้นด้วย roomId (ไม่ล้าง room อื่น)
Set<String> keys = redisTemplate.keys("chatHistory::" + roomId + "_*");
redisTemplate.delete(keys);
```
> เรียกทุกครั้งที่มีข้อความใหม่เข้ามาในห้องนั้น (ทั้ง user message และ AI reply)
