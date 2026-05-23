# BaletPOS Desktop - Panduan Trial Toko

Dokumen ini dipakai untuk trial operasional BaletPOS desktop di toko.

## Status Kesiapan

Hasil validasi terakhir:

- Build desktop: sukses.
- Unit test: sukses.
- Startup desktop: sukses sampai layar login.
- Database aktif: Supabase/PostgreSQL online.
- Integrity check database: passed.
- Produk di database: 87 item.
- Sales historis di database: 37 transaksi.
- User aktif: 3 user.
- Kolom POS laptop `bonus_product_id`, `bonus_product_name`, dan `warranty_label`: tersedia.
- Nota dot-matrix continuous form sudah memakai logo dan form feed.

## Akun Login Trial

Gunakan hanya 3 akun ini:

| Role | Username | Password | Pemakaian |
| --- | --- | --- | --- |
| Kasir | `kasir` | `kasir123` | POS / transaksi harian |
| Admin Toko | `admintoko` | `toko123` | Monitor operasional toko |
| Admin Keuangan | `adminkeuangan` | `keuangan123` | Laporan dan web admin |

User lama `admin`, `kasir2`, dan `teknisi` sudah dinonaktifkan. Aplikasi desktop juga sudah menjaga supaya saat startup user aktif tetap hanya 3 akun di atas.

## Menjalankan Desktop

Dari folder project:

```powershell
cd C:\skripsi\baletpos
.\gradlew.bat run
```

Tanda startup normal di terminal:

```text
Using POSTGRES database
Database integrity check passed.
Trial user ensured: admintoko (ADMIN_TOKO)
Trial user ensured: adminkeuangan (ADMIN_KEUANGAN)
Trial user ensured: kasir (KASIR)
Legacy trial users deactivated: admin, kasir2, teknisi
Automatic backup scheduled.
```

Kalau muncul layar login, desktop sudah siap dites.

## Konfigurasi Database Desktop

Desktop membaca konfigurasi dari:

```text
C:\Users\<user>\.baletpos\config.properties
```

Format yang dipakai:

```properties
baletpos.db.url=jdbc:postgresql://aws-1-ap-northeast-2.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0
baletpos.db.user=postgres.<PROJECT_REF>
baletpos.db.password=<PASSWORD>
```

Untuk menulis ulang config:

```powershell
.\scripts\configure-supabase-desktop.ps1 -DbPassword "<PASSWORD_SUPABASE>"
```

Catatan: jangan share file `config.properties` karena berisi password database.

## Checklist Trial Di Toko

1. Login sebagai `kasir`.
2. Buka menu POS / Kasir.
3. Tambahkan produk biasa dan pastikan subtotal berubah.
4. Tambahkan produk laptop.
5. Isi `Serial Number` dan `Nama Pembeli`.
6. Pilih `Bonus dari Peripheral` jika ada bonus.
7. Pilih garansi: `2 Minggu`, `1 Tahun`, atau `2 Tahun`.
8. Selesaikan pembayaran.
9. Pastikan stok produk utama berkurang.
10. Jika ada bonus, pastikan stok peripheral bonus juga berkurang.
11. Cetak nota dan pastikan logo tampil.
12. Cek transaksi muncul di dashboard desktop dan web admin.
13. Login sebagai `adminkeuangan`, buka laporan dan pastikan angka terbaca.
14. Coba stock opname untuk satu item kecil, lalu cek stok berubah.

## Nota Dan Printer

Nota desktop memakai:

- Logo dari resource `/images/logonota.png`.
- Lebar nota 80 kolom.
- Mode dot-matrix pica 10 CPI.
- Line spacing 1/6 inch.
- Continuous form 9.5 inch x 11 inch.
- Form feed di akhir nota supaya maju ke perforasi berikutnya.

Jika printer terpotong:

1. Pastikan kertas di Windows diset continuous form 9.5 x 11 inch.
2. Pastikan printer dot-matrix menjadi default printer Windows, atau set env:

```powershell
$env:BALETPOS_RECEIPT_PRINTER="Nama Printer"
```

3. Jalankan ulang desktop.

## Stock Opname

Stock opname bisa dicek dari desktop dan web admin karena memakai database yang sama.

## Upload Produk & Stok Excel

Upload Excel dilakukan dari desktop oleh Admin Toko.

Login:

```text
admintoko / toko123
```

Alur:

1. Buka menu `Produk`.
2. Klik `Import Excel`.
3. Klik `Download Template Excel`.
4. Isi kolom template: `SKU`, `Nama Produk`, `Tipe Produk`, `HPP`, `Margin (%)`, `Harga Jual`, `Stok`, `Deskripsi`.
5. Klik `Import File Excel`.
6. SKU baru akan dibuat sebagai produk baru.
7. SKU yang sudah ada akan update stok, HPP, margin, harga jual, nama, tipe produk, dan deskripsi.

Catatan:

- `Tipe Produk` mengikuti nilai aplikasi: `LAPTOP_NEW`, `LAPTOP_SECOND`, `SPAREPARTS`, `PERIPHERAL`, `SERVICE`.
- Import Excel tidak tersedia untuk role Kasir.
- Untuk stok opname fisik, tetap pakai menu stock opname agar ada catatan adjustment.

Alur trial:

1. Pilih produk.
2. Catat stok sistem.
3. Isi stok fisik.
4. Simpan adjustment.
5. Pastikan `stock_adjustments` dan `stock_movements` bertambah.
6. Pastikan stok produk berubah sesuai hasil fisik.

## Web Admin Keuangan

Web admin memakai database yang sama dengan desktop.

Local preview:

```powershell
cd C:\skripsi\baletpos\web-admin
npm start
```

URL lokal:

```text
http://localhost:4173
```

Login:

```text
adminkeuangan / keuangan123
```

Untuk deploy Vercel, set Root Directory ke `web-admin` dan isi env:

```text
DATABASE_URL=postgresql://postgres.<PROJECT_REF>:<PASSWORD_URL_ENCODED>@aws-1-ap-northeast-2.pooler.supabase.com:6543/postgres?sslmode=require
WEB_SESSION_SECRET=<RANDOM_SECRET>
```

## Validasi Teknis

Jalankan ini sebelum trial:

```powershell
.\gradlew.bat test
.\gradlew.bat build
cd web-admin
node scripts\check-trial-readiness.js
```

Expected result dari readiness check:

```text
saleItemColumns: bonus_product_id, bonus_product_name, warranty_label
active_users: 3
expected_users: 3
products: > 0
```

## Troubleshooting Cepat

Login gagal:

- Pastikan username/password sesuai tabel di atas.
- Pastikan user aktif hanya `kasir`, `admintoko`, `adminkeuangan`.
- Jalankan `node web-admin\scripts\ensure-three-users.js`.

Desktop tidak konek DB:

- Cek `C:\Users\<user>\.baletpos\config.properties`.
- Pastikan pakai pooler port `6543`.
- Pastikan URL JDBC punya `sslmode=require&prepareThreshold=0`.
- Pastikan internet toko aktif.

Nota tidak keluar:

- Pastikan default printer Windows benar.
- Jika printer bukan default, set `BALETPOS_RECEIPT_PRINTER`.
- Cek nama printer dari Windows Settings.

Dashboard kosong:

- Pastikan tanggal filter sesuai periode transaksi.
- Cek koneksi Supabase.
- Cek transaksi berstatus `COMPLETED`.

Stok bonus tidak berkurang:

- Pastikan bonus dipilih dari dropdown `Bonus dari Peripheral`.
- Pastikan produk bonus bertipe `PERIPHERAL`.
- Pastikan stok bonus lebih dari 0 saat transaksi.
