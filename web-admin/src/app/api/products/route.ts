import { NextResponse } from "next/server";
import { query, requireFinance } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const rows = await query<any>(
      `SELECT p.sku, p.name, p.product_type_code AS type,
              COALESCE(c.name, '-') AS category,
              COALESCE(b.name, '-') AS brand,
              p.stock::integer AS stock,
              p.selling_price::bigint AS selling_price
       FROM products p
       LEFT JOIN categories c ON c.id = p.category_id
       LEFT JOIN brands b ON b.id = p.brand_id
       WHERE p.is_active = 1
       ORDER BY p.name ASC
       LIMIT 500`,
      []
    );
    return NextResponse.json({ products: rows });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}

export async function POST(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const body = await request.json();
    const margin = 10;
    await query(
      `INSERT INTO products
         (sku, name, product_type_code, hpp, margin_percent, selling_price, stock, is_active)
       VALUES ($1, $2, $3, $4, $5, $6, $7, 1)`,
      [
        String(body.sku || "").trim(),
        String(body.name || "").trim(),
        String(body.type || "PERIPHERAL"),
        Number(body.hpp || 0),
        margin,
        Number(body.sellingPrice || 0),
        Number(body.stock || 0),
      ]
    );
    return NextResponse.json({ ok: true });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
