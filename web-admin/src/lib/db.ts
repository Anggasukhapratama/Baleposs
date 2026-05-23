import { Pool } from "pg";
import crypto from "crypto";

let pool: Pool | null = null;

function getConnectionString(): string {
  return process.env.DATABASE_URL
    || process.env.POSTGRES_URL
    || process.env.BALETPOSTGRES_URL
    || "";
}

export function getPool(): Pool {
  const connectionString = getConnectionString();
  if (!connectionString) {
    throw new Error("DATABASE_URL/POSTGRES_URL belum diset di Vercel atau environment.");
  }

  if (!pool) {
    pool = new Pool({
      connectionString,
      ssl: connectionString.includes("localhost") ? false : { rejectUnauthorized: false },
      max: 3,
    });
  }

  return pool;
}

export async function query<T = any>(text: string, params: any[] = []): Promise<T[]> {
  const result = await getPool().query(text, params);
  return result.rows;
}

export function getSessionSecret(): string {
  return process.env.WEB_SESSION_SECRET || getConnectionString() || "fallback-secret-key-1234567890";
}

export function signToken(user: { id: number; username: string; role: string }): string {
  const payload = {
    id: user.id,
    username: user.username,
    role: user.role,
    exp: Date.now() + 8 * 60 * 60 * 1000,
  };
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const signature = crypto
    .createHmac("sha256", getSessionSecret())
    .update(body)
    .digest("base64url");
  return `${body}.${signature}`;
}

export function verifyToken(token: string): { id: number; username: string; role: string } | null {
  if (!token) return null;
  const [body, signature] = token.split(".");
  if (!body || !signature) return null;

  const expected = crypto
    .createHmac("sha256", getSessionSecret())
    .update(body)
    .digest("base64url");

  const signatureBuffer = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expected);

  if (signatureBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(signatureBuffer, expectedBuffer)) {
    return null;
  }

  try {
    const payload = JSON.parse(Buffer.from(body, "base64url").toString("utf8"));
    if (!payload.exp || payload.exp < Date.now()) return null;
    if (payload.role !== "ADMIN_KEUANGAN") return null;
    return payload;
  } catch {
    return null;
  }
}

export function requireFinance(req: Request): { id: number; username: string; role: string } | null {
  const authHeader = req.headers.get("authorization") || "";
  const token = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";
  return verifyToken(token);
}

export function getDateRange(period: string): { start: string; end: string } {
  if (period === "all") {
    return {
      start: "1900-01-01T00:00:00.000Z",
      end: "2999-12-31T23:59:59.999Z",
    };
  }

  const now = new Date();
  const end = new Date(now);
  end.setHours(23, 59, 59, 999);

  const start = new Date(now);
  start.setHours(0, 0, 0, 0);

  if (period === "week") {
    const day = start.getDay() || 7;
    start.setDate(start.getDate() - day + 1);
  } else if (period === "month") {
    start.setDate(1);
  }

  return {
    start: start.toISOString(),
    end: end.toISOString(),
  };
}
