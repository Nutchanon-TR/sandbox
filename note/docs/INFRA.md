# Infrastructure - Current Port Map

เอกสารนี้สรุป port/runtime ของโปรเจกต์ตอนนี้ โดยแยกเป็น 3 โหมด:

- local dev: รัน Next.js/Spring Boot ตรงบนเครื่อง ไม่ผ่าน Docker gateway
- docker-on-local: รัน app stack ด้วย Docker Compose บนเครื่องเรา
- cloud: deploy ขึ้น Azure Container Apps ผ่าน GitHub Actions

---

## Port Owners

| โหมด | ไฟล์ที่คุม port หลัก |
|---|---|
| local dev บนเครื่อง | `backend/*/src/main/resources/application-local.yml`, `frontend/next.config.ts` |
| docker-on-local | `docker-compose.yml`, `docker-compose.local.yml`, `docker-compose.monitoring.yml` |
| cloud ACA | `.github/workflows/aca-deploy.yml` |
| monitoring cloud config | `monitoring/prometheus/prometheus.cloud.yml`, `monitoring/grafana/cloud/**` |

---

## Local Dev ไม่ผ่าน Docker

โหมดนี้ใช้ตอนรันจาก IDE/terminal โดยตรง เช่น `npm run dev` และ Spring Boot profile `local`

| Service | Local URL/Port | คุมจาก |
|---|---:|---|
| frontend | `http://localhost:3000` | `frontend/package.json` |
| user-service | `localhost:8080` | `backend/user/src/main/resources/application-local.yml` |
| chat-service | `localhost:8081` | `backend/chatapp/src/main/resources/application-local.yml` |
| bpost-service | `localhost:8082` | `backend/bpost/src/main/resources/application-local.yml` |

Frontend dev rewrite อยู่ใน `frontend/next.config.ts`:

| Frontend path | Default destination |
|---|---|
| `/v1/api/user/*` | `http://localhost:8080` |
| `/v1/api/chat-app/*` | `http://localhost:8081` |
| `/v1/api/b-post/*` | `http://localhost:8082` |

---

## Docker-On-Local

โหมดนี้จำลองมุมมองแบบ cloud ให้ gateway ยิง service ทุกตัวผ่าน port `80` เหมือน ACA internal ingress

### Host Ports

| เข้าเองจากเครื่องเรา | Container ปลายทาง |
|---|---|
| `http://localhost:8088` | `gateway:80` |
| `http://localhost:9090` | `prometheus:9090` |
| `http://localhost:3001` | `grafana:3000` |

`docker-compose.local.yml` override gateway host port:

```yaml
services:
  gateway:
    ports: !override
      - "8088:80"
```

### Internal Docker Ports

ใน Docker network, gateway เห็นทุก app service เป็น port `80`:

| Gateway ยิงไป | Container listen จริงใน docker-on-local |
|---|---:|
| `frontend` | `80` |
| `user-service` | `80` |
| `chat-service` | `80` |
| `bpost-service` | `80` |
| `oauth2-proxy` | `80` |

ค่าที่ gateway ใช้ใน `docker-compose.yml`:

```text
FRONTEND_PORT=80
CHAT_PORT=80
BACKEND_PORT=80
BPOST_PORT=80
OAUTH2_PROXY_PORT=80
```

แต่ละ service ถูกบังคับให้ listen port `80` เฉพาะตอนรัน Docker local:

| Service | Docker local runtime env |
|---|---|
| frontend | `PORT=80`, `HOSTNAME=0.0.0.0` |
| user-service | `SERVER_PORT=80` |
| chat-service | `SERVER_PORT=80` |
| bpost-service | `SERVER_PORT=80` |
| oauth2-proxy | `--http-address=0.0.0.0:80` |

Docker local ใช้ CORS แบบเดียวกับ cloud คือ backend allow origin ของ gateway ด้านหน้า:

```text
CORS_ALLOWED_ORIGINS=http://localhost:8088
```

ค่านี้อยู่ใน `docker-compose.local.yml` เพราะเป็น local-only override ส่วน cloud ใช้ gateway/site URL จาก GitHub Actions

`CORS_ALLOWED_ORIGINS` รองรับหลาย origin แบบ comma-separated เช่น `https://a.com,https://b.com`

คำสั่งรัน local stack:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.monitoring.yml up -d --build
```

คำสั่งหยุด:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.monitoring.yml down
```

---

## Cloud - Azure Container Apps

บน cloud ผู้ใช้เข้า public URL ของ `gateway-service` ก่อน จากนั้น gateway ยิง service อื่นผ่าน ACA internal ingress ที่ port `80`

```text
Internet
  -> gateway-service:80
  -> service-name:80
  -> ACA forwards to container targetPort
```

Gateway cloud env ใน `.github/workflows/aca-deploy.yml`:

```text
FRONTEND_PORT=80
CHAT_PORT=80
BACKEND_PORT=80
BPOST_PORT=80
OAUTH2_PROXY_PORT=80
```

Backend บน cloud allow origin ของ gateway/site ผ่าน env `CORS_ALLOWED_ORIGINS` ที่ workflow ส่งเข้า backend ทั้งสามตัว:

```text
CORS_ALLOWED_ORIGINS=${{ vars.CORS_ALLOWED_ORIGINS || secrets.NEXT_PUBLIC_SITE_URL }}
```

ค่า default ใน `application.yml` เหลือแค่ `http://localhost:3000` สำหรับ local dev ตรงบนเครื่อง ถ้า cloud เปลี่ยนเป็น custom domain ให้ตั้ง GitHub variable `CORS_ALLOWED_ORIGINS` เป็น domain นั้น

ACA target ports:

| Container App | Ingress | targetPort จริง |
|---|---|---:|
| `gateway-service` | external | `80` |
| `frontend` | internal | `3000` |
| `user-service` | internal | `8080` |
| `chat-service` | internal | `8081` |
| `bpost-service` | internal | `8080` |
| `oauth2-proxy` | internal | `4180` |
| `prometheus-service` | internal | `9090` |
| `grafana-service` | external | `3000` |

ดังนั้น cloud กับ docker-on-local เหมือนกันในมุมของ gateway:

```text
gateway -> service:80
```

แต่ต่างกันที่ cloud มี ACA ingress เป็นตัว forward เข้า targetPort จริง ส่วน docker-on-local ให้ container listen `80` โดยตรง

---

## Gateway Routing

ไฟล์: `gateway/nginx.conf.template`

```nginx
upstream frontend      { server frontend:${FRONTEND_PORT}; }
upstream chat-service  { server chat-service:${CHAT_PORT}; }
upstream bpost-service { server bpost-service:${BPOST_PORT}; }
upstream user-service  { server user-service:${BACKEND_PORT}; }
upstream oauth2-proxy  { server oauth2-proxy:${OAUTH2_PROXY_PORT}; }
```

Routes:

| Route | Upstream | Auth |
|---|---|---|
| `/` | `frontend` | no `auth_request` |
| `/v1/api/user/` | `user-service` | oauth2-proxy |
| `/v1/api/chat-app/` | `chat-service` | oauth2-proxy |
| `/v1/api/b-post/` | `bpost-service` | oauth2-proxy |
| `/v1/api/b-post/ws/` | `bpost-service` | STOMP JWT, no `auth_request` |
| `/oauth2/` | `oauth2-proxy` | OAuth2 endpoints |

---

## Monitoring

มี monitoring config 2 ชุด:

| โหมด | ตัวรัน | Config หลัก |
|---|---|---|
| local docker monitoring | `docker-compose.monitoring.yml` | `monitoring/prometheus/prometheus.yml` |
| cloud self-host monitoring | `.github/workflows/aca-deploy.yml` | `monitoring/prometheus/prometheus.cloud.yml`, `monitoring/grafana/cloud/**` |

Local Prometheus scrape targets ตอนนี้ใช้ port `80` เพื่อให้เหมือน gateway/cloud view:

```text
user-service:80/actuator/prometheus
chat-service:80/actuator/prometheus
bpost-service:80/actuator/prometheus
```

Grafana local:

```text
http://localhost:3001
admin / admin
```

Prometheus local:

```text
http://localhost:9090
```

---

## Port Duplication Rule

สิ่งที่ซ้ำได้:

```text
container A:80
container B:80
container C:80
```

เพราะแต่ละ container มี network namespace/IP ของตัวเอง

สิ่งที่ซ้ำไม่ได้:

```text
localhost:8080 -> container A
localhost:8080 -> container B
```

เพราะ host port บนเครื่องเดียวกัน bind ได้ทีละ process/container เท่านั้น

ACA ก็แนวคิดคล้ายกัน: แต่ละ Container App มี ingress/network แยกของตัวเอง จึงมีหลาย app ที่มองจาก gateway เป็น `service:80` ได้ แล้ว ACA ค่อย forward เข้า targetPort จริงของแต่ละ container

---

## Notes

- Chatapp generated images are controlled by chat-service env:
  - `IMAGE_GENERATION_ENABLED=false` disables Cloudflare calls and keeps text fallback behavior.
  - `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN` are required when image generation is enabled.
  - `CLOUDFLARE_IMAGE_MODEL` defaults to `@cf/black-forest-labs/flux-1-schnell`.
  - Generated image files are uploaded to Supabase Storage bucket `images`.

- docker-on-local ต้องรันพร้อม `docker-compose.local.yml` ถ้าไม่อยากให้ gateway bind host port `80`
- `docker-compose.yml` ยังมี `80:80` เป็น base config แต่ local dev ปกติใช้ override เป็น `8088:80`
- local dev ไม่ผ่าน Docker ยังใช้ port `3000`, `8080`, `8081`, `8082` เหมือนเดิม
- ถ้าเปิดเฉพาะ monitoring ใน Docker แต่ backend รันบนเครื่องตรง ๆ ต้องเปลี่ยน Prometheus target เป็น `host.docker.internal:<port>`
