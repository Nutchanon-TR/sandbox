# INFRA.md

## ภาพรวม
เอกสารนี้อธิบายสถาปัตยกรรมการ Deploy ทั้งหมดของโปรเจกต์ **Sandbox** โดยใช้ Docker คอนเทนเนอร์ที่จัดการด้วย **docker‑compose** (สำหรับ local) / **Azure Container Apps** (สำหรับ prod) และเปิดให้เข้าถึงจากภายนอกผ่าน **Nginx** gateway มี side‑car **OAuth2‑Proxy** สำหรับ validate Supabase JWT

---

## 1. Dockerfile

### 1.1 Gateway (Nginx) – `gateway/Dockerfile`
```Dockerfile
FROM nginx:1.25.4-alpine

# Copy the custom Nginx template configuration
COPY nginx.conf.template /etc/nginx/templates/nginx.conf.template

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
```
* สร้างอิมเมจ Nginx ขนาดเบาที่ทำงานกับไฟล์ `nginx.conf.template` ของเรา (ดูส่วน 2)
* **การทำงานของ Template (`envsubst`):** ตัว Base Image `nginx:alpine` ถูกออกแบบมาให้รองรับการแทนที่ตัวแปร (Variables Substitution) หากเรานำไฟล์ไปวางไว้ที่ `/etc/nginx/templates/` ระบบจะอ่าน Environment Variables ในขณะที่ Container กำลังสตาร์ท และแปลงคำอย่าง `${FRONTEND_PORT}` ให้เป็นตัวเลขพอร์ตจริงๆ แล้วเซฟทับเป็นไฟล์ `nginx.conf` ตัวจริงให้โดยอัตโนมัติ ทำให้เราสามารถจัดการพอร์ตบน Local (เช่น 8080) และบน Cloud (80) ได้โดยใช้ไฟล์คอนฟิกเดียวกัน

### 1.2 Frontend – `frontend/Dockerfile`
```Dockerfile
# Stage 1: Build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Production runtime (Next.js standalone)
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production

COPY --from=builder /app/public ./public
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

EXPOSE 3000
CMD ["node", "server.js"]
```
* ใช้ Next.js standalone output ทำงานด้วย Node.js บนพอร์ต **3000**

### 1.3 Backend Services – `backend/{chatapp,dinner,bpost,user}/Dockerfile`
```Dockerfile
# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
* สร้าง JAR ของ Spring Boot แล้วรันบน JRE ที่เบา ใช้พอร์ต **8080**

---

## 2. การตั้งค่า Nginx – `gateway/nginx.conf.template`

> ไฟล์จริงที่ใช้ deploy อยู่ที่ [`gateway/nginx.conf.template`](../../gateway/nginx.conf.template) — ส่วนด้านล่างคือ skeleton สรุปโครงสร้าง

```nginx
# upstream ใช้ชื่อเดียวกับ service name ใน docker-compose / ACA
upstream frontend     { server frontend:${FRONTEND_PORT}; }
upstream chat-service { server chat-service:${BACKEND_PORT}; }
upstream dinner-service { server dinner-service:${BACKEND_PORT}; }
upstream bpost-service  { server bpost-service:${BACKEND_PORT}; }
upstream user-service   { server user-service:${BACKEND_PORT}; }
upstream oauth2-proxy   { server oauth2-proxy:${OAUTH2_PROXY_PORT}; }

server {
    listen 80;
    server_name localhost;

    # HTTP/1.1 จำเป็นสำหรับ ACA Envoy (HTTP/1.0 โดน 426 Upgrade Required)
    proxy_http_version 1.1;
    proxy_set_header Connection "";

    # buffer ใหญ่พอสำหรับ Supabase JWT cookie (เกิน default 4k/8k ของ nginx)
    proxy_buffer_size 128k;
    proxy_buffers 4 256k;
    proxy_busy_buffers_size 256k;

    # Rate limit error
    proxy_intercept_errors on;
    error_page 429 = @rate_limited;

    # oauth2-proxy: auth subrequest + login flow
    location = /oauth2/auth {
        proxy_pass http://oauth2-proxy;
        proxy_pass_request_body off;
        proxy_set_header Content-Length "";
        # ...
    }
    location /oauth2/ { proxy_pass http://oauth2-proxy; ... }

    # Frontend
    location / {
        proxy_pass http://frontend;
        proxy_set_header Host $proxy_host;
        # ...
    }

    # Backend APIs — ทั้งหมดผ่าน auth_request /oauth2/auth (validate Supabase JWT)
    location /v1/api/chat-app/ { auth_request /oauth2/auth; proxy_pass http://chat-service/v1/api/chat-app/; ... }
    location /v1/api/dinner/   { auth_request /oauth2/auth; proxy_pass http://dinner-service/v1/api/dinner/; ... }
    location /v1/api/b-post/   { auth_request /oauth2/auth; proxy_pass http://bpost-service/v1/api/b-post/; ... }

    # User service — มี CORS handling เพิ่มเติม (ดู §2.1)
    location /v1/api/user/ {
        # ... OPTIONS preflight bypass + proxy_hide_header + add_header
        auth_request /oauth2/auth;
        proxy_pass http://user-service/v1/api/user/;
    }

    location @unauthorized { return 302 /login; }
    location @rate_limited { default_type application/json; return 429 '{"error": "Too Many Requests"}'; }
}
```
* auth_request `/oauth2/auth` เปิดไว้ทุก backend route ตลอด (ไม่มี commented-out แบบเดิม)
* oauth2-proxy ถูก config ให้ validate Supabase JWT ใน `Authorization: Bearer <token>` (ดู AUTH.md)
* ส่วน `/v1/api/user/` มี CORS handling เพิ่ม — ดู §2.1

### 2.1 CORS Handling ที่ Gateway (`/v1/api/user/`)

สำหรับ endpoint ที่ frontend local dev (http://localhost:3000) ยิงตรงเข้า prod gateway จำเป็นต้อง handle CORS ที่ nginx เอง (ไม่พึ่ง Spring CORS เพราะจะ conflict กัน):

```nginx
location /v1/api/user/ {
    # Echo origin กลับเฉพาะ localhost — ป้องกัน arbitrary cross-origin
    set $cors_origin "";
    if ($http_origin ~* "^https?://localhost(:[0-9]+)?$") {
        set $cors_origin $http_origin;
    }

    # CORS preflight (OPTIONS ไม่ส่ง Authorization → ต้อง bypass auth_request)
    if ($request_method = OPTIONS) {
        add_header Access-Control-Allow-Origin $cors_origin always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Authorization, Content-Type, sourceSystem" always;
        add_header Access-Control-Allow-Credentials "true" always;
        add_header Access-Control-Max-Age 86400 always;
        return 204;
    }

    auth_request /oauth2/auth;
    error_page 401 = @unauthorized;
    ...
    proxy_pass http://user-service/v1/api/user/;

    # Strip CORS จาก upstream — nginx เป็น single source of truth
    # (ถ้าปล่อยผ่าน + add_header ด้านล่าง จะได้ header ซ้ำ 2 ค่า → browser reject)
    proxy_hide_header Access-Control-Allow-Origin;
    proxy_hide_header Access-Control-Allow-Credentials;
    proxy_hide_header Access-Control-Allow-Methods;
    proxy_hide_header Access-Control-Allow-Headers;

    add_header Access-Control-Allow-Origin $cors_origin always;
    add_header Access-Control-Allow-Credentials "true" always;
}
```

**จุดสำคัญ 3 ข้อ:**

1. **Custom headers ต้อง whitelist** — axios ([frontend/config/axiosConfig.tsx](frontend/config/axiosConfig.tsx)) ส่ง `sourceSystem` → ต้องใส่ใน `Access-Control-Allow-Headers` ไม่งั้น browser block ตั้งแต่ preflight
2. **OPTIONS ต้อง bypass `auth_request`** — browser ไม่แนบ `Authorization` ใน preflight → ถ้าบังคับ auth จะ 401
3. **`proxy_hide_header` กัน CORS ซ้ำ** — ถ้า upstream (Spring) เผลอเติม `Access-Control-Allow-Origin` ด้วย + nginx เติมอีก → response จะมี 2 ค่าคั่นด้วย comma → browser reject ด้วย error "header contains multiple values"

---

## 3. docker‑compose – `docker-compose.yml`
```yaml
version: '3.8'

services:
  # API Gateway (Nginx) — entry point เดียว
  gateway:
    build: { context: ./gateway, dockerfile: Dockerfile }
    ports: [ "80:80" ]
    environment:                       # ← จำเป็นเพื่อ envsubst ใน nginx.conf.template
      - FRONTEND_PORT=3000
      - BACKEND_PORT=8080
      - OAUTH2_PROXY_PORT=4180
    depends_on: [ frontend, chat-service, dinner-service, bpost-service, user-service, oauth2-proxy ]
    networks: [ sandbox_net ]
    restart: unless-stopped

  # OAuth2 Validation Sidecar (validate Supabase JWT)
  oauth2-proxy:
    image: quay.io/oauth2-proxy/oauth2-proxy:v7.6.0
    command:
      - "--http-address=0.0.0.0:4180"
      - "--provider=oidc"
      - "--oidc-issuer-url=https://${SUPABASE_PROJECT_ID:-YOUR_SUPABASE_ID}.supabase.co"
      - "--skip-provider-button=true"
      - "--email-domain=*"
      - "--cookie-secret=OAUTH2_PROXY_COOKIE_SECRET_32_BYTES"
      - "--cookie-secure=false"            # production ให้ตั้งเป็น true
      - "--upstream=http://dummy"          # Nginx จะทำ reverse ไปยัง OAuth
      - "--skip-jwt-bearer-tokens=true"    # parse Authorization: Bearer <jwt>
      - "--extra-jwt-issuers=https://${SUPABASE_PROJECT_ID:-YOUR_SUPABASE_ID}.supabase.co=https://${SUPABASE_PROJECT_ID:-YOUR_SUPABASE_ID}.supabase.co"
    environment:
      - OAUTH2_PROXY_CLIENT_ID=${OAUTH2_PROXY_CLIENT_ID:-DUMMY}
      - OAUTH2_PROXY_CLIENT_SECRET=${OAUTH2_PROXY_CLIENT_SECRET:-DUMMY}
    networks: [ sandbox_net ]
    restart: unless-stopped

  # Frontend (Next.js) — NEXT_PUBLIC_* baked at build time, runtime env ไม่มีผล
  frontend:
    build: { context: ./frontend, dockerfile: Dockerfile }
    networks: [ sandbox_net ]
    restart: unless-stopped

  # Backend Services — ทั้ง 4 service ใช้ pattern เดียวกัน (env_file: .env)
  chat-service:
    build: { context: ./backend/chatapp, dockerfile: Dockerfile }
    env_file: .env
    networks: [ sandbox_net ]
    restart: unless-stopped

  dinner-service:
    build: { context: ./backend/dinner, dockerfile: Dockerfile }
    env_file: .env
    networks: [ sandbox_net ]
    restart: unless-stopped

  bpost-service:
    build: { context: ./backend/bpost, dockerfile: Dockerfile }
    env_file: .env
    networks: [ sandbox_net ]
    restart: unless-stopped

  user-service:
    build: { context: ./backend/user, dockerfile: Dockerfile }
    env_file: .env
    networks: [ sandbox_net ]
    restart: unless-stopped

networks:
  sandbox_net:
    driver: bridge
```
* `gateway` เป็น entry point เดียว; frontend + backend ทุกตัว internal only คุยกันผ่าน docker network
* backend services ทั้ง 4 ใช้ `env_file: .env` โหลด Supabase credentials + AI keys ร่วมกัน
* Frontend **ไม่ set** `NEXT_PUBLIC_API_URL` — axios fallback เป็น relative URL แล้ว route ผ่าน gateway อัตโนมัติ
* **ไม่มี Redis container** — ระบบปัจจุบันไม่ได้ใช้ Redis cache (เคยวางแผนไว้แต่ยังไม่ implement)

---

## 5. ส่วนประกอบสนับสนุน

| ส่วน | วัตถุประสงค์ | ค่าตั้งค่าสำคัญ |
|------|---------------|-------------------|
| **OAuth2‑Proxy** | ตรวจสอบ JWT จาก Supabase และส่งต่อข้อมูลผู้ใช้ให้ backend | `SUPABASE_PROJECT_ID`, `OAUTH2_PROXY_CLIENT_ID`, `OAUTH2_PROXY_CLIENT_SECRET`, `OAUTH2_PROXY_COOKIE_SECRET` |
| **Supavisor Pooler** | Supabase connection pooler (IPv4 transaction mode) ที่ backend services ใช้เชื่อม Postgres จาก ACA | host: `aws-1-ap-northeast-1.pooler.supabase.com:6543`, param `?prepareThreshold=0` |

---

## 6. ขั้นตอน Deploy
1. **พัฒนาในเครื่อง** – รัน `docker compose up --build` เพื่อสตาร์ทสแต็กทั้งหมด; gateway จะรับที่ `http://localhost`
2. **CI/CD** – Workflow `aca-deploy.yml` จะ build image แต่ละไฟล์, push ไป GHCR, แล้ว deploy ไป Azure Container Apps
3. **Production** – Azure Container Apps เปิด ingress ของ `gateway` เป็น **External** ส่วน `frontend` และ `backend` ตั้งเป็น **Internal** เพื่อจำกัดการเข้าถึงจาก VNet เท่านั้น (บังคับให้เข้าเว็บมือผ่าน Nginx อย่างเดียว)

---

## 7. ตัวแปรสภาพแวดล้อม (ดู KEYS_SUMMARY.md สำหรับรายละเอียดเต็ม)
- `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` – ใช้ใน Frontend
- `SUPABASE_DB_USERNAME`, `SUPABASE_DB_PASSWORD`, `SUPABASE_SERVICE_ROLE_KEY` – ใช้ใน Backend (JDBC, Storage)
- `SUPABASE_PROJECT_ID` – ใช้โดย OAuth2‑Proxy สำหรับ OIDC
- `AZURE_CREDENTIALS`, `RESOURCE_GROUP` – ใช้ใน CI/CD

---

*เอกสารนี้ให้มุมมองครบถ้วนของโครงสร้างพื้นฐานที่ใช้ในโปรเจกต์ Sandbox*
