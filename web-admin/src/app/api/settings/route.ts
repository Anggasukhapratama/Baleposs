import { NextResponse } from "next/server";
import { query, requireFinance } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const rows = await query<any>(
      `SELECT setting_key AS key, setting_value AS value, description
       FROM settings
       ORDER BY setting_key ASC`,
      []
    );
    return NextResponse.json({ settings: rows });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
