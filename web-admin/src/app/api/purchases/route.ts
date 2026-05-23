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
      `SELECT p.purchase_number AS number,
              p.purchase_date AS date,
              COALESCE(s.name, '-') AS supplier,
              p.total_amount::bigint AS total,
              p.status
       FROM purchases p
       LEFT JOIN suppliers s ON s.id = p.supplier_id
       WHERE p.purchase_date BETWEEN $1 AND $2
       ORDER BY p.purchase_date DESC
       LIMIT 200`,
      [start, end]
    );

    return NextResponse.json({ purchases: rows });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
