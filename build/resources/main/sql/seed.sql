-- BaletPOS Sample Data
-- Data Awal untuk Testing

-- =====================================================
-- DEFAULT TRIAL USERS
-- Passwords are hashed/refreshed by DatabaseConfig on first run.
-- =====================================================
INSERT INTO users (username, password_hash, full_name, role, is_active) VALUES
('admin', 'admin123', 'Admin Toko Legacy', 'ADMIN_TOKO', 1),
('admintoko', 'toko123', 'Admin Toko', 'ADMIN_TOKO', 1),
('adminkeuangan', 'keuangan123', 'Admin Keuangan', 'ADMIN_KEUANGAN', 1),
('kasir', 'kasir123', 'Kasir Utama', 'KASIR', 1),
('kasir2', 'kasir123', 'Kasir Cadangan', 'KASIR', 1),
('teknisi', 'teknisi123', 'Teknisi Laptop', 'KASIR', 1);

-- =====================================================
-- CATEGORIES
-- =====================================================
INSERT INTO categories (code, name, description) VALUES
('CAT-LAPTOP', 'Laptop', 'Kategori untuk semua jenis laptop'),
('CAT-PC', 'PC Desktop', 'Kategori untuk komputer desktop'),
('CAT-MONITOR', 'Monitor', 'Kategori untuk monitor komputer'),
('CAT-KEYBOARD', 'Keyboard', 'Kategori untuk keyboard'),
('CAT-MOUSE', 'Mouse', 'Kategori untuk mouse'),
('CAT-STORAGE', 'Penyimpanan', 'Kategori untuk SSD, HDD, Flashdisk'),
('CAT-MEMORY', 'RAM', 'Kategori untuk RAM/Memory'),
('CAT-AKSESORIS', 'Aksesoris', 'Kategori untuk aksesoris komputer lainnya'),
('CAT-SERVICE', 'Jasa Service', 'Kategori untuk jasa perbaikan');

-- =====================================================
-- BRANDS
-- =====================================================
INSERT INTO brands (code, name) VALUES
('ASUS', 'ASUS'),
('LENOVO', 'Lenovo'),
('HP', 'HP'),
('DELL', 'Dell'),
('ACER', 'Acer'),
('MSI', 'MSI'),
('SAMSUNG', 'Samsung'),
('LG', 'LG'),
('LOGITECH', 'Logitech'),
('RAZER', 'Razer'),
('CORSAIR', 'Corsair'),
('KINGSTON', 'Kingston'),
('WD', 'Western Digital'),
('SEAGATE', 'Seagate'),
('SANDISK', 'SanDisk'),
('GENERIC', 'Generic/Lainnya');

-- =====================================================
-- SUPPLIERS
-- =====================================================
INSERT INTO suppliers (code, name, contact, address, phone, email) VALUES
('SUP-001', 'PT. Synnex Metrodata Indonesia', 'Budi Santoso', 'Jl. Sudirman No. 123, Jakarta', '021-5551234', 'sales@synnex.co.id'),
('SUP-002', 'PT. Datascrip', 'Andi Wijaya', 'Jl. Gatot Subroto No. 45, Jakarta', '021-5552345', 'order@datascrip.co.id'),
('SUP-003', 'CV. Komputer Jaya', 'Hendra', 'Mangga Dua Mall Lt. 3, Jakarta', '021-6401234', 'hendra@komputerjaya.com'),
('SUP-004', 'PT. Ingram Micro Indonesia', 'Siti Rahma', 'Jl. TB Simatupang, Jakarta', '021-7891234', 'siti@ingrammicro.co.id'),
('SUP-005', 'UD. Mitra Komputer', 'Joko Widodo', 'Ruko ITC Cempaka Mas, Jakarta', '021-4251234', 'mitra@komputer.com');

-- =====================================================
-- CUSTOMERS
-- =====================================================
INSERT INTO customers (name, phone, address, email, notes) VALUES
('Pelanggan Umum', '-', '-', '-', 'Default customer untuk penjualan tanpa member'),
('Budi Hartono', '08123456789', 'Jl. Merdeka No. 10, Bandung', 'budi@email.com', 'Pelanggan tetap'),
('Sari Dewi', '08234567890', 'Jl. Asia Afrika No. 25, Bandung', 'sari@email.com', 'Kantor PT. ABC'),
('Ahmad Fauzi', '08345678901', 'Jl. Braga No. 15, Bandung', 'ahmad@email.com', 'Warnet Jaya Net'),
('Maria Christina', '08456789012', 'Jl. Setiabudi No. 88, Bandung', 'maria@email.com', 'Reseller');

-- =====================================================
-- PRODUCTS - LAPTOP
-- =====================================================
INSERT INTO products (sku, name, product_type_code, category_id, brand_id, hpp, margin_percent, stock, description) VALUES
('LPT-ASUS-001', 'ASUS VivoBook 14 X1402ZA i3-1215U 8GB 512GB', 'LAPTOP_NEW', 1, 1, 7500000, 12.0, 10, 'Laptop entry level untuk kerja harian'),
('LPT-ASUS-002', 'ASUS VivoBook 15 X1502ZA i5-1235U 8GB 512GB', 'LAPTOP_NEW', 1, 1, 9200000, 12.0, 8, 'Laptop mid-range untuk produktivitas'),
('LPT-ASUS-003', 'ASUS ROG Strix G15 G513RC Ryzen 7 RTX3050', 'LAPTOP_NEW', 1, 1, 15500000, 10.0, 5, 'Laptop gaming'),
('LPT-LEN-001', 'Lenovo IdeaPad Slim 3 i3-1215U 8GB 512GB', 'LAPTOP_NEW', 1, 2, 7200000, 12.0, 12, 'Laptop tipis untuk mobilitas'),
('LPT-LEN-002', 'Lenovo ThinkPad E14 Gen 4 i5-1235U 8GB 512GB', 'LAPTOP_NEW', 1, 2, 12500000, 10.0, 6, 'Laptop bisnis premium'),
('LPT-HP-001', 'HP 14s-dq5002TU i5-1235U 8GB 512GB', 'LAPTOP_NEW', 1, 3, 9000000, 12.0, 7, 'Laptop HP untuk kerja'),
('LPT-ACER-001', 'Acer Aspire 3 A314-36M i3-1215U 8GB 512GB', 'LAPTOP_NEW', 1, 5, 6800000, 12.0, 15, 'Laptop budget-friendly'),
('LPT-MSI-001', 'MSI Modern 14 C12M i5-1235U 8GB 512GB', 'LAPTOP_NEW', 1, 6, 10500000, 10.0, 4, 'Laptop ultrabook MSI');

-- =====================================================
-- PRODUCTS - PERIPHERAL
-- =====================================================
INSERT INTO products (sku, name, product_type_code, category_id, brand_id, hpp, margin_percent, stock, description) VALUES
('PRF-LOG-001', 'Logitech MK270 Wireless Combo', 'PERIPHERAL', 4, 9, 350000, 20.0, 25, 'Keyboard + Mouse Wireless'),
('PRF-LOG-002', 'Logitech G102 Gaming Mouse', 'PERIPHERAL', 5, 9, 280000, 20.0, 30, 'Mouse gaming entry level'),
('PRF-LOG-003', 'Logitech K380 Bluetooth Keyboard', 'PERIPHERAL', 4, 9, 450000, 18.0, 20, 'Keyboard bluetooth multi-device'),
('PRF-RAZ-001', 'Razer DeathAdder Essential', 'PERIPHERAL', 5, 10, 380000, 18.0, 15, 'Mouse gaming Razer'),
('PRF-COR-001', 'Corsair K55 RGB Gaming Keyboard', 'PERIPHERAL', 4, 11, 850000, 15.0, 10, 'Keyboard gaming RGB'),
('PRF-MON-001', 'ASUS VA24DQ 24" IPS FHD', 'PERIPHERAL', 3, 1, 2100000, 12.0, 8, 'Monitor 24 inch IPS'),
('PRF-MON-002', 'LG 24MK430H 24" IPS FHD', 'PERIPHERAL', 3, 8, 1950000, 12.0, 10, 'Monitor LG IPS'),
('PRF-MON-003', 'Samsung S24R350 24" IPS FHD', 'PERIPHERAL', 3, 7, 2050000, 12.0, 6, 'Monitor Samsung IPS'),
('PRF-SSD-001', 'Kingston A400 480GB SATA SSD', 'PERIPHERAL', 6, 12, 450000, 18.0, 20, 'SSD SATA 480GB'),
('PRF-SSD-002', 'WD Blue SN570 500GB NVMe', 'PERIPHERAL', 6, 13, 620000, 15.0, 15, 'SSD NVMe 500GB'),
('PRF-SSD-003', 'Samsung 870 EVO 500GB SATA', 'PERIPHERAL', 6, 7, 850000, 12.0, 10, 'SSD Samsung premium'),
('PRF-RAM-001', 'Kingston Fury Beast 8GB DDR4 3200MHz', 'PERIPHERAL', 7, 12, 320000, 18.0, 25, 'RAM DDR4 8GB'),
('PRF-RAM-002', 'Corsair Vengeance LPX 16GB DDR4 3200MHz', 'PERIPHERAL', 7, 11, 680000, 15.0, 15, 'RAM DDR4 16GB'),
('PRF-FD-001', 'SanDisk Ultra Flair 32GB USB 3.0', 'PERIPHERAL', 6, 15, 85000, 25.0, 50, 'Flashdisk 32GB'),
('PRF-FD-002', 'SanDisk Ultra Flair 64GB USB 3.0', 'PERIPHERAL', 6, 15, 120000, 25.0, 40, 'Flashdisk 64GB');

-- =====================================================
-- PRODUCTS - LAPTOP SECOND (Laptop Bekas)
-- =====================================================
INSERT INTO products (sku, name, product_type_code, category_id, brand_id, hpp, margin_percent, stock, description) VALUES
('SEC-LEN-001', 'Lenovo ThinkPad T480 i5-8350U 8GB 256GB', 'LAPTOP_SECOND', 1, 2, 4500000, 20.0, 3, 'Laptop bekas bisnis, kondisi 85%'),
('SEC-LEN-002', 'Lenovo ThinkPad X1 Carbon Gen 6 i7-8550U 16GB 512GB', 'LAPTOP_SECOND', 1, 2, 7500000, 18.0, 2, 'Ultrabook bekas premium, kondisi 90%'),
('SEC-HP-001', 'HP EliteBook 840 G5 i5-8250U 8GB 256GB', 'LAPTOP_SECOND', 1, 3, 4200000, 20.0, 4, 'Laptop bekas bisnis HP, kondisi 85%'),
('SEC-HP-002', 'HP ProBook 450 G6 i7-8565U 8GB 512GB', 'LAPTOP_SECOND', 1, 3, 5500000, 18.0, 2, 'Laptop bekas kerja, kondisi 88%'),
('SEC-DELL-001', 'Dell Latitude 5490 i5-8350U 8GB 256GB', 'LAPTOP_SECOND', 1, 4, 4300000, 20.0, 3, 'Laptop bekas enterprise Dell, kondisi 85%'),
('SEC-DELL-002', 'Dell XPS 13 9370 i7-8550U 16GB 512GB', 'LAPTOP_SECOND', 1, 4, 8000000, 15.0, 1, 'Ultrabook bekas premium, kondisi 92%'),
('SEC-ASUS-001', 'ASUS ZenBook UX430UA i5-8250U 8GB 256GB', 'LAPTOP_SECOND', 1, 1, 4000000, 20.0, 2, 'Ultrabook bekas ASUS, kondisi 85%'),
('SEC-ACER-001', 'Acer Swift 3 SF314-52 i5-8250U 8GB 256GB', 'LAPTOP_SECOND', 1, 5, 3500000, 22.0, 3, 'Laptop bekas tipis Acer, kondisi 82%');

-- =====================================================
-- PRODUCTS - SPAREPARTS (Sparepart Laptop)
-- =====================================================
INSERT INTO products (sku, name, product_type_code, category_id, brand_id, hpp, margin_percent, stock, description) VALUES
('SPR-BAT-001', 'Baterai Laptop ASUS A456U Original', 'SPAREPARTS', 8, 1, 450000, 25.0, 10, 'Baterai original ASUS A456 series'),
('SPR-BAT-002', 'Baterai Laptop Lenovo ThinkPad T480', 'SPAREPARTS', 8, 2, 550000, 25.0, 8, 'Baterai original Lenovo T480'),
('SPR-BAT-003', 'Baterai Laptop HP Pavilion 14', 'SPAREPARTS', 8, 3, 420000, 25.0, 12, 'Baterai original HP Pavilion'),
('SPR-LCD-001', 'LCD Laptop 14" HD 30pin Universal', 'SPAREPARTS', 8, 16, 650000, 20.0, 6, 'Panel LCD 14 inch HD'),
('SPR-LCD-002', 'LCD Laptop 15.6" FHD 30pin Universal', 'SPAREPARTS', 8, 16, 850000, 20.0, 5, 'Panel LCD 15.6 inch Full HD'),
('SPR-LCD-003', 'LCD Laptop 14" FHD IPS 30pin', 'SPAREPARTS', 8, 16, 950000, 18.0, 4, 'Panel LCD IPS 14 inch FHD'),
('SPR-KEY-001', 'Keyboard Laptop ASUS X455 X455L', 'SPAREPARTS', 8, 1, 180000, 30.0, 15, 'Keyboard ASUS X455 series'),
('SPR-KEY-002', 'Keyboard Laptop Lenovo IdeaPad 320', 'SPAREPARTS', 8, 2, 200000, 30.0, 12, 'Keyboard Lenovo IdeaPad 320'),
('SPR-KEY-003', 'Keyboard Laptop HP 14-BS 14-BW', 'SPAREPARTS', 8, 3, 175000, 30.0, 18, 'Keyboard HP 14 series'),
('SPR-CHG-001', 'Charger Laptop ASUS 19V 3.42A 65W', 'SPAREPARTS', 8, 1, 150000, 35.0, 20, 'Adaptor ASUS 65W'),
('SPR-CHG-002', 'Charger Laptop Lenovo 20V 3.25A 65W', 'SPAREPARTS', 8, 2, 180000, 35.0, 15, 'Adaptor Lenovo 65W USB-C'),
('SPR-CHG-003', 'Charger Laptop HP 19.5V 3.33A 65W', 'SPAREPARTS', 8, 3, 160000, 35.0, 18, 'Adaptor HP 65W Blue Pin'),
('SPR-FAN-001', 'Kipas Cooling Laptop ASUS X455', 'SPAREPARTS', 8, 1, 120000, 40.0, 10, 'Fan cooling ASUS X455 series'),
('SPR-FAN-002', 'Kipas Cooling Laptop Lenovo IdeaPad', 'SPAREPARTS', 8, 2, 135000, 40.0, 8, 'Fan cooling Lenovo IdeaPad'),
('SPR-HNG-001', 'Engsel Laptop ASUS A456U Sepasang', 'SPAREPARTS', 8, 1, 250000, 30.0, 6, 'Hinge ASUS A456 kiri-kanan'),
('SPR-HNG-002', 'Engsel Laptop HP 14-BS Sepasang', 'SPAREPARTS', 8, 3, 220000, 30.0, 8, 'Hinge HP 14-BS kiri-kanan');

-- =====================================================
-- PRODUCTS - SERVICE
-- =====================================================
INSERT INTO products (sku, name, product_type_code, category_id, brand_id, hpp, margin_percent, stock, description) VALUES
('SVC-INST-001', 'Jasa Install Windows + Office + Software', 'SERVICE', 9, 16, 0, 0, 999, 'Instalasi OS dan aplikasi standar'),
('SVC-INST-002', 'Jasa Install Ulang Windows', 'SERVICE', 9, 16, 0, 0, 999, 'Instalasi ulang Windows bersih'),
('SVC-UPDT-001', 'Jasa Upgrade RAM', 'SERVICE', 9, 16, 0, 0, 999, 'Jasa pemasangan RAM baru'),
('SVC-UPDT-002', 'Jasa Upgrade SSD', 'SERVICE', 9, 16, 0, 0, 999, 'Jasa pemasangan SSD + cloning data'),
('SVC-REPR-001', 'Jasa Perbaikan Laptop Ringan', 'SERVICE', 9, 16, 0, 0, 999, 'Perbaikan ringan: keyboard, touchpad, dll'),
('SVC-REPR-002', 'Jasa Perbaikan Laptop Berat', 'SERVICE', 9, 16, 0, 0, 999, 'Perbaikan berat: motherboard, layar, dll'),
('SVC-CLNG-001', 'Jasa Cleaning Laptop + Repaste', 'SERVICE', 9, 16, 0, 0, 999, 'Pembersihan dalam dan ganti thermal paste'),
('SVC-RCVR-001', 'Jasa Recovery Data', 'SERVICE', 9, 16, 0, 0, 999, 'Recovery data dari storage rusak');

-- Update harga jual untuk service (manual tanpa margin)
UPDATE products SET selling_price = 150000 WHERE sku = 'SVC-INST-001';
UPDATE products SET selling_price = 100000 WHERE sku = 'SVC-INST-002';
UPDATE products SET selling_price = 50000 WHERE sku = 'SVC-UPDT-001';
UPDATE products SET selling_price = 75000 WHERE sku = 'SVC-UPDT-002';
UPDATE products SET selling_price = 150000 WHERE sku = 'SVC-REPR-001';
UPDATE products SET selling_price = 350000 WHERE sku = 'SVC-REPR-002';
UPDATE products SET selling_price = 100000 WHERE sku = 'SVC-CLNG-001';
UPDATE products SET selling_price = 250000 WHERE sku = 'SVC-RCVR-001';

-- =====================================================
-- EXPENSE CODES (Kode Biaya Operasional)
-- =====================================================
INSERT INTO expense_codes (code, name, description) VALUES
('EXP-SEWA', 'Biaya Sewa Toko', 'Biaya sewa tempat usaha bulanan'),
('EXP-LISTRIK', 'Biaya Listrik', 'Tagihan listrik bulanan'),
('EXP-AIR', 'Biaya Air', 'Tagihan air/PDAM bulanan'),
('EXP-INTERNET', 'Biaya Internet', 'Tagihan internet bulanan'),
('EXP-GAJI', 'Gaji Karyawan', 'Pengeluaran gaji karyawan'),
('EXP-ATK', 'Alat Tulis Kantor', 'Pembelian ATK dan perlengkapan'),
('EXP-PACKAGING', 'Packaging', 'Biaya packaging dan plastik'),
('EXP-TRANSPORT', 'Transport', 'Biaya transportasi operasional'),
('EXP-MAKAN', 'Konsumsi', 'Biaya makan minum karyawan'),
('EXP-LAIN', 'Biaya Lain-lain', 'Biaya operasional lainnya');

-- =====================================================
-- SETTINGS
-- =====================================================
INSERT INTO settings (setting_key, setting_value, description) VALUES
('store_name', 'BALET COMPUTER', 'Nama toko'),
('store_address', 'Jl. Komputer No. 123, Bandung', 'Alamat toko'),
('store_phone', '022-12345678', 'Telepon toko'),
('store_email', 'info@baletcomputer.com', 'Email toko'),
('default_margin_laptop', '12.0', 'Margin default untuk laptop (%)'),
('default_margin_peripheral', '18.0', 'Margin default untuk peripheral (%)'),
('default_margin_service', '100.0', 'Margin default untuk jasa service (%)'),
('receipt_footer', 'Terima kasih atas kunjungan Anda!', 'Footer struk'),
('receipt_warranty_note', 'Barang yang sudah dibeli tidak dapat dikembalikan kecuali cacat pabrik.', 'Catatan garansi di struk');

-- =====================================================
-- SAMPLE PURCHASES (Pembelian Barang dari Supplier)
-- =====================================================
INSERT INTO purchases (purchase_number, supplier_id, purchase_date, total_amount, notes, status, created_by) VALUES
('PO-20260101-0001', 1, '2026-01-01 10:00:00', 75000000, 'Pembelian awal tahun - Laptop ASUS', 'COMPLETED', 1),
('PO-20260101-0002', 3, '2026-01-01 14:00:00', 5600000, 'Pembelian peripheral Logitech', 'COMPLETED', 1);

INSERT INTO purchase_items (purchase_id, product_id, quantity, hpp_per_unit, subtotal) VALUES
(1, 1, 5, 7500000, 37500000),
(1, 2, 4, 9200000, 36800000),
(2, 9, 10, 350000, 3500000),
(2, 10, 10, 280000, 2800000);

-- =====================================================
-- SAMPLE EXPENSES (Biaya Operasional)
-- =====================================================
INSERT INTO expenses (expense_number, expense_code_id, expense_date, amount, description, created_by) VALUES
('EXP-20260101-0001', 1, '2026-01-01', 5000000, 'Pembayaran sewa toko bulan Januari', 1),
('EXP-20260101-0002', 2, '2026-01-01', 850000, 'Tagihan listrik Desember', 1),
('EXP-20260102-0001', 5, '2026-01-02', 8500000, 'Gaji karyawan bulan Desember', 1);
