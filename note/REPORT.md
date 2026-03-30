# Project Progress Report

## Phase 1: Ingress Layer & Gateway Security

**[Add]** `gateway/nginx.conf` => เพื่อจัดการทำ API Gateway (Reverse Proxy) แบ่งเส้นทาง Request ไปหา Frontend (`/`) และ Backend (เช่น `/v1/api/chat/`, `/v1/api/dinner/`, `/v1/api/bpost/`)
**[Add]** `gateway/Dockerfile` => เพื่อไว้สร้าง Image Nginx โดยฝังไฟล์ `nginx.conf` ตัวใหม่เข้าไปด้วย เตรียมนำขึ้นรัน
**[Add]** `docker-compose.yml` => เพื่อมัดรวมทุก Service ทั่วทั้งโปรเจกต์ ทั้ง Frontend, Backend ทั้ง 3 ตัว, Nginx, และ Sidecar (OAuth2Proxy) เข้ามาเสกรันทำงานร่วมกันบน Local Network ในคำสั่งเดียว (`docker-compose up`)
**[Edit]** `note/docs/ROADMAP.md` => เพิ่มการเชื่อมต่อเซอร์วิสใหม่ภาพรวม (B-Post) ลงไปในแผนการเชื่อมต่อ Nginx และระบบติดตาม New Relic

## Phase 2: Caching & Redis Integration (Prototype)

**[Edit]** `docker-compose.yml` => เพิ่ม Service `redis:7.2-alpine` เพื่อใช้เป็น Cache Server ส่วนกลางแทนการฝัง Cache Engine ไว้ในแต่ละ Service
**[Edit]** `backend/*/pom.xml` => เพิ่ม Dependency `spring-boot-starter-data-redis` และ `spring-boot-starter-cache` สำหรับ Chat และ Dinner Services
**[Edit]** `backend/*/src/main/resources/application.yml` => ชี้เป้าหมายให้ Spring Boot หันไปใช้ `redis` host ตัวกลาง
**[Edit]** `backend/*/src/main/java/com/sandbox/sandman/backend/Application.java` => ใส่ Annotations `@EnableCaching` เพื่อเปิดฟีเจอร์ Caching ให้แอปพลิเคชัน
**[Edit]** `ChatService.java` & `SupplierOrderService.java` => ใส่ `@Cacheable` ดักหน้า Method ดึงข้อมูลหนักๆ (ประวัติแชท, ออเดอร์ของซัพพลายเออร์) และใส่ `@CacheEvict` ล้าง Cache เมื่อมีการสร้างข้อความแชทใหม่ (ในฐานะตัวต้นแบบ)
