-- ระบบฐานข้อมูลใบเสนอราคาซื้อขายวัตถุดิบอาหารโอมากาเซะ
-- ออกแบบโดยเน้นความเรียบง่ายและประสิทธิภาพ
-- localhost:8080/operator/supOrderList
create database sandbox;
use sandbox;

-- drop database omakaseEiei; 

-- ========================================
-- 1. ตารางผู้จำหน่าย (Suppliers)
-- ========================================
CREATE TABLE suppliers (
    supplier_id INT PRIMARY KEY AUTO_INCREMENT,
    supplier_name VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    address TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========================================
-- 2. ตารางหมวดหมู่สินค้า (Categories)
-- ========================================
CREATE TABLE categories (
    category_id INT PRIMARY KEY AUTO_INCREMENT,
    category_name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
);

-- ========================================
-- 3. ตารางวัตถุดิบ (Ingredients)
-- ========================================
CREATE TABLE ingredients (
    ingredient_id INT PRIMARY KEY AUTO_INCREMENT,
    ingredient_name VARCHAR(100) NOT NULL,
    category_id INT NOT NULL,
    unit VARCHAR(20) NOT NULL,
    description TEXT,
    FOREIGN KEY (category_id) REFERENCES categories(category_id)
);

-- ========================================
-- 4. ตารางใบสั่งซื้อ (Orders)
-- ========================================
CREATE TABLE orders (
    order_id INT PRIMARY KEY AUTO_INCREMENT,
    supplier_id INT NOT NULL,
    order_date DATE NOT NULL,
    delivery_date DATE NOT NULL,
    status ENUM('pending', 'confirmed', 'shipped', 'delivered', 'cancelled') DEFAULT 'pending',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id)
);

-- ========================================
-- 5. ตารางรายการสินค้าในใบสั่งซื้อ (Order Items)
-- ========================================
CREATE TABLE order_items (
    item_id INT PRIMARY KEY AUTO_INCREMENT,
    order_id INT NOT NULL,
    ingredient_id INT NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    quality_grade VARCHAR(20),
    origin VARCHAR(50),
    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (ingredient_id) REFERENCES ingredients(ingredient_id)
);

-- Mock Data for Omakase Restaurant Ingredient Purchasing System
-- Includes various edge cases and realistic scenarios

-- ========================================
-- 1. Suppliers Data
-- ========================================
INSERT INTO suppliers (supplier_name, contact_person, phone, email, address) VALUES
('Tokyo Premium Seafood Co.', 'Hiroshi Tanaka', '+81-3-1234-5678', 'hiroshi@tokyoseafood.jp', '1-2-3 Tsukiji, Chuo-ku, Tokyo 104-0045, Japan'),
('Osaka Fish Market Ltd.', 'Kenji Nakamura', '+81-6-2345-6789', 'kenji@osakafish.com', '5-7-9 Namba, Osaka 556-0011, Japan'),
('Northern Seas Trading', '', '+1-206-555-0123', 'orders@northernseas.com', '1500 Pike Place, Seattle, WA 98101, USA'),
('Pacific Specialty Foods', 'Maria Santos', '+1-415-555-0189', '', '789 Fisherman Wharf, San Francisco, CA 94133, USA'),
('Kyoto Organic Vegetables', 'Yuki Sato', '+81-75-3456-7890', 'yuki@kyotoorganic.jp', '12-34 Gion-machi, Higashiyama-ku, Kyoto 605-0074, Japan'),
('Premium Condiments Inc.', 'Robert Johnson', '+1-212-555-0156', 'robert@premiumcondiments.com', '123 Broadway, New York, NY 10001, USA'),
('Artisan Sake Brewery', 'Taro Yamamoto', '+81-3-4567-8901', 'taro@artisansake.jp', '45-67 Shibuya, Tokyo 150-0002, Japan'),
('Unused Supplier Co.', 'John Doe', '+1-555-000-0000', 'unused@supplier.com', '999 Nowhere Street, Unused City, UC 00000, USA');

-- ========================================
-- 2. Categories Data
-- ========================================
INSERT INTO categories (category_name, description) VALUES
('Fish', 'Fresh and frozen fish for sashimi and sushi'),
('Shellfish', 'Various types of shellfish including crabs, lobsters, and mollusks'),
('Seaweed', 'Nori, wakame, and other seaweed varieties'),
('Rice', 'Premium sushi rice and specialty rice varieties'),
('Condiments', 'Soy sauce, wasabi, miso, and other seasonings'),
('Beverages', 'Sake, tea, and other traditional beverages'),
('Vegetables', 'Fresh vegetables for garnish and preparation'),
('Dairy', 'Specialty dairy products'),
('Empty Category', 'Category with no associated ingredients');

-- ========================================
-- 3. Ingredients Data
-- ========================================
INSERT INTO ingredients (ingredient_name, category_id, unit, description) VALUES
-- Fish
('Bluefin Tuna (Otoro)', 1, 'kg', 'Premium fatty tuna belly, highest grade'),
('Salmon (Norwegian)', 1, 'kg', 'Fresh Atlantic salmon from Norway'),
('Yellowtail (Hamachi)', 1, 'kg', 'Japanese yellowtail, farm-raised'),
('Sea Bream (Tai)', 1, 'kg', 'Wild-caught Japanese sea bream'),
('Mackerel (Saba)', 1, 'kg', 'Fresh mackerel for shime-saba preparation'),
('Eel (Unagi)', 1, 'pieces', 'Pre-cooked freshwater eel'),
('Tuna (Maguro)', 1, 'kg', 'Regular tuna for akami cuts'),
('Amberjack (Kanpachi)', 1, 'kg', NULL),

-- Shellfish
('King Crab Legs', 2, 'kg', 'Alaskan king crab legs, frozen'),
('Sea Urchin (Uni)', 2, 'pieces', 'Fresh sea urchin from Hokkaido'),
('Scallops (Hotate)', 2, 'pieces', 'Large diver scallops'),
('Prawns (Ebi)', 2, 'kg', 'Tiger prawns, size 16-20'),
('Abalone', 2, 'pieces', 'Fresh abalone shellfish'),
('Oysters (Kaki)', 2, 'pieces', 'Kumamoto oysters'),

-- Seaweed
('Nori Sheets', 3, 'sheets', 'Premium grade nori for sushi rolls'),
('Wakame', 3, 'grams', 'Dried wakame seaweed'),
('Kombu', 3, 'grams', 'Kelp for dashi preparation'),

-- Rice
('Sushi Rice (Koshihikari)', 4, 'kg', 'Premium Japanese short-grain rice'),
('Brown Rice', 4, 'kg', 'Organic brown rice'),

-- Condiments
('Soy Sauce (Shoyu)', 5, 'liters', 'Premium aged soy sauce'),
('Wasabi Paste', 5, 'grams', 'Real wasabi, not horseradish'),
('Miso Paste', 5, 'kg', 'White miso for soup'),
('Rice Vinegar', 5, 'liters', 'Seasoned rice vinegar for sushi rice'),
('Sesame Oil', 5, 'liters', 'Pure sesame oil'),
('Mirin', 5, 'liters', 'Sweet rice wine for cooking'),

-- Beverages
('Junmai Daiginjo Sake', 6, 'bottles', 'Premium sake, 720ml bottles'),
('Green Tea (Sencha)', 6, 'grams', 'High-grade green tea leaves'),
('Plum Wine (Umeshu)', 6, 'bottles', 'Traditional plum wine'),

-- Vegetables
('Daikon Radish', 7, 'kg', 'Large white radish for garnish'),
('Cucumber', 7, 'kg', 'Japanese cucumber'),
('Ginger', 7, 'kg', 'Fresh ginger root'),
('Shiso Leaves', 7, 'grams', 'Perilla leaves for garnish'),
('Spring Onions', 7, 'kg', 'Green onions/scallions'),

-- Unused ingredient (not referenced in any order)
('Unused Fish Species', 1, 'kg', 'This ingredient is never ordered');

-- ========================================
-- 4. Orders Data
-- ========================================
INSERT INTO orders (supplier_id, order_date, delivery_date, status, notes) VALUES
-- Multiple orders from same supplier
(1, '2024-01-15', '2024-01-17', 'delivered', 'Regular weekly fish order'),
(1, '2024-01-22', '2024-01-24', 'delivered', 'Extra tuna for weekend special'),
(1, '2024-01-29', '2024-02-01', 'shipped', 'Premium selection for VIP event'),

-- Orders from different suppliers
(2, '2024-01-16', '2024-01-18', 'delivered', 'Shellfish order'),
(3, '2024-01-18', '2024-01-20', 'delivered', 'Weekly crab shipment'),
(4, '2024-01-20', '2024-01-22', 'cancelled', 'Supplier had quality issues'),

-- Orders with different statuses
(5, '2024-01-25', '2024-01-27', 'confirmed', 'Vegetable order for new menu'),
(6, '2024-01-28', '2024-01-30', 'pending', NULL),
(7, '2024-02-01', '2024-02-03', 'pending', 'Sake order for sake tasting event'),

-- Future delivery date
(1, '2024-02-05', '2024-02-10', 'confirmed', 'Pre-order for Valentine menu'),

-- Empty order (no items will be added)
(2, '2024-02-02', '2024-02-04', 'pending', 'Emergency order - items TBD');

-- ========================================
-- 5. Order Items Data
-- ========================================
INSERT INTO order_items (order_id, ingredient_id, quantity, unit_price, total_price, quality_grade, origin) VALUES
-- Order 1: Regular weekly fish order
(1, 1, 2.5, 180.00, 450.00, 'Grade A+', 'Tokyo Bay'),
(1, 2, 3.0, 45.00, 135.00, 'Grade A', 'Norway'),
(1, 3, 2.0, 38.00, 76.00, 'Grade A', 'Kyushu'),
(1, 7, 1.5, 32.00, 48.00, 'Grade B+', 'Pacific'),

-- Order 2: Extra tuna for weekend special
(2, 1, 5.0, 175.00, 875.00, 'Grade A+', 'Tokyo Bay'),
(2, 7, 3.0, 30.00, 90.00, 'Grade A', 'Pacific'),

-- Order 3: Premium selection for VIP event
(3, 1, 1.0, 200.00, 200.00, 'Grade S', 'Tsukiji Premium'),
(3, 4, 2.0, 65.00, 130.00, 'Grade A+', 'Setouchi'),
(3, 6, 8.0, 25.00, 200.00, 'Grade A', 'Lake Biwa'),

-- Order 4: Shellfish order
(4, 9, 2.0, 85.00, 170.00, 'Grade A', 'Alaska'),
(4, 10, 24.0, 8.50, 204.00, 'Grade A+', 'Hokkaido'),
(4, 11, 12.0, 12.00, 144.00, 'Grade A', 'Aomori'),

-- Order 5: Weekly crab shipment
(5, 9, 3.0, 82.00, 246.00, 'Grade A', 'Alaska'),
(5, 13, 6.0, 45.00, 270.00, 'Grade A+', 'Australia'),

-- Order 6: Cancelled order (still has items)
(6, 2, 2.0, 44.00, 88.00, 'Grade A', 'Norway'),
(6, 12, 1.0, 55.00, 55.00, 'Grade A', 'Thailand'),

-- Order 7: Vegetable order
(7, 27, 5.0, 3.50, 17.50, NULL, 'Kyoto'),
(7, 28, 3.0, 2.80, 8.40, NULL, 'Local'),
(7, 29, 2.0, 8.00, 16.00, 'Organic', 'Shizuoka'),

-- Order 8: Pending order with condiments
(8, 19, 2.0, 45.00, 90.00, 'Premium', 'Ishikawa'),
(8, 20, 500.0, 0.18, 90.00, 'Grade A', 'Shizuoka'),
(8, 21, 1.0, 15.00, 15.00, 'Aged 3 years', 'Kyoto'),

-- Order 9: Sake order
(9, 25, 12.0, 85.00, 1020.00, 'Premium', 'Niigata'),
(9, 26, 200.0, 0.35, 70.00, 'First flush', 'Uji'),

-- Order 10: Pre-order for Valentine menu
(10, 1, 1.0, 185.00, 185.00, 'Grade A+', 'Tokyo Bay'),
(10, 10, 20.0, 9.00, 180.00, 'Grade A+', 'Hokkaido'),

-- Edge cases:
-- Extremely high quantity
(1, 17, 1000.0, 0.05, 50.00, NULL, NULL),

-- Very low price
(2, 18, 50.0, 0.01, 0.50, 'Grade C', 'Local'),

-- Same ingredient in multiple orders
(4, 2, 1.0, 46.00, 46.00, 'Grade A', 'Scotland'),
(7, 2, 2.5, 44.50, 111.25, 'Grade A', 'Norway'),

-- NULL values in quality_grade and origin
(8, 22, 2.0, 18.00, 36.00, NULL, NULL),
(9, 23, 0.5, 25.00, 12.50, NULL, 'Yamanashi');

-- Note: Order 11 (empty order) intentionally has no items
