import { NextResponse } from "next/server";
import { query, requireFinance } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const rows = await query<any>(
      `SELECT sku, name, product_type_code AS type, stock::integer AS system,
              stock::integer AS physical, hpp::bigint, selling_price::bigint
       FROM products
       WHERE is_active = 1
       ORDER BY stock ASC, name ASC
       LIMIT 500`,
      []
    );
    return NextResponse.json({ stock: rows });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}

export async function POST(request: Request) {
  const user = requireFinance(request);
  if (!user) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const body = await request.json();
    const sku = String(body.sku || "").trim();
    const physical = Number(body.physical || 0);
    const note = String(body.note || "Stock opname web");

    const productRows = await query<any>("SELECT id, stock FROM products WHERE sku = $1 LIMIT 1", [sku]);
    const product = productRows[0];
    if (!product) {
      return NextResponse.json({ error: "Produk tidak ditemukan" }, { status: 404 });
    }

    const diff = physical - Number(product.stock || 0);
    if (diff !== 0) {
      const createdBy = user.id;

      await query(
        `INSERT INTO stock_adjustments
           (adjustment_number, product_id, adjustment_date, quantity_change, reason, notes, created_by)
         VALUES
           ('ADJ-' || to_char(NOW(), 'YYYYMMDDHH24MISSMS'), $1, NOW(), $2, 'OPNAME', $3, $4)`,
        [product.id, diff, note, createdBy]
      );
      await query(
        `INSERT INTO stock_movements
           (product_id, movement_type, reference_type, reference_id, quantity_change,
            stock_before, stock_after, notes, created_by)
         VALUES ($1, 'ADJUSTMENT', 'STOCK_OPNAME_WEB', NULL, $2, $3, $4, $5, $6)`,
        [product.id, diff, product.stock, physical, note, createdBy]
      );
      await query("UPDATE products SET stock = $1, updated_at = LOCALTIMESTAMP(0) WHERE id = $2", [physical, product.id]);
    }

    return NextResponse.json({ ok: true, sku, physical, diff });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
