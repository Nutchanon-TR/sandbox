# ChatApp Backend — Bug & Action Report

> อัปเดต 2026-04-12 | สาขา: `main`

---

## สถานะ Supabase Schema (ตรวจสอบแล้ว)

| ส่วน | สถานะ |
|------|--------|
| `vector` extension (pgvector) | ✅ ติดตั้งแล้ว |
| `chat.message_embeddings` (`vector(384)`) | ✅ มีอยู่แล้ว |
| Vector index HNSW cosine ops | ✅ มีอยู่แล้ว (`idx_message_embeddings_vector`) |
| `chat.room_members.ai_id` (FK → `ai_context.id`) | ✅ มี column จริง |
| `chat.ai_context` | ✅ มีอยู่แล้ว |

> **หมายเหตุ:** schema management ทำผ่าน Supabase โดยตรง รายละเอียดดูได้ที่ `note/docs/SUPABASE.md`

---

## Bug 1: HuggingFace API Response Type ผิด — `double[]` แทน `double[][]`

**ไฟล์:** `backend/chatapp/.../services/EmbeddingService.java`
**ความรุนแรง:** Critical
**สถานะ:** ✅ แก้แล้ว (2026-04-12)

### สาเหตุ

HuggingFace feature-extraction endpoint คืน response เป็น nested array `[[...]]`
แต่โค้ดเดิม deserialize เป็น `double[]` (flat) → Jackson โยน `HttpMessageConversionException`

### การแก้ไข

```java
// ก่อน (ผิด)
ResponseEntity<double[]> response = restTemplate.exchange(..., double[].class);
double[] doubles = response.getBody();

// หลัง (ถูก)
ResponseEntity<double[][]> response = restTemplate.exchange(..., double[][].class);
double[][] body = response.getBody();
double[] doubles = body[0];
```

---

## Bug 2: Cache Invalidation ล้างทุก Room — `allEntries = true`

**ไฟล์:** `backend/chatapp/.../services/ChatService.java`
**ความรุนแรง:** Medium (functional ถูก แต่ performance แย่เมื่อ scale)
**สถานะ:** ✅ แก้แล้ว (2026-04-12)

### สาเหตุ

`@CacheEvict(value = "chatHistory", allEntries = true)` ล้าง cache ทุกห้องแชตทุกครั้งที่มีข้อความใหม่แม้จะอยู่ห้องเดียว

### การแก้ไข

ลบ `@CacheEvict` ออก แล้วเพิ่ม method `evictRoomCache(roomId)` ที่ใช้ `RedisTemplate.keys()` ลบเฉพาะ key ที่ match pattern `chatHistory::{roomId}_*`

---

## Bug 3: ACA Port Mapping ผิดทั้งหมด

**ไฟล์:** `.github/workflows/aca-deploy.yml`
**ความรุนแรง:** Critical (ACA deployment พัง)
**สถานะ:** ✅ แก้แล้ว (2026-04-12)

### สาเหตุ

`environmentVariables: FRONTEND_PORT=80 BACKEND_PORT=80 OAUTH2_PROXY_PORT=80`
ทั้งที่ services ฟังคนละ port จริงๆ

### การแก้ไข

```yaml
environmentVariables: FRONTEND_PORT=3000 BACKEND_PORT=8080 OAUTH2_PROXY_PORT=4180
```

---

## Bug 4: WebConfig CORS Hardcode — ไม่อ่านจาก Config

**ไฟล์:** `backend/*/config/WebConfig.java` (ทั้ง 3 services)
**ความรุนแรง:** Medium (ACA/production จะเกิด CORS Error)
**สถานะ:** ✅ แก้แล้ว (2026-04-12)

### สาเหตุ

`allowedOrigins("http://localhost:3000")` hardcode อยู่ใน code ทำให้ deploy บน ACA แล้ว browser ยิงจาก domain จริงไม่ได้

### การแก้ไข

ใช้ `@Value("${app.cors.allowedOrigins}")` อ่านจาก `application.yml` แทน
เพิ่ม domain production ใน `application.yml` ของแต่ละ service:

```yaml
app:
  cors:
    allowedOrigins:
      - http://localhost:3000
      # TODO: เพิ่ม ACA/Cloudflare domain เมื่อ deploy
```

---

## รายการที่ต้องทำเอง (Manual Actions)

รายการต่อไปนี้ **ไม่สามารถแก้ในโค้ดได้** ต้องดำเนินการใน portal/console ภายนอก

---

### 🔵 M1 — Azure for Students + ACA Setup

**ที่ไหน:** [Azure Portal](https://portal.azure.com)
**ค่าใช้จ่าย:** ฟรี ($100 student credit)

**ขั้นตอน:**
1. สมัคร Azure for Students ที่ `https://azure.microsoft.com/free/students` ด้วย email มหาวิทยาลัย
2. สร้าง Resource Group: `sandbox-rg` (region: `Southeast Asia` หรือ `East Asia`)
3. สร้าง ACA Environment: `sandbox-env` ใน Resource Group เดียวกัน
4. ตั้ง GitHub Secrets ต่อไปนี้ใน repo Settings → Secrets:
   - `AZURE_CREDENTIALS` — JSON จาก `az ad sp create-for-rbac ...`
   - `RESOURCE_GROUP` — ชื่อ Resource Group (เช่น `sandbox-rg`)
5. เพิ่ม domain production ใน `app.cors.allowedOrigins` ของทุก `application.yml`

**คำสั่งสร้าง Azure Credentials:**
```bash
az ad sp create-for-rbac \
  --name "sandbox-github-actions" \
  --role contributor \
  --scopes /subscriptions/<subscription-id>/resourceGroups/sandbox-rg \
  --sdk-auth
```

---

### 🔵 M2 — OAuth2 Proxy Secrets

**ที่ไหน:** GitHub Secrets + Supabase Dashboard
**ค่าใช้จ่าย:** ฟรี

**ขั้นตอน:**
1. ไปที่ Supabase Dashboard → Project → Authentication → Providers
2. เปิด **Email** หรือ OAuth provider ที่ต้องการ และจด `Client ID` / `Client Secret`
3. สร้าง `OAUTH2_PROXY_COOKIE_SECRET`:
   ```bash
   openssl rand -base64 32 | tr -- '+/' '-_'
   ```
4. ตั้ง GitHub Secrets:
   - `SUPABASE_PROJECT_ID` — เช่น `xabewjiiewyhhjfekazv`
   - `OAUTH2_PROXY_CLIENT_ID`
   - `OAUTH2_PROXY_CLIENT_SECRET`
   - `OAUTH2_PROXY_COOKIE_SECRET`
5. ตั้ง `--oidc-issuer-url` ใน oauth2-proxy ให้ชี้ไป:
   `https://<project-id>.supabase.co/auth/v1`

---

### 🔵 M3 — Cloudflare DNS + WAF (Free Tier)

**ที่ไหน:** [Cloudflare Dashboard](https://dash.cloudflare.com)
**ค่าใช้จ่าย:** ฟรี

**ขั้นตอน:**
1. สมัคร Cloudflare Free และเพิ่ม domain (หรือใช้ subdomain ฟรีจาก ACA FQDN ก็พอ)
2. ตั้ง DNS CNAME ชี้ไปยัง ACA gateway external FQDN:
   `gateway-service.<env-id>.<region>.azurecontainerapps.io`
3. เปิด **Proxy** (orange cloud) เพื่อได้ WAF และ DDoS protection ฟรี
4. ตั้ง Rate Limiting rule (Free: 1 rule):
   - Path: `/v1/api/*`
   - Threshold: 100 requests / 1 minute per IP
5. เพิ่ม Cloudflare domain ใน `app.cors.allowedOrigins` ของทุก service

---

### 🔵 M4 — New Relic APM (Free Tier)

**ที่ไหน:** [New Relic](https://newrelic.com) (100 GB/month ฟรี)
**ค่าใช้จ่าย:** ฟรี

**ขั้นตอน:**
1. สมัคร New Relic Free ด้วย email นักศึกษา
2. ดาวน์โหลด New Relic Java Agent: `newrelic-agent.jar`
3. เพิ่มใน Dockerfile ของแต่ละ backend service:
   ```dockerfile
   ADD https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-agent.jar /app/newrelic-agent.jar
   ENV JAVA_TOOL_OPTIONS="-javaagent:/app/newrelic-agent.jar"
   ENV NEW_RELIC_LICENSE_KEY=${NEW_RELIC_LICENSE_KEY}
   ENV NEW_RELIC_APP_NAME="chat-service"
   ```
4. ตั้ง GitHub Secret `NEW_RELIC_LICENSE_KEY` และเพิ่มใน ACA Secrets
5. ได้ APM Dashboard + Distributed Tracing + Logs ทันทีโดยไม่ต้องแก้โค้ด

---

### 🔵 M5 — Supabase RLS (Row-Level Security)

**ที่ไหน:** Supabase Dashboard → SQL Editor
**ค่าใช้จ่าย:** ฟรี

**ขั้นตอน:**
1. เปิด RLS บน tables หลัก:
   ```sql
   ALTER TABLE chat.messages ENABLE ROW LEVEL SECURITY;
   ALTER TABLE chat.rooms ENABLE ROW LEVEL SECURITY;
   ALTER TABLE chat.room_members ENABLE ROW LEVEL SECURITY;
   ```
2. สร้าง policies:
   ```sql
   -- User เห็นเฉพาะข้อความในห้องที่ตัวเองเป็นสมาชิก
   CREATE POLICY "members see room messages"
     ON chat.messages FOR SELECT
     USING (
       room_id IN (
         SELECT room_id FROM chat.room_members rm
         JOIN chat.users u ON u.id = rm.user_id
         WHERE u.supabase_uid = auth.uid()
       )
     );
   ```
3. ทดสอบด้วย Supabase SQL Editor โดยเซ็ต `auth.uid()` เป็น UID จริงของ test user

---

### 🔵 M6 — เพิ่ม CORS Domain หลัง Deploy

**ที่ไหน:** ไฟล์ `application.yml` ของแต่ละ service
**ค่าใช้จ่าย:** ฟรี

หลังจากได้ ACA External FQDN และ/หรือ Cloudflare domain แล้ว ให้เพิ่มใน `application.yml` ทั้ง 3 services:

```yaml
app:
  cors:
    allowedOrigins:
      - http://localhost:3000
      - https://yourdomain.com                          # Cloudflare domain
      - https://gateway-service.xxx.azurecontainerapps.io  # ACA FQDN
```

แล้ว push → CI/CD จะ rebuild และ deploy อัตโนมัติ

---

## สรุปสถานะ Bug ทั้งหมด

| # | รายการ | ไฟล์ | สถานะ |
|---|--------|------|-------|
| B1 | EmbeddingService `double[]` → `double[][]` | `EmbeddingService.java` | ✅ แก้แล้ว |
| B2 | Cache Invalidation ล้างทุก room | `ChatService.java` | ✅ แก้แล้ว |
| B3 | ACA Port Mapping ผิด (80 ทั้งหมด) | `aca-deploy.yml` | ✅ แก้แล้ว |
| B4 | WebConfig CORS hardcode | `WebConfig.java` (×3) | ✅ แก้แล้ว |

## สรุป Manual Actions

| # | รายการ | ที่ไหน | สถานะ |
|---|--------|--------|-------|
| M1 | Azure for Students + ACA Setup | Azure Portal | ⬜ รอดำเนินการ |
| M2 | OAuth2 Proxy Secrets | GitHub Secrets + Supabase | ⬜ รอดำเนินการ |
| M3 | Cloudflare DNS + WAF | Cloudflare Dashboard | ⬜ รอดำเนินการ |
| M4 | New Relic APM | New Relic + Dockerfile | ⬜ รอดำเนินการ |
| M5 | Supabase RLS Policies | Supabase SQL Editor | ⬜ รอดำเนินการ |
| M6 | เพิ่ม CORS Domain หลัง Deploy | application.yml (×3) | ⬜ รอ domain จริง |
