-- คำสั่งล้างตารางทั้งหมดในฐานข้อมูล (ทำงานแบบ CASCADE คือลบ constraint และ data ที่เกี่ยวข้องกันด้วย)

-- 1. ลบตารางกลุ่ม Chat
-- ต้องลบ messages และ room_members ก่อนเพราะอ้างอิงไปยัง users และ rooms
DROP TABLE IF EXISTS chat.messages;
DROP TABLE IF EXISTS chat.room_members;
DROP TABLE IF EXISTS chat.rooms;
DROP TABLE IF EXISTS chat.users;

-- 2. ลบตารางกลุ่ม Dinner
-- ต้องลบ order_items ก่อนเป็นอันดับแรก เพราะเป็นตารางปลายทางสุดของความสัมพันธ์
DROP TABLE IF EXISTS dinner.order_items;
DROP TABLE IF EXISTS dinner.orders;
DROP TABLE IF EXISTS dinner.ingredients;
DROP TABLE IF EXISTS dinner.categories;
DROP TABLE IF EXISTS dinner.suppliers;

-- (เพิ่มเติม) หากต้องการลบ Schema ด้วย 
-- ต้องลบหลังจากตารางข้างในว่างหมดแล้วเท่านั้น
-- IF EXISTS (SELECT * FROM sys.schemas WHERE name = 'chat') DROP SCHEMA chat;
-- IF EXISTS (SELECT * FROM sys.schemas WHERE name = 'dinner') DROP SCHEMA dinner;
GO