import { NextResponse } from "next/server";
import { query, requireFinance, getDateRange } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const url = new URL(request.url);
    const period = url.searchParams.get("period") || "all";
    const { start, end } = getDateRange(period);

    const rows = await query<any>(
      `SELECT s.invoice_number AS invoice,
              s.sale_date AS time,
              COALESCE(u.full_name, '-') AS cashier,
              s.payment_method AS method,
              s.total_amount::bigint AS total,
              s.status
       FROM sales s
       LEFT JOIN users u ON u.id = s.created_by
       WHERE s.sale_date BETWEEN $1 AND $2
       ORDER BY s.sale_date DESC
       LIMIT 200`,
      [start, end]
    );

    return NextResponse.json({ sales: rows });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
