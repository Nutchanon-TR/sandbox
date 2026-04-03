# Project Progress Report

## Phase 1: Ingress Layer & Gateway Security

**[Add]** `gateway/nginx.conf` => เพื่อจัดการทำ API Gateway (Reverse Proxy) แบ่งเส้นทาง Request ไปหา Frontend (`/`) และ Backend (เช่น `/v1/api/chat/`, `/v1/api/dinner/`, `/v1/api/bpost/`)
**[Add]** `gateway/Dockerfile` => เพื่อไว้สร้าง Image Nginx โดยฝังไฟล์ `nginx.conf` ตัวใหม่เข้าไปด้วย เตรียมนำขึ้นรัน
**[Add]** `docker-compose.yml` => เพื่อมัดรวมทุก Service ทั่วทั้งโปรเจกต์ ทั้ง Frontend, Backend ทั้ง 3 ตัว, Nginx, และ Sidecar (OAuth2Proxy) เข้ามาเสกรันทำงานร่วมกันบน Local Network ในคำสั่งเดียว (`docker-compose up`)
**[Edit]** `note/docs/ROADMAP.md` => เพิ่มการเชื่อมต่อเซอร์วิสใหม่ภาพรวม (B-Post) ลงไปในแผนการเชื่อมต่อ Nginx และระบบติดตาม New Relic

## Phase 2: Caching & Redis Integration (Prototype)

**[Add]** `docker-compose.yml` => เพิ่ม Service `redis:7.2-alpine` เพื่อใช้เป็น Cache Server ส่วนกลางแทนการฝัง Cache Engine ไว้ในแต่ละ Service
**[Edit]** `backend/*/pom.xml` => เพิ่ม Dependency `spring-boot-starter-data-redis` และ `spring-boot-starter-cache` สำหรับ Chat และ Dinner Services
**[Edit]** `backend/*/src/main/resources/application.yml` => ชี้เป้าหมายให้ Spring Boot หันไปใช้ `redis` host ตัวกลาง
**[Edit]** `backend/*/src/main/java/com/sandbox/sandman/backend/Application.java` => ใส่ Annotation `@EnableCaching` เพื่อเปิดฟีเจอร์ Caching ให้แอปพลิเคชัน
**[Edit]** `ChatService.java` & `SupplierOrderService.java` => ใส่ `@Cacheable` ดักหน้า Method ดึงข้อมูลหนักๆ (ประวัติแชท, ออเดอร์ของซัพพลายเออร์) และใส่ `@CacheEvict` ล้าง Cache เมื่อมีการสร้างข้อความแชทใหม่ (ในฐานะตัวต้นแบบ)

> **ปรับปรุงใน Phase 4:** Cache key ของประวัติแชทถูกเปลี่ยนจาก `roomId` เดี่ยว เป็น `roomId + beforeId + limit` เพื่อรองรับ Pagination และ `@CacheEvict` เปลี่ยนเป็น `allEntries = true` เพื่อล้างทุก entry ของห้องนั้นพร้อมกันเมื่อมีข้อความใหม่

---

## Phase 3: pgvector & Resilience

**[Add]** `database/03_pgvector_schema.sql` => สร้าง Table `chat.message_embeddings` (vector 384 มิติ) พร้อม Index แบบ HNSW รองรับการค้นหา Semantic Search ในอนาคต
**[Edit]** `backend/chatapp/pom.xml` => เพิ่ม Dependency `spring-ai-pgvector-store` เตรียม Integration กับ pgvector
**[Edit]** `GroqAiClient.java` => ห่อการเรียก Groq API ด้วย `@CircuitBreaker` และ `@Retry` (Resilience4j) พร้อม Fallback method คืนข้อความแจ้งเตือนแทน error 500
**[Edit]** `application.yml` => กำหนดค่า Circuit Breaker (sliding window 10, fail rate 50%, wait 30s) และ Retry (max 3 ครั้ง, delay 2s)

---

## Phase 4: Chat History Limiting & Paginated Message Loading

แก้ปัญหาที่ Backend ส่ง message ทั้งหมดให้ LLM (เสี่ยงเกิน context window) และ Frontend โหลด message ทั้งหมดทีเดียว (UI ช้าถ้าแชทยาว)

**[Edit]** `MessageRepository.java` => เพิ่ม 3 Query Method:
- `findTopNByRoomId(roomId, pageable)` — ดึง N message ล่าสุด ใช้สำหรับสร้าง LLM Prompt
- `findLatestByRoomId(roomId, pageable)` — ดึง batch แรกสุด (initial load)
- `findByRoomIdBeforeId(roomId, beforeId, pageable)` — ดึง messages ก่อน cursor ID (load more)

**[Add]** `MessageHistoryResponse.java` => DTO ใหม่ `{ messages: List<MessageDto>, hasMore: boolean }` ห่อผลลัพธ์ของ History API พร้อมบอก Frontend ว่ายังมี message เก่ากว่าอีกไหม

**[Edit]** `ChatService.java`:
- `callAiAndSaveReply()` — เปลี่ยนจากดึง message ทั้งหมดใน room เป็นดึงแค่ **20 message ล่าสุด** (`CONTEXT_LIMIT = 20`) แล้ว reverse เรียง asc ก่อนส่งเป็น Prompt ให้ LLM
- `getChatHistoryByRoom()` — รับ parameter `beforeId` (cursor) และ `limit` เพิ่มเติม, ใช้ logic cursor-based pagination (fetch limit+1 เพื่อตรวจสอบ `hasMore`), reverse ผลลัพธ์ให้เรียง asc ก่อน return

**[Edit]** `ChatController.java` => เพิ่ม Query Parameter `beforeId` (optional) และ `limit` (default 20) ใน Endpoint `GET /v1/api/chat-app/message/history/{roomId}` และเปลี่ยน Return type เป็น `MessageHistoryResponse`

**[Edit]** `frontend/app/chat-app/message/page.tsx`:
- เพิ่ม State: `hasMore`, `isLoadingMore`, `oldestMessageId` สำหรับควบคุม Pagination
- `fetchHistory()` — ดึงแค่ 20 message ล่าสุดตอน mount แทนการโหลดทั้งหมด
- `loadMoreMessages()` — ดึง batch ก่อนหน้าโดยใช้ `beforeId` ของ message แรกสุดที่มีอยู่, restore scroll position หลัง prepend (ไม่ให้ view กระโดด)
- `handleScroll()` — trigger `loadMoreMessages()` อัตโนมัติเมื่อ user scroll ถึงด้านบนสุด
- แสดง `<Spin>` ที่ด้านบนของ chat ระหว่างโหลด batch เก่า, ซ่อนเมื่อ `hasMore = false`
