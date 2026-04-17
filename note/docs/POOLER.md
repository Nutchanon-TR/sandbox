# Supabase Connection Pooler

บันทึกเรื่อง DB connection pool, ทำไมต้องใช้ pooler, และทำไมเลือก transaction mode (port 6543)

---

## 1. ฐานเรื่อง: ทำไมต้องมี "connection pool"?

ทุกครั้งที่ backend จะคุยกับ database ต้องเปิด **TCP connection + TLS handshake + auth** ซึ่งแพง (ประมาณ 50-200ms/ครั้ง)

ถ้าเปิดใหม่ทุก request = ช้ามาก

**วิธีแก้:** เปิด connection ไว้ล่วงหน้าหลายๆ เส้น แล้วเอากลับมาใช้ซ้ำ → ที่เก็บ connection นี้เรียกว่า **"connection pool"**

---

## 2. HikariCP คืออะไร?

คือ library connection pool ของ Java ที่ **Spring Boot ใช้เป็น default** ตั้งแต่ v2+

โค้ดเราไม่ได้เขียนเรียก HikariCP ตรงๆ แต่พอใส่ `spring-boot-starter-data-jpa` ใน pom.xml มันจะมาเอง

**Default config:**
- Max pool size = **10 connections ต่อ 1 service**
- ตอน service start → HikariCP ไม่ได้เปิด 10 ทันที แต่จะเปิดเพิ่มเรื่อยๆ เมื่อมี request เข้ามา จนถึง 10

---

## 3. Supabase ให้ connection ได้กี่เส้น?

Supabase มี **3 ช่องทางเชื่อม DB** ดังนี้:

| ช่องทาง | Host | Port | Limit |
|---|---|---|---|
| **Direct** | `db.<ref>.supabase.co` | 5432 | ~60 connections (แต่ IPv6-only → ACA ใช้ไม่ได้) |
| **Session Pooler** | `aws-1-...pooler.supabase.com` | **5432** | **15 clients** |
| **Transaction Pooler** | `aws-1-...pooler.supabase.com` | **6543** | **~200+ (ใช้ร่วมกัน)** |

**สำคัญ:** host สอง pooler เหมือนกัน เปลี่ยนแค่ **port** → แต่ behavior ต่างกันมาก

---

## 4. Session Pooler (5432) ทำงานยังไง?

**Session mode = จอง connection แบบ 1:1 ยาวๆ**

แปลว่า: ตอน HikariCP ของ service เปิด connection 1 เส้นไปที่ pooler → pooler จะ **lock backend connection 1 เส้นไว้ให้ HikariCP นั้นตลอดอายุของ connection** (ไม่ว่า HikariCP จะใช้หรือนั่ง idle)

**ปัญหาในโปรเจคเรา:**

```
chat-service     HikariCP → เปิด 10 เส้น → ถือไว้ 10 slot
user-service     HikariCP → เปิด 10 เส้น → ถือไว้ 10 slot
dinner-service   HikariCP → เปิด 10 เส้น → ถือไว้ 10 slot
bpost-service    HikariCP → เปิด 10 เส้น → ถือไว้ 10 slot
─────────────────────────────────────────────────────
                                รวม = 40 slot ต้องการ
```

แต่ Supabase ให้แค่ **15 slot** → ชนเพดานตั้งแต่ service ที่ 2 เริ่ม warm pool = **EMAXCONNSESSION**

**คำว่า "1:1" หมายถึง:** 1 HikariCP connection จอง 1 backend connection ตลอดเวลา ไม่แชร์กับใคร (แม้ไม่ได้ใช้ก็ถือไว้)

---

## 5. Transaction Pooler (6543) ทำงานยังไง?

**Transaction mode = แชร์ connection แบบ many-to-few**

HikariCP ยังเปิด 10 เส้นเข้า pooler เหมือนเดิม แต่ **pooler ไม่จองฝั่ง backend ไว้ตายตัว** — เมื่อไหร่ที่เรามี transaction ค่อยยืม backend 1 เส้นมาใช้ พอ commit/rollback ก็คืนทันที

```
Service ยิง query → pooler ยืม backend → ทำงาน → คืน → พร้อมให้คนอื่น
```

ผลที่ได้: HikariCP 40 เส้น × 4 services แชร์ backend แค่ ~10-20 เส้นได้สบาย เพราะส่วนใหญ่ idle อยู่

---

## 6. Trade-off ของ Transaction mode

เนื่องจาก backend connection สลับไปมา → feature ที่ต้อง "จำ state ข้าม query" จะพัง:

- **Server-side prepared statements** (PgJDBC cache): เตรียมไว้ที่ backend A แต่ query ต่อไปไปเจอ backend B ที่ไม่รู้จัก → Error

**วิธีแก้:** เติม `?prepareThreshold=0` ใน URL → PgJDBC ส่งเป็น ad-hoc SQL ทุกครั้ง ไม่ cache ฝั่ง backend
(เสีย performance นิดเดียว เพราะ PgJDBC ยัง cache ฝั่ง client อยู่)

**Feature อื่นที่ไม่รองรับใน transaction mode (ไม่กระทบโปรเจคนี้):**
- `LISTEN/NOTIFY` (pub/sub ของ Postgres)
- Advisory locks
- Session variables (`SET ...`)
- Temporary tables ข้าม transaction
