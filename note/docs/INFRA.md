# INFRA.md

## ภาพรวม
เอกสารนี้อธิบายสถาปัตยกรรมการ Deploy ทั้งหมดของโปรเจกต์ **Sandbox** โดยใช้ Docker คอนเทนเนอร์ที่จัดการด้วย **docker‑compose** และเปิดให้เข้าถึงจากภายนอกผ่าน **Nginx** gateway มี side‑car อย่าง **OAuth2‑Proxy** และ **Redis** สำหรับการยืนยันตัวตนและแคช

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

### 1.3 Backend Services – `backend/{chatapp,dinner,bpost}/Dockerfile`
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
```nginx
worker_processes 1;

events { worker_connections 1024; }

http {
    include       mime.types;
    default_type  application/octet-stream;

    # Upstream ของบริการใน Docker Compose (ใช้ตัวแปรเพื่อรองรับ Local และ Cloud)
    upstream frontend { server frontend:${FRONTEND_PORT}; }
    upstream chat_backend { server chat-service:${BACKEND_PORT}; }
    upstream dinner_backend { server dinner-service:${BACKEND_PORT}; }
    upstream bpost_backend { server bpost-service:${BACKEND_PORT}; }
    upstream oauth2_proxy { server oauth2-proxy:${OAUTH2_PROXY_PORT}; }

    server {
        listen 80;
        server_name localhost;

        # จุดเชื่อมต่อ OAuth2‑Proxy
        location /oauth2/ {
            proxy_pass http://oauth2_proxy;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Scheme $scheme;
            proxy_set_header X-Auth-Request-Redirect $request_uri;
        }

        # เส้นทางของ Frontend
        location / {
            proxy_pass http://frontend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        }

        # API ของ Backend – ยกเลิกคอมเม้นต์ auth_request เพื่อเปิดการตรวจสอบ JWT
        location /v1/api/chat/ {
            # auth_request /oauth2/auth;
            proxy_pass http://chat_backend/v1/api/chat/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        location /v1/api/supplier-order/ {
            proxy_pass http://dinner_backend/v1/api/supplier-order/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        location /v1/api/bpost/ {
            proxy_pass http://bpost_backend/v1/api/bpost/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
        location /v1/api/report/ {
            proxy_pass http://bpost_backend/v1/api/bpost/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
}
```
* กำหนดเส้นทางจากภายนอกไปยังคอนเทนเนอร์ที่เกี่ยวข้อง
* หากต้องการเปิดการตรวจสอบ JWT ให้เอา `#` ออกจากบรรทัด `auth_request`

---

## 3. docker‑compose – `docker-compose.yml`
```yaml
version: '3.8'

services:
  # ------------------------------------------------
  # API Gateway (Nginx)
  # ------------------------------------------------
  gateway:
    build:
      context: ./gateway
      dockerfile: Dockerfile
    ports:
      - "80:80"
    depends_on:
      - frontend
      - chat-service
      - dinner-service
      - bpost-service
      - oauth2-proxy
    networks:
      - sandbox_net
    restart: unless-stopped

  # ------------------------------------------------
  # OAuth2 Validation Sidecar
  # ------------------------------------------------
  oauth2-proxy:
    image: quay.io/oauth2-proxy/oauth2-proxy:v7.6.0
    command:
      - "--http-address=0.0.0.0:4180"
      - "--provider=oidc"
      - "--oidc-issuer-url=https://${SUPABASE_PROJECT_ID:-YOUR_SUPABASE_ID}.supabase.co"
      - "--skip-provider-button=true"
      - "--email-domain=*"
      - "--cookie-secret=OAUTH2_PROXY_COOKIE_SECRET_32_BYTES"
      - "--cookie-secure=false" # production ให้ตั้งเป็น true
      - "--upstream=http://dummy" # Nginx จะทำ reverse ไปยัง OAuth
      - "--skip-jwt-bearer-tokens=true"
      - "--extra-jwt-issuers=https://${SUPABASE_PROJECT_ID:-YOUR_SUPABASE_ID}.supabase.co=https://${SUPABASE_PROJECT_ID:-YOUR_SUPABASE_ID}.supabase.co"
    environment:
      - OAUTH2_PROXY_CLIENT_ID=${OAUTH2_PROXY_CLIENT_ID:-DUMMY}
      - OAUTH2_PROXY_CLIENT_SECRET=${OAUTH2_PROXY_CLIENT_SECRET:-DUMMY}
    networks:
      - sandbox_net
    restart: unless-stopped

  # ------------------------------------------------
  # Frontend (Next.js)
  # ------------------------------------------------
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    networks:
      - sandbox_net
    environment:
      - NEXT_PUBLIC_API_URL=http://localhost
    restart: unless-stopped

  # ------------------------------------------------
  # Backend Services
  # ------------------------------------------------
  chat-service:
    build:
      context: ./backend/chatapp
      dockerfile: Dockerfile
    networks:
      - sandbox_net
    restart: unless-stopped

  dinner-service:
    build:
      context: ./backend/dinner
      dockerfile: Dockerfile
    networks:
      - sandbox_net
    restart: unless-stopped

  bpost-service:
    build:
      context: ./backend/bpost
      dockerfile: Dockerfile
    networks:
      - sandbox_net
    restart: unless-stopped

  # ------------------------------------------------
  # Redis Cache กลาง (Phase 2)
  # ------------------------------------------------
  redis:
    image: redis:7.2-alpine
    ports:
      - "6379:6379"
    networks:
      - sandbox_net
    restart: unless-stopped

networks:
  sandbox_net:
    driver: bridge
```
* กำหนดคอนเทนเนอร์ทั้งหมด, การเชื่อมต่อเครือข่าย, และนโยบาย restart
* `gateway` เป็นจุดเข้าถึงทั้งหมดและทำ reverse‑proxy ไปยังบริการที่เกี่ยวข้อง (Frontend และ Backends คุยผ่าน Gateway)
* Redis จะใช้ในขั้นตอนต่อไป (Phase 2) เพื่อแคช

---

## 5. ส่วนประกอบสนับสนุน

| ส่วน | วัตถุประสงค์ | ค่าตั้งค่าสำคัญ |
|------|---------------|-------------------|
| **OAuth2‑Proxy** | ตรวจสอบ JWT จาก Supabase และส่งต่อข้อมูลผู้ใช้ให้ backend | `SUPABASE_PROJECT_ID`, `OAUTH2_PROXY_CLIENT_ID`, `OAUTH2_PROXY_CLIENT_SECRET`, `OAUTH2_PROXY_COOKIE_SECRET` |
| **Redis** | Cache กลางสำหรับ session, rate‑limiting หรือ chat messages (Phase 2) | เปิดพอร์ต `6379` |

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
