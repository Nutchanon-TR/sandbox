-- สร้าง Schema สำหรับ Dinner (ใช้คำสั่งแยกเป็น Batch ด้วย GO)
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'dinner')
BEGIN
    EXEC('CREATE SCHEMA dinner');
END
GO

-- 1. สร้างตาราง categories
CREATE TABLE dinner.categories (
    category_id INT PRIMARY KEY IDENTITY(1,1),
    category_name NVARCHAR(50) NOT NULL,
    description NVARCHAR(MAX)
);

-- 2. สร้างตาราง ingredients
CREATE TABLE dinner.ingredients (
    ingredient_id INT PRIMARY KEY IDENTITY(1,1),
    ingredient_name NVARCHAR(100) NOT NULL,
    category_id INT NOT NULL FOREIGN KEY REFERENCES dinner.categories(category_id),
    unit NVARCHAR(20) NOT NULL,
    description NVARCHAR(MAX)
);

-- 3. สร้างตาราง suppliers
CREATE TABLE dinner.suppliers (
    supplier_id INT PRIMARY KEY IDENTITY(1,1),
    supplier_name NVARCHAR(100) NOT NULL,
    contact_person NVARCHAR(50) NOT NULL,
    phone NVARCHAR(20) NOT NULL,
    email NVARCHAR(100),
    address NVARCHAR(MAX) NOT NULL,
    created_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET()
);

-- 4. สร้างตาราง orders
CREATE TABLE dinner.orders (
    order_id INT PRIMARY KEY IDENTITY(1,1),
    supplier_id INT NOT NULL FOREIGN KEY REFERENCES dinner.suppliers(supplier_id),
    order_date DATE NOT NULL,
    delivery_date DATE NOT NULL,
    status NVARCHAR(20) DEFAULT 'pending',
    notes NVARCHAR(MAX),
    created_at DATETIMEOFFSET DEFAULT SYSDATETIMEOFFSET()
);

-- 5. สร้างตาราง order_items
CREATE TABLE dinner.order_items (
    item_id INT PRIMARY KEY IDENTITY(1,1),
    order_id INT NOT NULL FOREIGN KEY REFERENCES dinner.orders(order_id),
    ingredient_id INT NOT NULL FOREIGN KEY REFERENCES dinner.ingredients(ingredient_id),
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price AS (quantity * unit_price), -- ใช้ Computed Column คำนวณอัตโนมัติได้เลย
    quality_grade NVARCHAR(20),
    origin NVARCHAR(50)
);