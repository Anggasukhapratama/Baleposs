import { NextResponse } from "next/server";
import bcrypt from "bcryptjs";
import { query, signToken } from "@/lib/db";

export async function POST(request: Request) {
  try {
    const body = await request.json();
    const username = String(body.username || "").trim();
    const password = String(body.password || "");

    const rows = await query<any>(
      `SELECT id, username, password_hash, full_name, role
       FROM users
       WHERE username = $1 AND is_active = 1
       LIMIT 1`,
      [username]
    );

    const user = rows[0];
    if (!user || user.role !== "ADMIN_KEUANGAN") {
      return NextResponse.json({ error: "Akses ditolak" }, { status: 401 });
    }

    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) {
      return NextResponse.json({ error: "Username atau password salah" }, { status: 401 });
    }

    return NextResponse.json({
      token: signToken(user),
      user: {
        id: user.id,
        username: user.username,
        fullName: user.full_name,
        role: user.role,
      },
    });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Server error", message: error.message }, { status: 500 });
  }
}
