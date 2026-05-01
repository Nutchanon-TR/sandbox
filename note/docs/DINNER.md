# Dinner - Current Code Spec

เอกสารนี้สรุป `dinner-service` จาก source code ปัจจุบัน เป็น service สำหรับ inquiry supplier/order จาก schema `dinner`

---

## ภาพรวม

Dinner service ปัจจุบันมี endpoint หลักเพียงตัวเดียว:

```text
GET /v1/api/dinner/supplier/inquiry
```

เส้นทาง:

```text
Frontend -> Nginx gateway -> /v1/api/dinner/* -> dinner-service
```

Auth:

- gateway ตรวจ Bearer JWT ผ่าน oauth2-proxy
- `JwtAuthFilter` จาก `common-auth` ถูก register บน `/v1/api/dinner/*`
- controller ปัจจุบันยังไม่ inject หรือเรียก `CurrentUser.requireUserId()`
- endpoint เป็น read-only inquiry และไม่ได้ผูกข้อมูลกับ user ปัจจุบัน

---

## Tech Stack

| ส่วน | เทคโนโลยี |
|---|---|
| Backend | Spring Boot 3.2.5 |
| Java compile | Java 21 (`pom.xml`) |
| Runtime image | Eclipse Temurin 21 JRE |
| Database | Supabase PostgreSQL |
| ORM | Spring Data JPA |
| Query style | native SQL projection |
| Auth helper | `common-auth` dependency + filter registration |

---

## Environment Variables

| ตัวแปร | ใช้ที่ | หมายเหตุ |
|---|---|---|
| `SUPABASE_DB_USERNAME` | local datasource username | default `postgres` |
| `SUPABASE_DB_PASSWORD` | datasource password | ใช้ต่อ PostgreSQL |
| `SPRING_DATASOURCE_URL` | ACA override | Supavisor pooler `:6543` |
| `SPRING_DATASOURCE_USERNAME` | ACA override | format `postgres.<project-ref>` |
| `SUPABASE_URL` | inherited app.yml config | มีใน config pattern แต่ Dinner endpoint ปัจจุบันไม่ใช้ Storage |
| `SUPABASE_SERVICE_ROLE_KEY` | inherited app.yml config | มีใน config pattern |
| `GROK_API_KEY` | inherited app.yml config | มีใน config pattern แต่ Dinner ไม่ได้ใช้ AI flow |
| `CORS_ALLOWED_ORIGIN_GATEWAY` | `WebConfig` | allowed origin ของ backend CORS |

Config สำคัญ:

```yaml
app:
  api:
    prefix:
      dinner: v1/api/dinner
```

---

## Endpoint

Base path: `/v1/api/dinner`

| Method | Path | Controller |
|---|---|---|
| `GET` | `/supplier/inquiry` | `SupplierOrderController.AllSupplierOrders(...)` |

Query parameters bind เข้า `PaginationRequest`

| Parameter | Default | Validation |
|---|---|---|
| `page` | `1` | min `1` |
| `size` | `10` | min `1`, max `50` |
| `sort` | `[{ field: "id", direction: "asc" }]` | ใช้ `Sort.Direction.fromString(...)` |

ตัวอย่าง:

```text
GET /v1/api/dinner/supplier/inquiry?page=1&size=10
```

หมายเหตุ: การ bind `List<SortRequest>` จาก query string ใน Spring MVC อาจต้องใช้รูปแบบ query ที่เหมาะสมถ้าจะส่งหลาย sort field; ถ้าไม่ส่งจะใช้ default sort

---

## Response

`PageResponse<SupplierOrderDto>`:

```json
{
  "content": [
    {
      "id": 1,
      "supplierName": "Supplier A",
      "contactPerson": "Jane",
      "phone": "0812345678",
      "email": "supplier@example.com",
      "orderId": 1001,
      "orderDate": "2026-05-01",
      "deliveryDate": "2026-05-03",
      "status": "PENDING",
      "notes": "..."
    }
  ],
  "page": 1,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

Projection interface:

```text
SupplierOrderDto
  id
  supplierName
  contactPerson
  phone
  email
  orderId
  orderDate
  deliveryDate
  status
  notes
```

---

## Database Schema

Dinner service ใช้ schema `dinner`

### `dinner.suppliers`

Entity: `backend/dinner/src/main/java/.../model/entity/DinnerEntity/Supplier.java`

| Column | Type/Mapping |
|---|---|
| `supplier_id` | `Integer`, primary key |
| `supplier_name` | string length 100, not null |
| `contact_person` | string length 50, not null |
| `phone` | string length 20, not null |
| `email` | string length 100 |
| `address` | nationalized text, not null |
| `created_at` | timestamp |

หมายเหตุจาก entity:

- ใช้ `@Nationalized` บาง field
- `created_at` มี `@ColumnDefault("getdate()")` ซึ่งเป็นร่องรอยจาก SQL Server เดิม ถ้าสร้าง schema บน PostgreSQL ใหม่ควรใช้ `now()`

### `dinner.orders`

ไม่มี JPA entity โดยตรง แต่ถูก join ใน native query:

| Column | ใช้ใน projection |
|---|---|
| `order_id` | `orderId` |
| `supplier_id` | join กับ suppliers |
| `order_date` | `orderDate` |
| `delivery_date` | `deliveryDate` |
| `status` | `status` |
| `notes` | `notes` |

Native query อยู่ใน `SupplierRepository.findSupplierOrder(Pageable pageable)`

```sql
SELECT
    s.supplier_id AS id,
    s.supplier_name AS supplierName,
    s.contact_person AS contactPerson,
    s.phone AS phone,
    s.email AS email,
    o.order_id AS orderId,
    o.order_date AS orderDate,
    o.delivery_date AS deliveryDate,
    o.status AS status,
    o.notes AS notes
FROM dinner.suppliers s
JOIN dinner.orders o ON s.supplier_id = o.supplier_id
```

---

## Internal Flow

```text
SupplierOrderController
  -> SupplierOrderService.getAllSupplierOrders(req)
  -> PaginationRequest.toPageable()
  -> SupplierRepository.findSupplierOrder(pageable)
  -> PageResponse.toPageResponse(page)
```

`PaginationRequest.toPageable()`:

- แปลง `page` เป็น zero-based index ด้วย `page - 1`
- สร้าง `Sort` จาก list ของ `SortRequest`
- ถ้าไม่มี sort ใช้ `Sort.unsorted()`

---

## Frontend Mapping

API constant:

```text
frontend/constants/api/ApiSandbox.ts
```

```ts
DINNER_SUPPLIER_ORDER: {
  path: `${contextPath}/v1/api/dinner/supplier/inquiry`,
  method: 'GET',
}
```

Frontend page:

```text
frontend/app/dinner/supplier/page.tsx
```

---

## Build / Infra Notes

Dockerfile:

```text
backend/dinner/Dockerfile
```

Current code note:

- `backend/dinner/pom.xml` มี dependency `common-auth`
- `backend/dinner/Dockerfile` build ด้วย context ย่อย `./backend/dinner` และไม่ได้ install/copy `common-auth`
- ถ้า local/CI Maven ไม่มี `common-auth:0.1.0` ใน repository หรือ local cache อาจ build fail
- `docker-compose.yml` ก็ยังใช้ context `./backend/dinner`

ดูรายละเอียด infra mismatch ใน `INFRA.md`

---

## Known Gaps / Notes

| เรื่อง | สถานะ |
|---|---|
| Auth enforcement ใน controller | `JwtAuthFilter` register แล้ว แต่ controller ไม่เรียก `CurrentUser.requireUserId()` |
| Write APIs | ยังไม่มี |
| Orders entity | ยังไม่มี JPA entity, ใช้ native join query |
| Schema migration | ไม่มี migration file ใน repo สำหรับ dinner schema |
| Cache | ไม่มี Redis/cache layer |
