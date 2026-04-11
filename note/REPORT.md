# ChatApp Backend — Bug Report

> สแกนเมื่อ 2026-04-10 | สาขา: `main` | ตรวจสอบจาก Supabase จริง (project: `xabewjiiewyhhjfekazv`)

---

## สถานะ Supabase Schema (ตรวจสอบแล้ว)

| ส่วน | สถานะ |
|------|--------|
| `vector` extension (pgvector) | ✅ ติดตั้งแล้ว |
| `chat.message_embeddings` (`vector(384)`) | ✅ มีอยู่แล้ว |
| Vector index HNSW cosine ops | ✅ มีอยู่แล้ว (`idx_message_embeddings_vector`) |
| `chat.room_members.ai_id` (FK → `ai_context.id`) | ✅ มี column จริง |
| `chat.ai_context` | ✅ มีอยู่แล้ว |

> **หมายเหตุ:** โฟลเดอร์ `database/` ที่เคยมี SQL schema ถูกลบออกจาก repo แล้ว — schema management ทำผ่าน Supabase โดยตรง ข้อมูล schema ปัจจุบันดูได้ที่ `note/docs/SUPABASE.md`

---

## Bug 1: HuggingFace API Response Type ผิด — `double[]` แทน `double[][]`

**ไฟล์:** `backend/chatapp/src/main/java/com/sandbox/sandman/backend/services/EmbeddingService.java` บรรทัด 88
**ความรุนแรง:** Critical — ทำให้ `embed()` โยน Exception ทุกครั้ง embedding ไม่ถูกบันทึกเลย

### อาการ

HuggingFace Inference API endpoint `pipeline/feature-extraction` คืน response เป็น **nested array**:

```json
[[0.0123, -0.0456, 0.0789, ...]]
```

แต่ code ปัจจุบัน deserialize เป็น `double[]` (flat array):

```java
// บรรทัด 88 — ผิด
ResponseEntity<double[]> response = restTemplate.exchange(
        HF_API_URL, HttpMethod.POST, request, double[].class);

double[] doubles = response.getBody();
```

Jackson พยายาม deserialize `[[...]]` เป็น `double[]` → โยน `HttpMessageConversionException`

Exception ถูก catch ด้วย `log.warn(...)` ใน `embedAndSave()` และ `searchSimilarMessages()` จึงไม่ crash แต่:
- embedding ไม่ถูก insert ลง `message_embeddings` เลย
- vector search คืน list ว่าง → AI ไม่มี context จาก past messages

### วิธีแก้

```java
// EmbeddingService.java บรรทัด 88 — แก้เป็น double[][]
ResponseEntity<double[][]> response = restTemplate.exchange(
        HF_API_URL, HttpMethod.POST, request, double[][].class);

double[][] body = response.getBody();
if (body == null || body.length == 0) {
    throw new RuntimeException("Empty response from HuggingFace API");
}
double[] doubles = body[0]; // embedding ของ input แรก
```

---

## สรุปภาพรวม

| # | Bug | ไฟล์ | ผลกระทบ |
|---|-----|------|---------|
| 1 | Response type `double[]` ควรเป็น `double[][]` | `EmbeddingService.java:88` | `embed()` โยน Exception ทุกครั้ง — embedding ไม่ทำงานเลย |

**Pipeline ที่ได้รับผลกระทบ:**
```
ส่งข้อความ → embedAndSave() → embed() ← FAIL ตรงนี้
AI response  → searchSimilarMessages() → embed() ← FAIL ตรงนี้
```

> **สถานะ (2026-04-12):** Bug นี้ยังไม่ได้แก้ — `EmbeddingService.java` บรรทัด 89-90 ยังใช้ `double[].class` อยู่ ส่งผลให้ vector search ทั้ง pipeline ไม่ทำงาน (tracked ใน ROADMAP Phase 4 — Implement Vector Search)
