-- Phase 4: pgvector Extension & Embeddings Table (PostgreSQL / Supabase)
-- ต้องรันหลังจาก 02_chat_schema.sql
-- Migration applied: enable_pgvector_and_message_embeddings

-- 1. เปิด pgvector extension (version 0.8.0 บน Supabase)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. สร้างตาราง message_embeddings สำหรับเก็บ vector embeddings
CREATE TABLE IF NOT EXISTS chat.message_embeddings (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES chat.messages(id) ON DELETE CASCADE,
    embedding vector(384),  -- 384 dimensions สำหรับ all-MiniLM-L6-v2
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(message_id)
);

-- 3. สร้าง HNSW index สำหรับ cosine similarity search
-- หมายเหตุ: ใช้ HNSW แทน IVFFlat เพราะ IVFFlat ต้องการ lists=100 ซึ่งต้องมีข้อมูล >= 100 rows ก่อน
--           HNSW ทำงานได้ทันทีบนตารางเปล่า และมี recall ที่ดีกว่า
CREATE INDEX IF NOT EXISTS idx_message_embeddings_vector
    ON chat.message_embeddings
    USING hnsw (embedding vector_cosine_ops);
