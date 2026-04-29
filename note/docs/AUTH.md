# แนวคิดและระบบ Authentication (Auth)

เอกสารฉบับนี้รวบรวมคำอธิบาย โครงสร้าง และแนวคิดพื้นฐานเกี่ยวกับระบบการยืนยันตัวตน (Authentication) ที่ใช้ภายในโปรเจกต์ Sandbox นี้ โดยเฉพาะความแตกต่างระหว่างระบบต่างๆ และแนวปฏิบัติ (Best Practices) ในการวางโครงสร้างไฟล์

---

## 1. เปรียบเทียบ NextAuth (Auth.js) vs Supabase Auth

ใน Next.js นั้นมีไลบรารียอดนิยม 2 ตัวที่เรามักจะนำมาใช้ทำระบบ Login/Auth ซึ่งมีความแตกต่างกันในด้านโครงสร้างการออกแบบดังนี้:

### 1.1 `api/auth/[...nextauth]/route.ts` (NextAuth.js)
- **Catch-all Route:** โครงสร้างโฟลเดอร์ที่มีวงเล็บเหลี่ยมจุด 3 จุด `[...]` เป็นความสามารถของ Next.js ที่เรียกว่า Catch-all route หมายความว่า URL ใดๆ ก็ตามที่นำหน้าด้วย `api/auth/` (เช่น `/api/auth/signin`, `/api/auth/callback`, `/api/auth/session`) จะถูกดึงและจัดการรวมไว้ที่ไฟล์ `route.ts` ไฟล์นี้ไฟล์เดียว
- **ระบบอัตโนมัติเบ็ดเสร็จ:** เนื่องจากไลบรารี NextAuth ได้ออกแบบระบบจัดการให้หมดแล้ว ผู้ใช้ "จำเป็น" ต้องสร้างโครงสร้างพาธนี้ให้ตรงเป๊ะ ๆ เพราะโค้ดวงใน (Hardcoded) ของระบบมันจะชี้มาหาโฟลเดอร์นี้เสมอ
- **สถานะใน Sandbox ปัจจุบัน:** ปัจจุบันเราใช้ Supabase 100% และได้ลบโฟลเดอร์ `frontend/app/api/auth/` ออกจาก main แล้ว (อาจยังเห็นหลงเหลืออยู่ใน worktree เก่า เช่น `.claude/worktrees/...` ซึ่งไม่กระทบกับโค้ดที่รันจริง)

### 1.2 `auth/callback/route.ts` (Supabase Auth)
- **Custom Callback แบบทำมือ:** แตกต่างจาก NextAuth ตัวระบบจัดการเซสชันของ `@supabase/ssr` ไม่ได้ผูกขาดว่าเราต้องมี Catch-all Route เป็นของตัวเอง สิ่งที่เราต้องทำคือการเขียน "จุดรับเสด็จ" (Redirect Receiver) เมื่อเราเลือก Login เสร็จจากฝั่งผู้ให้บริการ
- **การทำงาน:** ฝั่งผู้ให้บริการ (อย่าง Google หรือ Magic Link) จะส่งรหัส (`code`) ใส่มาใน URL กลับมาที่หน้าเว็บของเรา ตัว Endpoint นี้มีหน้าที่นำโค้ดที่ได้ ไปแลกฝั่งกุญแจเข้ารหัสใหม่ให้กลายเป็น Session Cookies
- **อิสระในการตั้งชื่อ:** เราสามารถเปลี่ยนชื่อพาธจาก `auth/callback` เป็นอะไรก็ได้ (เช่น `api/confirm`, `auth/verify`) **แต่** ต้องจำไว้เสมอว่า URL ไหนที่เราตั้งขึ้นใหม่ จะต้องนำไปอัปเดตลง **Callback URLs** ในหน้าของ Supabase Dashboard ให้ตรงกันด้วยเสมอ
- **สถานะใน Sandbox ปัจจุบัน:** ไฟล์จริงอยู่ที่ [frontend/app/auth/callback/route.ts](../../frontend/app/auth/callback/route.ts) เรียกใช้ `createSupabaseServer()` จาก [frontend/lib/supabase/server.ts](../../frontend/lib/supabase/server.ts) เพื่อทำ `exchangeCodeForSession(code)` แล้ว redirect กลับไปที่ `next` (default `/`) โดยรองรับการ deploy หลัง nginx ผ่านการอ่าน `X-Forwarded-Host` / `X-Forwarded-Proto` (และ override ด้วย `NEXT_PUBLIC_SITE_URL` เมื่อ build production/staging)

---

## 2. โครงสร้างโฟลเดอร์ (Conventions) สำหรับ Auth Config

คำถามที่พบบ่อยคือ "เราควรนำไฟล์ตั้งค่าอย่าง `auth.ts`, `middleware.ts`, หรือไฟล์ต่อ Supabase ไปไว้ในโฟลเดอร์ไหนดี ระหว่าง `lib/` กับ `config/` ?"

### 2.1 Middleware (`middleware.ts`)
- **ต้องอยู่ที่ Root ของโปรเจกต์เสมอ:** ไม่สามารถนำไปซ่อนใน `config/` หรือ `lib/` ได้ ตัวไฟล์จะต้องอยู่ติดกับ `package.json` (หรือในโฟลเดอร์ `src/`) เสมอ 
- **หน้าที่:** ระบบ Edge Server ของ Next.js จะดึงไฟล์นี้ไปทำงานเป็น "ป้อมยาม" ดักจับทุก Request ที่พยายามเข้าเว็บ ถ้าผู้ใช้ยังไม่ล็อกอินก็จะดันไปที่หน้า `/login` หรือต่ออายุ Session คุกกี้

### 2.2 ไฟล์ Auth/Supabase Initialization
โค้ดที่ใช้เตรียมคำสั่ง (Initialize) เช่น `createBrowserClient` หรือเครื่องมือทำ Auth มักจะตั้งชื่อว่า `auth.ts`, `client.ts` เราควรและนิยมนำมาวางไว้ที่ **`lib/`**:
- **โฟลเดอร์ `lib/` (Library):** ใช้ห่อหุ้มเครื่องมือ ฟังก์ชันซับซ้อน หรือตัวระบบนอก (Third-party) ให้เหลือเพียงคำสั่งสั้นๆ เพื่อให้หน้าเว็บนำไป Import ใช้งานได้อย่างสะอาด
- **โฟลเดอร์ `config/` (Configuration):** นิยมเก็บเฉพาะ "ค่าคงที่เสมือน" สตริงข้อมูล หรือ Object ตัวแปรที่ไม่มีกลไกควบคุม เช่น `["Home", "About"]`, โค้ดสี, หรือสเปกไฟล์ เป็นต้น ดังนั้นระบบที่มีการประมวลผลจึงไม่ค่อยเหมาะกับที่นี่

---

## 3. Service Access Control — `allowed_services` (ยังไม่ implement)

> **สถานะปัจจุบัน:** ยังไม่มี column `allowed_services`, trigger, หรือ filter ใน codebase (ค้นใน `backend/` และ migrations แล้วไม่พบ) ส่วนนี้เก็บไว้เป็น **design proposal** สำหรับ Phase ที่ OAuth2Proxy + multi-service พร้อมใช้งานเท่านั้น

### 3.1 Schema (proposed)

Column นี้เก็บไว้ใน `public.users` (ไม่ใช่ `auth.users`) เพื่อให้ Service Role มีสิทธิ์แก้ไขได้โดยตรง

```sql
ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS allowed_services text[] NOT NULL DEFAULT '{all}';

-- Constraint: ค่าที่อนุญาตคือ all, chat_app, bpost, dinner เท่านั้น
ALTER TABLE public.users
  ADD CONSTRAINT chk_allowed_services
  CHECK (
    allowed_services <@ ARRAY['all','chat_app','bpost','dinner']::text[]
  );
```

| ค่า | ความหมาย |
|-----|-----------|
| `{all}` | เข้าถึงได้ทุก service |
| `{chat_app}` | เฉพาะ Chat App |
| `{bpost}` | เฉพาะ B-Post |
| `{dinner}` | เฉพาะ Dinner |
| `{chat_app,dinner}` | เข้าได้สอง service ขึ้นไป |

---

### 3.2 Architecture — 3 แนวทาง (เปรียบเทียบ)

#### Option A: Query DB ตรงใน BE แต่ละ Request (ง่าย แต่ไม่ scale)
```
FE → Nginx → OAuth2Proxy (validate JWT) → BE Service
                                               └─ query public.users.allowed_services
                                                  ทุก request → latency สูง
```
- **ข้อดี:** ข้อมูลสดเสมอ, implement ง่าย
- **ข้อเสีย:** ทุก request ต้องยิง Supabase → latency เพิ่ม, ทุก service ต้องเขียน logic ซ้ำ

#### Option B: Embed ใน `app_metadata` → JWT (แนะนำสำหรับโปรเจกต์นี้) ✅
```
Admin แก้ allowed_services ใน DB
     └─ Trigger/Function sync → auth.users.app_metadata.allowed_services
                                      └─ Supabase embed ลง JWT อัตโนมัติ
FE → Nginx → OAuth2Proxy (validate JWT + inject X-Allowed-Services header)
                                └─ BE Service อ่าน header โดยตรง ไม่ต้อง query DB
```
- **ข้อดี:** ไม่ต้อง query DB ต่อ request, ทุก BE service อ่าน header ตัวเดียวกัน, OAuth2Proxy inject ให้ทันที
- **ข้อเสีย:** ถ้าแก้ permission ต้องรอ JWT หมดอายุ (หรือบังคับ signOut) ก่อนค่าใหม่จะมีผล

#### Option C: External Auth Middleware Service (Enterprise, เกินความจำเป็น)
```
FE → Nginx → OAuth2Proxy → Auth Middleware Service → BE
                                 └─ cache allowed_services ใน Redis
```
- **ข้อดี:** ยืดหยุ่นสูงสุด
- **ข้อเสีย:** ซับซ้อนเกินสำหรับ scope นี้

---

### 3.3 Implementation — Option B (แนะนำ)

**Step 1: Supabase Function + Trigger (sync `allowed_services` → `app_metadata`)**

```sql
-- Function ที่จะเรียกเมื่อ allowed_services เปลี่ยน
CREATE OR REPLACE FUNCTION sync_allowed_services_to_metadata()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
  UPDATE auth.users
    SET raw_app_meta_data = raw_app_meta_data ||
        jsonb_build_object('allowed_services', NEW.allowed_services)
    WHERE id = NEW.supabase_uid;
  RETURN NEW;
END;
$$;

-- Trigger บน public.users
CREATE TRIGGER trg_sync_allowed_services
  AFTER INSERT OR UPDATE OF allowed_services ON public.users
  FOR EACH ROW EXECUTE FUNCTION sync_allowed_services_to_metadata();
```

**Step 2: OAuth2Proxy inject header**

ใน `oauth2-proxy` config ให้เพิ่ม pass-through ของ claim `allowed_services` จาก JWT:
```ini
# oauth2-proxy.cfg
pass-access-token = true
set-xauthrequest = true
# JWT claim จะถูก inject เป็น X-Auth-Request-* header อัตโนมัติ
```

**Step 3: Spring Boot Filter ในแต่ละ BE Service**

```java
@Component
public class ServiceAccessFilter extends OncePerRequestFilter {
    private static final String SERVICE_NAME = "chat_app"; // เปลี่ยนตาม service

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws IOException, ServletException {
        String allowed = req.getHeader("X-Allowed-Services"); // inject โดย OAuth2Proxy
        if (allowed == null || (!allowed.contains("all") && !allowed.contains(SERVICE_NAME))) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "Service access denied");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

---

### 3.4 แนวทางที่แนะนำสำหรับโปรเจกต์นี้

```
[Supabase DB: public.users.allowed_services]
        │ Trigger auto-sync
        ▼
[auth.users.app_metadata.allowed_services]
        │ embed ใน JWT
        ▼
[OAuth2Proxy: validate JWT + inject X-Allowed-Services header]
        │
        ├──▶ [chat_app BE] → Filter อ่าน header → ตรวจ "all" หรือ "chat_app"
        ├──▶ [bpost BE]    → Filter อ่าน header → ตรวจ "all" หรือ "bpost"
        └──▶ [dinner BE]   → Filter อ่าน header → ตรวจ "all" หรือ "dinner"
```

> **Note:** ถ้า Phase 1 (OAuth2Proxy) ยังไม่ live ให้ BE แต่ละตัว query `public.users.allowed_services` โดยตรงชั่วคราวก่อน แล้วค่อย migrate ไป header-based เมื่อ gateway พร้อม

---

## 4. สรุป Workflow ของระบบ Auth ที่ใช้งานจริงตอนนี้

ในปัจจุบันระบบ Sandbox ของเราพึ่งพา Supabase แบบเต็มรูปแบบ:
1. ผู้ใช้กดปุ่ม Login ที่ [frontend/app/login/page.tsx](../../frontend/app/login/page.tsx)
2. หลัง provider ส่งกลับมา จะเข้าที่ [frontend/app/auth/callback/route.ts](../../frontend/app/auth/callback/route.ts) เพื่อ `exchangeCodeForSession` และเซ็ต cookies
3. Session ถูก subscribe โดย [frontend/providers/AuthProvider.tsx](../../frontend/providers/AuthProvider.tsx) (ผ่าน `supabase.auth.onAuthStateChange`) แล้วเก็บใน Zustand store [`stores/sessionStore`](../../frontend/stores/sessionStore.ts) — components ย่อยอ่าน session จาก store นี้ตรงๆ (ยัง **ไม่มี** custom hook ชื่อ `useSupabaseSession.ts`)
4. ครั้งแรกที่เจอ session ใหม่ AuthProvider จะยิง `API_SANDBOX.USER_SYNC` ไปที่ backend เพื่อ sync `supabaseUid` → internal user id (เก็บใน store เช่นกัน)
5. การปกป้องเส้นทางใช้ [frontend/middleware.ts](../../frontend/middleware.ts) เป็น "ด่านกั้นกลาง" — ถ้าไม่มี user และ path ไม่ใช่ `/`, `/login*`, หรือ `/auth*` จะ redirect ไป `/login`; ถ้ามี user อยู่แล้วและพยายามเข้า `/login` จะ redirect กลับ `/`
