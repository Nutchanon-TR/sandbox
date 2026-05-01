# Environment Keys - Current Code Spec

เอกสารนี้สรุป environment variables และ secrets ที่พบใน source code ปัจจุบัน

---

## Frontend

| Key | ใช้ที่ | Required | หมายเหตุ |
|---|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | `frontend/lib/supabase/*`, `middleware.ts` | yes | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | `frontend/lib/supabase/*`, `middleware.ts` | yes | anon key ฝั่ง client/server middleware |
| `NEXT_PUBLIC_SITE_URL` | `frontend/app/auth/callback/route.ts`, login redirect | prod recommended | build-time arg ใน Dockerfile |
| `NEXT_PUBLIC_AUTH_REDIRECT_URL` | `frontend/app/login/page.tsx` | optional | override redirect URL ตอน login |
| `NEXT_PUBLIC_API_URL` | axios/API constants/BPost websocket | optional | ว่างแล้วใช้ relative path ผ่าน gateway |
| `NEXT_PUBLIC_USER_API_URL` | `ApiSandbox.ts` | optional | dev override สำหรับ user-service base URL |
| `SOURCE_SYSTEM_NAME` | `frontend/config/axiosConfig.tsx` | optional | header `sourceSystem`; fallback `FRONTEND` |

หมายเหตุ: ตัวแปร `NEXT_PUBLIC_*` ถูก bake ตอน `next build` ไม่ใช่ runtime injection

---

## Backend Common

| Key | ใช้ที่ | หมายเหตุ |
|---|---|---|
| `SUPABASE_DB_USERNAME` | local Spring datasource username | default ใน app.yml คือ `postgres` |
| `SUPABASE_DB_PASSWORD` | Spring datasource password | ใช้ทุก backend service |
| `SPRING_DATASOURCE_URL` | ACA runtime override | ใช้ Supavisor pooler `:6543` |
| `SPRING_DATASOURCE_USERNAME` | ACA runtime override | format `postgres.<project-ref>` |
| `CORS_ALLOWED_ORIGIN_GATEWAY` | `WebConfig` และ b-post websocket allowed origins | default ชี้ gateway ACA URL หรือ localhost สำหรับ websocket |

---

## Supabase API / Storage

| Key | ใช้ที่ | หมายเหตุ |
|---|---|---|
| `SUPABASE_URL` | `app.supabase.url` | ใช้สร้าง Storage REST URL |
| `SUPABASE_SERVICE_ROLE_KEY` | `BlobStorageService`, app config | ใช้ upload object เข้า Supabase Storage |

Storage bucket default ใน app.yml:

```text
app.supabase.storage.bucket-name=images
```

---

## AI Services

| Key | ใช้ที่ | หมายเหตุ |
|---|---|---|
| `GROK_API_KEY` | `spring.ai.openai.api-key` | ชื่อสะกดตามโค้ด แม้ provider คือ Groq |
| `HUGGINGFACE_API_KEY` | `app.huggingface.api-key` | ใช้ใน ChatApp embedding |

Groq base URL:

```text
https://api.groq.com/openai
```

HuggingFace endpoint:

```text
https://router.huggingface.co/hf-inference/models/intfloat/multilingual-e5-small/pipeline/feature-extraction
```

---

## Gateway / OAuth2 Proxy

| Key | ใช้ที่ | หมายเหตุ |
|---|---|---|
| `FRONTEND_PORT` | nginx template | local compose `3000`, ACA `80` |
| `BACKEND_PORT` | nginx template | local compose `8080`, ACA `80` |
| `OAUTH2_PROXY_PORT` | nginx template | local compose `4180`, ACA `80` |
| `SUPABASE_PROJECT_ID` | oauth2-proxy issuer | ใช้ประกอบ Supabase issuer URL |
| `OAUTH2_PROXY_CLIENT_ID` | oauth2-proxy | OIDC client id |
| `OAUTH2_PROXY_CLIENT_SECRET` | oauth2-proxy | OIDC client secret |
| `OAUTH2_PROXY_COOKIE_SECRET` | oauth2-proxy | cookie encryption secret |

ACA workflow ยังตั้งค่า oauth2-proxy เพิ่มด้วย `OAUTH2_PROXY_*` runtime env เช่น `OAUTH2_PROXY_PROVIDER=oidc`, `OAUTH2_PROXY_SKIP_JWT_BEARER_TOKENS=true`, `OAUTH2_PROXY_REVERSE_PROXY=true`

---

## GitHub Actions / Azure

| Secret/Env | ใช้ที่ |
|---|---|
| `AZURE_CREDENTIALS` | `azure/login` |
| `RESOURCE_GROUP` | deploy container apps |
| `CONTAINER_APP_ENVIRONMENT` | target ACA environment |
| `GITHUB_TOKEN` | push/pull GHCR packages |
| `NEXT_PUBLIC_SUPABASE_URL` | frontend build arg |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | frontend build arg |
| `NEXT_PUBLIC_SITE_URL` | frontend build arg |
| `SUPABASE_PROJECT_ID` | DB username และ oauth2 issuer |
| `SUPABASE_DB_PASSWORD` | backend datasource secret |
| `SUPABASE_URL` | backend Supabase REST/Storage secret |
| `SUPABASE_SERVICE_ROLE_KEY` | backend service-role secret |
| `GROK_API_KEY` | Groq secret |
| `HUGGINGFACE_API_KEY` | ChatApp embedding secret |

---

## .env.example ปัจจุบัน

ไฟล์ `.env.example` มี local template สำหรับ:

- Supabase DB: `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`
- Supabase API: `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_URL`
- AI: `GROK_API_KEY`, `HUGGINGFACE_API_KEY`
- Frontend redirect: `NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_AUTH_REDIRECT_URL`, `NEXT_PUBLIC_USER_API_URL`
- OAuth2 Proxy: `SUPABASE_PROJECT_ID`, `OAUTH2_PROXY_CLIENT_ID`, `OAUTH2_PROXY_CLIENT_SECRET`, `OAUTH2_PROXY_COOKIE_SECRET`

`NEXT_PUBLIC_API_URL` ถูกใช้ในโค้ด แต่ไม่ได้อยู่ใน `.env.example`; ถ้าไม่กำหนด ระบบจะใช้ relative URLs ผ่าน gateway
