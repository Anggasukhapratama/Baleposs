import { NextResponse } from "next/server";
import { query, requireFinance } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const rows = await query<any>(
      `SELECT
         (SELECT COUNT(*)::integer FROM users) AS users,
         (SELECT COUNT(*)::integer FROM sales) AS sales,
         (SELECT COUNT(*)::integer FROM products) AS products`,
      []
    );
    return NextResponse.json({
      ok: true,
      counts: rows[0],
    });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
