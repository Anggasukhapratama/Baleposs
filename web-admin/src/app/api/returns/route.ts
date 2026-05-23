import { NextResponse } from "next/server";
import { query, requireFinance } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const [returns, movements] = await Promise.all([
      query<any>(
        `SELECT sr.return_number AS number,
                s.invoice_number AS invoice,
                sr.return_date AS date,
                sr.total_amount::bigint AS total,
                sr.status
         FROM sales_returns sr
         JOIN sales s ON s.id = sr.sale_id
         ORDER BY sr.return_date DESC
         LIMIT 100`,
        []
      ),
      query<any>(
        `SELECT sm.created_at AS date,
                p.name AS product,
                sm.movement_type AS type,
                sm.quantity_change::integer AS qty
         FROM stock_movements sm
         JOIN products p ON p.id = sm.product_id
         ORDER BY sm.created_at DESC
         LIMIT 100`,
        []
      ),
    ]);

    return NextResponse.json({ returns, movements });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
