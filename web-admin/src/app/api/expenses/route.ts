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
      `SELECT e.expense_number AS code,
              COALESCE(ec.name, '-') AS category,
              e.description AS note,
              e.amount::bigint AS total,
              'Disetujui' AS status
       FROM expenses e
       LEFT JOIN expense_codes ec ON ec.id = e.expense_code_id
       WHERE e.expense_date BETWEEN $1::date AND $2::date
       ORDER BY e.expense_date DESC, e.id DESC
       LIMIT 200`,
      [start, end]
    );

    return NextResponse.json({ expenses: rows });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
