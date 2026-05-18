# Sandbox Architecture Action Report

> อัปเดต 2026-05-13 | โฟกัส: งาน architecture หลังระบบหลักเริ่มใช้งานได้

---

## ภาพรวมปัจจุบัน

ระบบตอนนี้มีแกนหลักครบแล้ว: Next.js frontend, Nginx gateway, Spring Boot microservices, Supabase Auth/Postgres/Storage, GitHub Actions, Docker และ Azure Container Apps. จุดที่ควรขยับต่อไม่ใช่ feature ใหม่อย่างเดียว แต่เป็น production readiness: observability, security edge, infrastructure as code, caching, CI/CD guardrails และ operational runbook

รายงานนี้ตัดส่วนสถานะปัญหาเดิมออกแล้ว เพื่อให้ใช้เป็น action backlog สำหรับ architecture ต่อจากนี้

---

## สิ่งที่ยังขาด

| Area | สิ่งที่ขาด | ทำไมสำคัญ |
|---|---|---|
| Observability | logs, metrics, traces, dashboards, alerts แบบรวมศูนย์ | ตอน production มีปัญหาจะรู้เร็วว่าเสียที่ frontend, gateway, service, DB หรือ external API |
| Edge Security | Cloudflare DNS/WAF/rate limiting และ origin protection | กัน traffic แปลก, brute force, spike API และช่วยซ่อน origin |
| Infrastructure as Code | Terraform/Bicep สำหรับ Azure resources, secrets, DNS, Redis, monitoring | ลด config drift และ rebuild environment ได้ |
| Caching | Redis/cache strategy สำหรับข้อมูลอ่านซ้ำและ realtime state | ลด DB load และรองรับ scale-out หลาย replica |
| CI/CD Guardrails | test/build/scan/deploy gates ที่ชัด | กัน deploy image เสีย, dependency risk และ config ผิด |
| Secrets & Identity | Key Vault/managed identity/GitHub OIDC | ลด secret leakage และไม่ต้องใช้ long-lived credentials |
| Database Governance | migrations, RLS, advisors, backups, restore drill | กัน schema drift, data leak และ recover ได้เมื่อพลาด |
| Resilience | timeout, retry budget, circuit breaker, health checks | กัน service ค้างลากทั้งระบบล้ม |
| Cost Control | budget, quota, scale rules, retention policy | กันค่าใช้จ่ายบานจาก logs, traffic, AI calls และ DB |

---

## Architecture Direction

แนวทางที่แนะนำคืออย่าเพิ่มทุกอย่างพร้อมกัน ให้ทำเป็น 3 ชั้น:

1. **Operate** - ทำให้เห็นระบบก่อน: Azure Monitor/Log Analytics, OpenTelemetry, alerts, health dashboards
2. **Protect** - ป้องกันขอบระบบ: Cloudflare, rate limiting, CORS/origin policy, Key Vault, RLS
3. **Codify & Scale** - ทำให้ซ้ำได้และขยายได้: Terraform, Redis, CI/CD gates, load test, runbooks

ลำดับนี้เหมาะกับโปรเจกต์นี้ เพราะถ้าเริ่มจาก Terraform หรือ cache ก่อน แต่ยังไม่มี monitor จะไล่สาเหตุยากมากเมื่อ deploy แล้วเจอปัญหา

---

## Actions

### A1 - Baseline Observability

**Priority:** P0  
**Where:** Azure Container Apps, Azure Monitor, Log Analytics  
**Goal:** เห็น logs/metrics ของ frontend, gateway และทุก backend service ในที่เดียว

**Tasks:**
1. สร้าง Log Analytics workspace สำหรับ ACA environment
2. เปิด container app logs และ system logs
3. ทำ dashboard สำหรับ request count, error rate, latency, CPU, memory, restart count
4. ตั้ง alert ขั้นต่ำ:
   - 5xx spike
   - container restart
   - CPU/memory สูงต่อเนื่อง
   - gateway unhealthy
5. กำหนด log retention เพื่อคุม cost

**Done when:** มี dashboard กลางและ alert ยิงได้จริงจาก incident จำลอง

---

### A2 - Distributed Tracing with OpenTelemetry

**Priority:** P0  
**Where:** Spring Boot services, ACA OpenTelemetry, Azure Monitor หรือ vendor ที่เลือก  
**Goal:** ตาม request เดียวจาก gateway ไป service และ DB/external API ได้

**Tasks:**
3. propagate trace headers ผ่าน gateway
4. ใส่ correlation id ใน logs
5. ตรวจ trace สำหรับ flow สำคัญ:
   - login -> `/v1/api/user/sync`
   - chat -> Groq/HuggingFace
   - b-post message -> notification

**Done when:** เปิด trace แล้วเห็น latency แตกตาม service ได้ ไม่ใช่เห็นแค่ request รวม

---

### A3 - Cloudflare Edge Protection

**Priority:** P0  
**Where:** Cloudflare DNS/WAF/Rate Limiting  
**Goal:** ให้ public traffic ผ่าน edge ก่อนถึง ACA gateway

**Tasks:**
1. ชี้ domain/subdomain ไปที่ ACA gateway FQDN
2. เปิด proxy mode สำหรับ public hostname
3. ตั้ง WAF/rate limiting สำหรับ:
   - `/login`
   - `/auth/*`
   - `/v1/api/*`
   - upload endpoints
4. ตั้ง cache bypass สำหรับ authenticated API
5. จำกัด allowed origins ใน backend ให้เหลือ localhost dev + production domain
6. วางแผน origin protection เพื่อไม่ให้ยิง ACA FQDN ตรงได้ง่าย

**Done when:** API ผ่าน domain จริง, rate limit ทำงาน, CORS ไม่เปิดกว้าง และ origin URL ไม่ใช่ primary entrypoint

---

### A4 - Infrastructure as Code

**Priority:** P1  
**Where:** `infra/terraform` หรือ `infra/bicep`  
**Goal:** สร้างและแก้ Azure resources ผ่าน code ไม่ใช่ portal click

**Tasks:**
1. สร้าง module สำหรับ:
   - resource group
   - Log Analytics workspace
   - Container Apps environment
   - frontend/gateway/backend container apps
   - Key Vault
   - managed identities
   - Redis/cache
2. แยก environment: `dev`, `prod`
3. ตั้ง remote state storage
4. เพิ่ม `terraform fmt`, `terraform validate`, `terraform plan` ใน GitHub Actions
5. ใช้ manual approval ก่อน `terraform apply` สำหรับ prod

**Done when:** ลบ environment แล้ว recreate จาก IaC ได้โดยไม่ต้องจำขั้นตอนใน portal

---

### A5 - Secrets and Identity Hardening

**Priority:** P1  
**Where:** Azure Key Vault, GitHub Actions, ACA secrets  
**Goal:** ลด long-lived secrets และแยก secret/runtime config ชัดเจน

**Tasks:**
1. ย้าย secrets runtime ไป Azure Key Vault หรือ ACA secrets
2. ใช้ managed identity ให้ container apps อ่าน secrets เท่าที่จำเป็น
3. ใช้ GitHub OIDC แทน service principal secret แบบยาว
4. ตั้ง secret rotation checklist
5. ตรวจว่า frontend ไม่มี `service_role` หรือ secret key ที่ expose ผ่าน `NEXT_PUBLIC_*`

**Done when:** GitHub ไม่มี cloud credential อายุยาว และ service แต่ละตัวเห็นเฉพาะ secret ที่ต้องใช้

---

### A6 - Redis and Cache Strategy

**Priority:** P1  
**Where:** Azure Cache for Redis หรือ managed Redis provider  
**Goal:** ลด DB load และเตรียม scale-out realtime

**Tasks:**
1. เลือก cache use cases ก่อนเพิ่ม dependency:
   - chat history page cache แบบ TTL สั้น
   - user/profile summary cache
   - friend/search result cache
   - presence online set สำหรับ b-post realtime
2. กำหนด key format เช่น `chat:history:{roomId}:{cursor}:{limit}`
3. กำหนด invalidation rule ต่อ write action
4. หลีกเลี่ยง `KEYS` ใน production ให้ใช้ explicit keys หรือ SCAN pattern อย่างระวัง
5. ใส่ fallback ถ้า Redis down ให้ยังอ่าน DB ได้

**Done when:** endpoint ที่อ่านซ้ำบ่อย latency ลดลง และ cache miss/fallback ไม่ทำให้ระบบล้ม

---

### A7 - API Rate Limiting and Abuse Control

**Priority:** P1  
**Where:** Cloudflare, Nginx gateway, application layer  
**Goal:** กัน abuse ทั้งก่อนเข้า origin และในระบบเอง

**Tasks:**
1. ตั้ง edge rate limit สำหรับ `/v1/api/*`
2. ตั้ง limit เข้มขึ้นสำหรับ login/auth/upload/chat AI endpoints
3. เพิ่ม request size limit สำหรับ upload
4. เพิ่ม per-user quota สำหรับ expensive AI calls
5. log blocked/limited requests เป็น metric

**Done when:** ยิง load เกิน limit แล้วได้ 429/blocked response และมี log ให้ตรวจ

---

### A8 - CI/CD Quality Gates

**Priority:** P1  
**Where:** `.github/workflows`  
**Goal:** กัน build เสียและ deploy ผิด environment

**Tasks:**
1. Frontend: `npm ci`, `npm run build`, `npx tsc --noEmit`
2. Backend: Maven test/build แยก service
3. Docker build ทุก image
4. Dependency/container vulnerability scan
5. Separate deploy jobs ต่อ environment พร้อม manual approval
6. Smoke test หลัง deploy:
   - `/`
   - `/v1/api/user/profile/...`
   - `/v1/api/chat-app/room/list`
   - `/v1/api/b-post/presence/online`

**Done when:** PR เสียโดน block ก่อน merge และ deploy เสร็จมี smoke result

---

### A9 - Database Governance and RLS

**Priority:** P1  
**Where:** Supabase, migration files, SQL review  
**Goal:** schema เปลี่ยนอย่างควบคุมได้ และ data access ปลอดภัย

**Tasks:**
1. ย้าย schema changes สำคัญเข้า migration workflow
2. เปิด RLS สำหรับ exposed/public tables ที่จำเป็น
3. ตรวจ policy ด้วย test user จริง
4. ตั้ง backup/restore drill
5. เปิด/ใช้ database advisors และ index review
6. แยก service role usage ให้อยู่เฉพาะ backend ที่จำเป็น

**Done when:** มี migration history, RLS policy ทดสอบได้ และ restore path ไม่ใช่แค่ทฤษฎี

---

### A10 - Runtime Resilience

**Priority:** P2  
**Where:** Spring Boot services, gateway, external API clients  
**Goal:** ลด blast radius เวลา Groq, HuggingFace, Supabase หรือ service ใด service หนึ่งช้า/ล่ม

**Tasks:**
1. ตั้ง timeout ทุก outbound HTTP client
2. ใช้ retry แบบมี budget และไม่ retry non-idempotent write มั่ว
3. ใช้ circuit breaker กับ Groq/HuggingFace
4. เพิ่ม readiness/liveness health endpoints
5. กำหนด graceful degradation เช่น AI down แต่ history ยังอ่านได้

**Done when:** external API ช้าแล้ว service ไม่ค้างยาวและผู้ใช้ได้ error ที่เข้าใจได้

---

### A11 - Storage and Upload Policy

**Priority:** P2  
**Where:** Supabase Storage, bpost upload APIs, Cloudflare  
**Goal:** อัปโหลดไฟล์ปลอดภัยและคุม cost ได้

**Tasks:**
1. จำกัด content type และ file size
2. scan/validate image metadata เท่าที่จำเป็น
3. แยก path ต่อ domain: posts, messages, legacy blog
4. กำหนด public vs signed URL ให้ชัด
5. ตั้ง lifecycle/cleanup policy สำหรับ orphan uploads

**Done when:** upload endpoint ไม่รับไฟล์แปลก/ใหญ่เกิน และมี cleanup strategy

---

### A12 - Performance and Load Testing

**Priority:** P2  
**Where:** local, staging, GitHub Actions optional  
**Goal:** รู้ limit ของระบบก่อนเจอ traffic จริง

**Tasks:**
1. สร้าง k6 หรือ Artillery scenario:
   - login/sync mock
   - feed read
   - chat history read
   - send chat message
   - upload image
2. เก็บ baseline latency p50/p95/p99
3. เทียบผลก่อน/หลัง Redis และ rate limiting
4. ตั้ง load test แบบเบาใน staging ก่อน release ใหญ่

**Done when:** มีตัวเลข baseline และรู้ bottleneck หลัก

---

### A13 - Operational Runbooks

**Priority:** P2  
**Where:** `note/docs` หรือ `runbooks/`  
**Goal:** เวลาเสียจริงมี playbook ไม่ต้องไล่เดาจากศูนย์

**Tasks:**
1. Runbook: 5xx spike
2. Runbook: Supabase auth/session issue
3. Runbook: DB connection pool exhausted
4. Runbook: AI provider outage
5. Runbook: rollback ACA revision
6. Runbook: rotate leaked secret

**Done when:** คนอื่นในทีมทำตาม runbook แล้วแก้ incident จำลองได้

---

## Recommended Order

| Phase | Actions | เหตุผล |
|---|---|---|
| Phase 1 | A1, A2, A3 | เห็นระบบและป้องกันขอบระบบก่อน |
| Phase 2 | A4, A5, A8, A9 | ทำ deploy/config ให้ repeatable และปลอดภัย |
| Phase 3 | A6, A7, A10 | เพิ่ม scale/performance/resilience หลังมี monitor |
| Phase 4 | A11, A12, A13 | polish ด้าน operations และ long-term maintenance |

---

## Reference Links

- Azure Container Apps observability: https://learn.microsoft.com/en-us/azure/container-apps/observability
- Azure Container Apps OpenTelemetry: https://learn.microsoft.com/en-us/azure/container-apps/opentelemetry-agents
- OpenTelemetry Java instrumentation: https://opentelemetry.io/docs/languages/java/instrumentation/
- Cloudflare WAF rate limiting rules: https://developers.cloudflare.com/waf/rate-limiting-rules/
- Terraform AzureRM Container App resource: https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs/resources/container_app
- Azure Cache for Redis overview: https://learn.microsoft.com/en-us/azure/azure-cache-for-redis/cache-overview
