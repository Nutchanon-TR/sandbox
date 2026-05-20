# Sandbox

Sandbox is a microservice playground with a Next.js frontend, Spring Boot backend services, an Nginx API gateway, Supabase Auth/Postgres/Storage, AI chat, B-Post social features, realtime messaging, and local/cloud monitoring.

Architecture notes and deeper design docs live in `note/docs/` and `note/arch/`.

## Current Architecture

```text
Browser
  |
  v
gateway/                         Nginx reverse proxy + oauth2-proxy auth_request
  |
  +-- frontend/                   Next.js App Router UI
  +-- backend/user/               user sync + profile APIs
  +-- backend/chatapp/            AI chat, rooms, history, legacy SyncHub APIs
  +-- backend/bpost/              social feed, friends, messages, realtime, storage
      ^
      |
backend/common-auth/             shared Supabase JWT decoder + current-user resolver

External services:
- Supabase Auth, Postgres, Storage
- Groq-compatible OpenAI API endpoint
- HuggingFace feature-extraction API
```

## Tech Stack

- Frontend: Next.js 15, React 19, TypeScript, Tailwind CSS 4, Ant Design 5, Zustand, Axios
- Auth: Supabase Auth via `@supabase/ssr`, OAuth callback at `/auth/callback`
- Backend: Spring Boot 3.2.5, Spring Data JPA/JDBC, Bean Validation, Lombok
- Shared auth: `backend/common-auth` resolves Supabase `sub` into `chat_app.users.id`
- AI: Spring AI OpenAI client pointed at Groq, with Resilience4j retry/circuit breaker
- Embeddings: HuggingFace `intfloat/multilingual-e5-small` and pgvector in Supabase Postgres
- Realtime: STOMP over SockJS for B-Post messages, notifications, and presence
- Storage: Supabase Storage bucket `images`
- Gateway: Nginx + oauth2-proxy v7.6.0
- Monitoring: Prometheus + Grafana
- Deployment: Docker, Docker Compose, GitHub Actions, GHCR, Azure Container Apps

## Repository Layout

```text
Sandbox/
|- frontend/                   Next.js app, providers, routes, UI components
|- backend/
|  |- common-auth/             shared JWT/current-user library
|  |- user/                    user sync and profile service
|  |- chatapp/                 chat, AI response, embeddings, rooms, SyncHub APIs
|  \- bpost/                   posts, comments, friends, messages, websocket APIs
|- gateway/                    Nginx image and routing template
|- monitoring/                 Prometheus and Grafana config/images
|- note/                       docs, architecture diagrams, reports, Postman files
|- docker-compose.yml          base Docker stack
|- docker-compose.local.yml    local gateway/CORS overrides
|- docker-compose.monitoring.yml
|- .env.example                root env template
\- .github/workflows/          Azure Container Apps deploy workflow
```

## Frontend Routes

| Route | Purpose |
|---|---|
| `/` | Home page |
| `/login` | Supabase OAuth login |
| `/auth/callback` | Exchanges OAuth code for a Supabase session |
| `/chat-app/message` | AI chat with rooms and message history |
| `/b-post/blog` | Feed, composer, comments, likes, notifications |
| `/b-post/socials` | Friends, friend requests, user search, open conversation |
| `/b-post/messages` | Conversations, chat history, image messages, realtime updates |
| `/b-post/profile/[supabaseUid]` | B-Post user profile and authored posts |

## Gateway Routing

`gateway/nginx.conf.template` is the public API entry point.

| Path | Upstream | Auth |
|---|---|---|
| `/` | `frontend` | Next.js middleware/session guard |
| `/oauth2/*` | `oauth2-proxy` | oauth2-proxy endpoints |
| `/v1/api/user/*` | `user-service` | `auth_request /oauth2/auth`, CORS preflight support |
| `/v1/api/chat-app/*` | `chat-service` | `auth_request /oauth2/auth` |
| `/v1/api/b-post/*` | `bpost-service` | `auth_request /oauth2/auth` |
| `/v1/api/b-post/ws/*` | `bpost-service` | STOMP `CONNECT` carries the JWT; no nginx `auth_request` |

Backend services still receive `Authorization: Bearer <jwt>`. `common-auth` decodes the Supabase JWT payload, checks `exp`, reads the `sub`, and resolves it to the internal `chat_app.users.id`.

## Backend Services

### User Service (`backend/user`)

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/v1/api/user/sync` | Resolve/upsert a Supabase user into `chat_app.users` |
| `GET` | `/v1/api/user/profile/{supabaseUid}` | Read a profile |
| `POST` | `/v1/api/user/profile` | Create or update a profile |

### Chat Service (`backend/chatapp`)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/v1/api/chat-app/room/list` | List caller's rooms |
| `POST` | `/v1/api/chat-app/room/create` | Create a chat room |
| `GET` | `/v1/api/chat-app/chat/history/{roomId}` | Load cursor-based chat history |
| `POST` | `/v1/api/chat-app/chat` | Send a message, create embedding, call Groq, persist reply |
| `GET` | `/v1/api/chat-app/blog/list` | Legacy SyncHub AI list |
| `GET` | `/v1/api/chat-app/blog/detail/{aiId}` | Legacy SyncHub AI detail |
| `POST/DELETE` | `/v1/api/chat-app/ai/{aiId}/like` | Like/unlike AI card |
| `POST/DELETE` | `/v1/api/chat-app/ai/{aiId}/friend` | Add/remove AI friend |
| `GET` | `/v1/api/chat-app/user/friends` | List AI friends |
| `GET/POST` | `/v1/api/chat-app/ai/{aiId}/comments` | List/add AI comments |

### B-Post Service (`backend/bpost`)

| Area | Method | Path |
|---|---|---|
| Posts | `POST` | `/v1/api/b-post/posts` |
| Posts | `GET` | `/v1/api/b-post/posts/feed` |
| Posts | `GET/PATCH/DELETE` | `/v1/api/b-post/posts/{postId}` |
| Posts | `GET` | `/v1/api/b-post/posts/by-author/{authorId}` |
| Images | `POST` | `/v1/api/b-post/posts/upload-image` |
| Legacy image | `POST` | `/v1/api/b-post/blog/upload-image` |
| Comments | `GET/POST` | `/v1/api/b-post/posts/{postId}/comments` |
| Comments | `PATCH/DELETE` | `/v1/api/b-post/comments/{commentId}` |
| Likes | `POST/DELETE` | `/v1/api/b-post/posts/{postId}/likes` |
| Friends | `GET` | `/v1/api/b-post/friends` |
| Friends | `POST` | `/v1/api/b-post/friends/requests` |
| Friends | `POST` | `/v1/api/b-post/friends/requests/{id}/accept` |
| Friends | `POST` | `/v1/api/b-post/friends/requests/{id}/decline` |
| Friends | `GET` | `/v1/api/b-post/friends/requests/incoming` |
| Friends | `GET` | `/v1/api/b-post/friends/requests/outgoing` |
| Users | `GET` | `/v1/api/b-post/users/search?q=...` |
| Messages | `GET/POST` | `/v1/api/b-post/conversations` |
| Messages | `GET` | `/v1/api/b-post/conversations/{conversationId}/messages` |
| Messages | `POST` | `/v1/api/b-post/conversations/{conversationId}/read` |
| Messages | `POST` | `/v1/api/b-post/messages` |
| Images | `POST` | `/v1/api/b-post/messages/upload-image` |
| Notifications | `GET` | `/v1/api/b-post/notifications` |
| Notifications | `GET` | `/v1/api/b-post/notifications/unread-count` |
| Notifications | `POST` | `/v1/api/b-post/notifications/{id}/read` |
| Presence | `GET` | `/v1/api/b-post/presence/online` |
| WebSocket | `STOMP` | SockJS endpoint `/v1/api/b-post/ws`, app destinations `/app/message.send` and `/app/message.read` |

## Environment

Copy the root env template for Docker/backend/gateway settings:

```powershell
Copy-Item .env.example .env
```

Important root variables:

| Variable | Used by |
|---|---|
| `SUPABASE_DB_USERNAME` / `SUPABASE_DB_PASSWORD` | Spring datasource |
| `SUPABASE_URL` | Supabase REST/Storage base URL |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase Storage uploads |
| `GROK_API_KEY` | Groq/Spring AI client |
| `HUGGINGFACE_API_KEY` | Embedding service |
| `SUPABASE_PROJECT_ID` | oauth2-proxy issuer config |
| `OAUTH2_PROXY_CLIENT_ID` / `OAUTH2_PROXY_CLIENT_SECRET` | oauth2-proxy |
| `OAUTH2_PROXY_COOKIE_SECRET` | oauth2-proxy cookie encryption |
| `CORS_ALLOWED_ORIGINS` | backend allowed origins |

Frontend local development uses `frontend/.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://<project-id>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon-or-publishable-key>
NEXT_PUBLIC_SITE_URL=http://localhost:3000
NEXT_PUBLIC_AUTH_REDIRECT_URL=

# Keep empty for relative URLs and Next.js rewrites in dev.
NEXT_PUBLIC_API_URL=
NEXT_PUBLIC_USER_API_URL=

BACKEND_USER_URL=http://localhost:8080
BACKEND_CHAT_URL=http://localhost:8081
BACKEND_BPOST_URL=http://localhost:8082
```

`NEXT_PUBLIC_*` values are baked into the frontend bundle during `next build` and must be provided as Docker build args for production images.

## Local Development Without Docker

Install the shared auth library first:

```powershell
cd backend\chatapp
.\mvnw.cmd -f ..\common-auth\pom.xml install -DskipTests
```

Run each backend with the `local` Spring profile:

```powershell
cd backend\user
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# http://localhost:8080

cd backend\chatapp
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# http://localhost:8081

cd backend\bpost
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
# http://localhost:8082
```

Run the frontend:

```powershell
cd frontend
npm install
npm run dev
# http://localhost:3000
```

In development, `frontend/next.config.ts` rewrites:

| Frontend path | Default destination |
|---|---|
| `/v1/api/user/*` | `http://localhost:8080` |
| `/v1/api/chat-app/*` | `http://localhost:8081` |
| `/v1/api/b-post/*` | `http://localhost:8082` |

## Docker-On-Local

The Docker local stack mirrors the cloud gateway view: the gateway calls app containers on port `80` inside the Docker network.

Start the app stack with monitoring:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.monitoring.yml up -d --build
```

Open:

| URL | Service |
|---|---|
| `http://localhost:8088` | Gateway / app |
| `http://localhost:9090` | Prometheus |
| `http://localhost:3001` | Grafana (`admin` / `admin`) |

Stop:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.monitoring.yml down
```

## Monitoring

Local monitoring is defined by:

- `docker-compose.monitoring.yml`
- `monitoring/prometheus/prometheus.yml`
- `monitoring/grafana/provisioning/`
- `monitoring/grafana/dashboards/`

Prometheus scrapes:

```text
user-service:80/actuator/prometheus
chat-service:80/actuator/prometheus
bpost-service:80/actuator/prometheus
```

The GitHub Actions workflow also builds and deploys:

- `prometheus-service`: internal ACA ingress, target port `9090`
- `grafana-service`: external ACA ingress, target port `3000`

See `monitoring/README.md` for details.

## Cloud Deployment

`.github/workflows/aca-deploy.yml` builds and deploys container apps to Azure Container Apps:

| Container app | Ingress | Target port |
|---|---|---:|
| `gateway-service` | external | `80` |
| `frontend` | internal | `3000` |
| `user-service` | internal | `8080` |
| `chat-service` | internal | `8081` |
| `bpost-service` | internal | `8080` |
| `oauth2-proxy` | internal | `4180` |
| `prometheus-service` | internal | `9090` |
| `grafana-service` | external | `3000` |

From the gateway's point of view, ACA internal ingress exposes services at `service-name:80`, so the gateway is configured with:

```text
FRONTEND_PORT=80
CHAT_PORT=80
BACKEND_PORT=80
BPOST_PORT=80
OAUTH2_PROXY_PORT=80
```

The workflow includes an ingress guardrail job that expects only `gateway-service` and `grafana-service` to be public.

## Build And Test

Frontend:

```powershell
cd frontend
npm run build
npm run test
```

Backend:

```powershell
cd backend\chatapp
.\mvnw.cmd -f ..\common-auth\pom.xml test
.\mvnw.cmd test
```

## Current Notes

- Local dev bypasses the gateway and uses Next.js rewrites to direct Spring ports.
- Cloud REST APIs are expected to pass through `gateway-service` and oauth2-proxy.
- `common-auth` decodes the JWT payload and trusts the gateway/oauth2-proxy as the signature verification boundary. Do not expose backend services directly in production.
- `/v1/api/b-post/ws/` is handled differently because STOMP sends the JWT in the `CONNECT` frame.
- Docker Compose does not provision a local Supabase/Postgres emulator; the app uses the configured Supabase project.
- Some legacy ChatApp/B-Post endpoints remain for compatibility.

## Additional Docs

- [Authentication Flow](./note/docs/AUTH.md)
- [ChatApp Feature Design](./note/docs/CHATAPP.md)
- [Infrastructure / Port Map](./note/docs/INFRA.md)
- [API Keys Configuration](./note/docs/KEYS.md)
- [Supabase Specification](./note/docs/SUPABASE.md)
- [Provider Notes](./note/docs/PROVIDER.md)
- [Pooler Notes](./note/docs/POOLER.md)
- [Monitoring](./monitoring/README.md)
- [Roadmap](./note/ROADMAP.md)
