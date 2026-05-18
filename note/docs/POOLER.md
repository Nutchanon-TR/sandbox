# Supabase Connection Pooler

เอกสารนี้สรุปการเชื่อมต่อ PostgreSQL/Supabase จาก config ปัจจุบัน และเหตุผลที่ production ใช้ Supavisor transaction pooler

---

## สถานะปัจจุบันในโค้ด

ใน `application.yml` ของ backend services ค่า default ยังเป็น direct DB URL:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.xabewjiiewyhhjfekazv.supabase.co:5432/postgres
    username: ${SUPABASE_DB_USERNAME:postgres}
    password: ${SUPABASE_DB_PASSWORD:}
```

บน Azure Container Apps workflow override ด้วย env:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:6543/postgres?prepareThreshold=0
SPRING_DATASOURCE_USERNAME=postgres.<SUPABASE_PROJECT_ID>
SUPABASE_DB_PASSWORD=secretref:supabase-db-password
```

ดังนั้น:

- local/default app.yml = direct host `db.<ref>.supabase.co:5432`
- ACA/runtime = Supavisor transaction pooler `aws-1-ap-northeast-1.pooler.supabase.com:6543`

---

## ทำไมต้องใช้ Pooler

Spring Boot ใช้ HikariCP เป็น connection pool default เมื่อใช้ `spring-boot-starter-data-jpa`

ถ้าแต่ละ service เปิด connection pool แยกกัน:

```text
chat-service   -> HikariCP สูงสุดประมาณ 10
bpost-service  -> HikariCP สูงสุดประมาณ 10
user-service   -> HikariCP สูงสุดประมาณ 10
รวมประมาณ 40 client connections
```

Supabase direct/session connection มีเพดานจำกัด และ ACA ใช้ IPv4 ได้สะดวกกว่าผ่าน pooler จึงใช้ Supavisor transaction mode ใน production

---

## Supabase Connection Modes

| Mode | Host/Port | พฤติกรรม |
|---|---|---|
| Direct | `db.<ref>.supabase.co:5432` | ต่อ database ตรง, อาจเจอข้อจำกัด IPv6/connection limit ใน cloud |
| Session pooler | `*.pooler.supabase.com:5432` | 1 client connection ผูก backend connection ยาวทั้ง session |
| Transaction pooler | `*.pooler.supabase.com:6543` | ยืม backend connection เฉพาะระหว่าง transaction แล้วคืนทันที |

โปรเจกต์นี้เลือก transaction pooler บน ACA เพื่อแชร์ backend connection ระหว่างหลาย service

---

## ทำไมต้อง `prepareThreshold=0`

Transaction pooler อาจสลับ backend connection ระหว่าง query ได้ ทำให้ server-side prepared statement ที่เตรียมไว้บน backend A ไปเจอ backend B แล้ว error

จึงตั้ง:

```text
?prepareThreshold=0
```

เพื่อให้ PgJDBC ไม่สร้าง server-side prepared statements บนฝั่ง PostgreSQL

---

## Username Format

สำหรับ Supavisor pooler ต้องใช้ username รูปแบบ:

```text
postgres.<project-ref>
```

ใน ACA workflow ใช้:

```text
SPRING_DATASOURCE_USERNAME=postgres.${{ secrets.SUPABASE_PROJECT_ID }}
```

local `.env.example` ยังใช้:

```text
SUPABASE_DB_USERNAME=postgres
```

---

## Feature ที่ควรระวังกับ Transaction Pooler

Transaction pooler ไม่เหมาะกับ feature ที่ต้องจำ session state ข้าม transaction เช่น:

- server-side prepared statements
- `LISTEN/NOTIFY`
- advisory locks
- temporary tables ข้าม transaction
- session variables (`SET ...`) ที่ต้องคงอยู่หลาย query

โค้ดปัจจุบันใช้ JPA/JdbcTemplate query ปกติ จึงเข้ากับ transaction pooler ได้

---

## Checklist เวลาเพิ่ม Service ใหม่

1. ใช้ `spring-boot-starter-data-jpa` ได้ตามปกติ
2. ตั้ง datasource ผ่าน env ไม่ hardcode connection string ใน production
3. บน ACA ใช้ `SPRING_DATASOURCE_URL` pooler `:6543`
4. เติม `?prepareThreshold=0`
5. ใช้ `SPRING_DATASOURCE_USERNAME=postgres.<project-ref>`
6. ใช้ secretref สำหรับ `SUPABASE_DB_PASSWORD`
