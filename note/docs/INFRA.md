# Infrastructure - Current Code Spec

เอกสารนี้สรุปโครงสร้าง infra จากไฟล์ปัจจุบัน: `docker-compose.yml`, `gateway/nginx.conf.template`, Dockerfile แต่ละ service และ `.github/workflows/aca-deploy.yml`

---

## Services

| Service | Local compose name | Runtime port | Path ผ่าน gateway |
|---|---|---|---|
| Frontend | `frontend` | `3000` | `/` |
| ChatApp | `chat-service` | `8080` | `/v1/api/chat-app/` |
| Dinner | `dinner-service` | `8080` | `/v1/api/dinner/` |
| B-Post | `bpost-service` | `8080` | `/v1/api/b-post/` |
| User | `user-service` | `8080` | `/v1/api/user/` |
| OAuth2 Proxy | `oauth2-proxy` | `4180` | `/oauth2/`, internal auth subrequest |
| Gateway | `gateway` | `80` | entry point |

ทุก service ใน docker-compose อยู่บน network `sandbox_net` และ frontend/backend ไม่ expose port ออกตรง เพราะ gateway เป็นทางเข้าเดียว

---

## Docker Compose

ไฟล์: `docker-compose.yml`

จุดสำคัญ:

- `gateway` expose `80:80`
- `gateway` inject env สำหรับ nginx template: `FRONTEND_PORT=3000`, `BACKEND_PORT=8080`, `OAUTH2_PROXY_PORT=4180`
- backend services ใช้ `env_file: .env`
- `chat-service` และ `bpost-service` ใช้ build context `./backend` เพราะ Dockerfile ต้อง copy `common-auth`
- `dinner-service` และ `user-service` ใช้ context ย่อยของตัวเอง

```yaml
chat-service:
  build:
    context: ./backend
    dockerfile: chatapp/Dockerfile

bpost-service:
  build:
    context: ./backend
    dockerfile: bpost/Dockerfile
```

---

## Gateway Routing

ไฟล์: `gateway/nginx.conf.template`

Nginx upstream ใช้ service name จาก Docker/ACA:

```nginx
upstream frontend       { server frontend:${FRONTEND_PORT}; }
upstream chat-service   { server chat-service:${BACKEND_PORT}; }
upstream dinner-service { server dinner-service:${BACKEND_PORT}; }
upstream bpost-service  { server bpost-service:${BACKEND_PORT}; }
upstream user-service   { server user-service:${BACKEND_PORT}; }
upstream oauth2-proxy   { server oauth2-proxy:${OAUTH2_PROXY_PORT}; }
```

API routes ทั้งหมดผ่าน `auth_request /oauth2/auth` ยกเว้น b-post websocket:

| Route | Auth | Upstream |
|---|---|---|
| `/v1/api/chat-app/` | oauth2-proxy | `chat-service` |
| `/v1/api/dinner/` | oauth2-proxy | `dinner-service` |
| `/v1/api/b-post/` | oauth2-proxy | `bpost-service` |
| `/v1/api/user/` | oauth2-proxy + CORS preflight bypass | `user-service` |
| `/v1/api/b-post/ws/` | STOMP CONNECT JWT, ไม่ใช้ `auth_request` | `bpost-service` |

Gateway forward header สำคัญ:

```nginx
proxy_set_header Authorization $http_authorization;
proxy_set_header X-User-Id $user;
proxy_set_header Host $proxy_host;
```

`/v1/api/user/` มี CORS handling ที่ Nginx เพื่อให้ local dev ยิง prod gateway ได้ โดย echo เฉพาะ origin ที่เป็น localhost และ bypass `OPTIONS`

---

## Dockerfiles

### Gateway

`gateway/Dockerfile`

- base image `nginx:1.25.4-alpine`
- ลบ default conf
- copy `nginx.conf.template` ไป `/etc/nginx/templates/`
- nginx image จะทำ envsubst template ตอน container start

### Frontend

`frontend/Dockerfile`

- base image `node:20-alpine`
- build ด้วย `npm ci` และ `npm run build`
- runtime ใช้ Next.js standalone output (`node server.js`)
- `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY`, `NEXT_PUBLIC_SITE_URL` เป็น build args และถูก bake เข้า bundle

### ChatApp และ B-Post

`backend/chatapp/Dockerfile` และ `backend/bpost/Dockerfile`

- base builder `maven:3.9.6-eclipse-temurin-21`
- context ต้องเป็น `./backend`
- copy `common-auth` แล้ว `mvn install -DskipTests -B`
- build service jar หลังจาก common-auth อยู่ใน local Maven cache ของ image
- runtime ใช้ `eclipse-temurin:21-jre`

### Dinner และ User

`backend/dinner/Dockerfile` และ `backend/user/Dockerfile`

- build จาก context ย่อยของแต่ละ service
- runtime `eclipse-temurin:21-jre`
- current code note: `pom.xml` ของทั้งสอง service มี dependency `common-auth` แต่ Dockerfile ยังไม่ได้ install/copy `common-auth` เหมือน chatapp/bpost

---

## Azure Container Apps Workflow

ไฟล์: `.github/workflows/aca-deploy.yml`

Workflow ปัจจุบัน build/deploy แยก job:

- frontend
- chat-service
- dinner-service
- bpost-service
- user-service
- gateway-service
- oauth2-proxy

ACA ingress ปัจจุบันใน workflow:

| Container App | Ingress | Target port |
|---|---|---|
| `gateway-service` | external | 80 |
| `frontend` | internal | 3000 |
| backend services | internal | 8080 |
| `oauth2-proxy` | internal | 4180 |

Gateway บน ACA ใช้ env:

```text
FRONTEND_PORT=80
BACKEND_PORT=80
OAUTH2_PROXY_PORT=80
```

เพราะ route ผ่าน internal ACA ingress ของแต่ละ container app

---

## ACA Environment และ Secrets

Backend services ใช้ Supavisor transaction pooler ใน ACA:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:6543/postgres?prepareThreshold=0
SPRING_DATASOURCE_USERNAME=postgres.<SUPABASE_PROJECT_ID>
SUPABASE_DB_PASSWORD=secretref:supabase-db-password
```

Service-specific secrets:

| Service | Secrets/env สำคัญ |
|---|---|
| chat-service | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `GROK_API_KEY`, `HUGGINGFACE_API_KEY` |
| bpost-service | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `GROK_API_KEY` |
| dinner-service | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `GROK_API_KEY` |
| user-service | `SUPABASE_DB_PASSWORD` |
| oauth2-proxy | `SUPABASE_PROJECT_ID`, OAuth2 client id/secret, cookie secret |

หมายเหตุ: `GROK_API_KEY` ใน dinner/bpost มาจาก shared app.yml pattern แม้ service เหล่านี้ไม่ได้ใช้ AI flow หลักแบบ ChatApp

---

## Known Infra Mismatches จากโค้ดปัจจุบัน

| จุด | สถานะในโค้ด | ผลที่อาจเกิด |
|---|---|---|
| CI build chat-service | workflow ใช้ context `./backend/chatapp` แต่ Dockerfile expect `./backend` | `COPY common-auth/...` fail |
| CI build bpost-service | workflow ใช้ context `./backend/bpost` แต่ Dockerfile expect `./backend` | `COPY common-auth/...` fail |
| dinner/user Dockerfile | ไม่ install `common-auth` แต่ `pom.xml` depend on `common-auth` | Maven resolve fail ถ้าไม่มี artifact ใน repo |
| OAuth2 issuer | docker-compose กับ ACA workflow ใช้ issuer format ไม่เหมือนกัน | auth validate fail ได้ถ้า token issuer ไม่ตรง |

---

## Local Run

ใช้ไฟล์ `.env` ที่สร้างจาก `.env.example` แล้วรัน:

```bash
docker compose up --build
```

Gateway จะรับ traffic ที่:

```text
http://localhost
```

Frontend API ใช้ relative URL เป็นค่า default (`NEXT_PUBLIC_API_URL` ว่าง) ทำให้ยิงผ่าน gateway อัตโนมัติ
