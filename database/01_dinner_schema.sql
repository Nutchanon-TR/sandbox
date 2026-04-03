-- สร้าง Schema สำหรับ Dinner (PostgreSQL / Supabase)
CREATE SCHEMA IF NOT EXISTS dinner;

-- 1. สร้างตาราง categories
CREATE TABLE dinner.categories (
    category_id SERIAL PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL,
    description TEXT
);

-- 2. สร้างตาราง ingredients
CREATE TABLE dinner.ingredients (
    ingredient_id SERIAL PRIMARY KEY,
    ingredient_name VARCHAR(100) NOT NULL,
    category_id INT NOT NULL REFERENCES dinner.categories(category_id),
    unit VARCHAR(20) NOT NULL,
    description TEXT
);

-- 3. สร้างตาราง suppliers
CREATE TABLE dinner.suppliers (
    supplier_id SERIAL PRIMARY KEY,
    supplier_name VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100),
    address TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. สร้างตาราง orders
CREATE TABLE dinner.orders (
    order_id SERIAL PRIMARY KEY,
    supplier_id INT NOT NULL REFERENCES dinner.suppliers(supplier_id),
    order_date DATE NOT NULL,
    delivery_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()  -- แก้ไขเป็น TIMESTAMPTZ (Migration: fix_dinner_orders_created_at_timezone)
);

-- 5. สร้างตาราง order_items
CREATE TABLE dinner.order_items (
    item_id SERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES dinner.orders(order_id),
    ingredient_id INT NOT NULL REFERENCES dinner.ingredients(ingredient_id),
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
    quality_grade VARCHAR(20),
    origin VARCHAR(50)
);
