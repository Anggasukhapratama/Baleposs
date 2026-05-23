import { NextResponse } from "next/server";
import { query, requireFinance } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const rows = await query<any>(
      `SELECT code, name, contact, phone, email, address
       FROM suppliers
       WHERE is_active = 1
       ORDER BY name ASC
       LIMIT 300`,
      []
    );
    return NextResponse.json({ suppliers: rows });
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
    await query(
      `INSERT INTO suppliers (code, name, contact, address, phone, email, is_active)
       VALUES ($1, $2, $3, $4, $5, $6, 1)`,
      [
        String(body.code || "").trim(),
        String(body.name || "").trim(),
        String(body.contact || ""),
        String(body.address || ""),
        String(body.phone || ""),
        String(body.email || ""),
      ]
    );
    return NextResponse.json({ ok: true });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
