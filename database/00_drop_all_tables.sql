-- คำสั่งล้างตารางทั้งหมดในฐานข้อมูล (PostgreSQL)
-- ใช้ CASCADE เพื่อลบ constraint ที่เกี่ยวข้องด้วย

-- 1. ลบตารางกลุ่ม Chat
DROP TABLE IF EXISTS chat.message_embeddings CASCADE;
DROP TABLE IF EXISTS chat.ai_context CASCADE;
DROP TABLE IF EXISTS chat.messages CASCADE;
DROP TABLE IF EXISTS chat.room_members CASCADE;
DROP TABLE IF EXISTS chat.rooms CASCADE;
DROP TABLE IF EXISTS chat.users CASCADE;

-- 2. ลบตารางกลุ่ม Dinner
DROP TABLE IF EXISTS dinner.order_items CASCADE;
DROP TABLE IF EXISTS dinner.orders CASCADE;
DROP TABLE IF EXISTS dinner.ingredients CASCADE;
DROP TABLE IF EXISTS dinner.categories CASCADE;
DROP TABLE IF EXISTS dinner.suppliers CASCADE;

-- (เพิ่มเติม) หากต้องการลบ Schema ด้วย
-- DROP SCHEMA IF EXISTS chat CASCADE;
-- DROP SCHEMA IF EXISTS dinner CASCADE;
