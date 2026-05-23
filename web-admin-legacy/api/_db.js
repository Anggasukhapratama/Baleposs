const { Pool } = require("pg");
const crypto = require("crypto");

let pool;

function getConnectionString() {
  return process.env.DATABASE_URL
    || process.env.POSTGRES_URL
    || process.env.BALETPOSTGRES_URL;
}

function getPool() {
  const connectionString = getConnectionString();
  if (!connectionString) {
    throw new Error("DATABASE_URL/POSTGRES_URL belum diset di Vercel.");
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

async function query(text, params = []) {
  const result = await getPool().query(text, params);
  return result.rows;
}

function sendJson(res, status, body) {
  res.statusCode = status;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.end(JSON.stringify(body));
}

function handleError(res, error) {
  console.error(error);
  sendJson(res, 500, {
    error: "Database error",
    message: error.message,
  });
}

function getSessionSecret() {
  return process.env.WEB_SESSION_SECRET || getConnectionString();
}

function signToken(user) {
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

function verifyToken(req) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  const [body, signature] = token.split(".");
  if (!body || !signature) return null;

  const expected = crypto
    .createHmac("sha256", getSessionSecret())
    .update(body)
    .digest("base64url");
  const signatureBuffer = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expected);
  if (signatureBuffer.length !== expectedBuffer.length
      || !crypto.timingSafeEqual(signatureBuffer, expectedBuffer)) {
    return null;
  }

  const payload = JSON.parse(Buffer.from(body, "base64url").toString("utf8"));
  if (!payload.exp || payload.exp < Date.now()) return null;
  if (payload.role !== "ADMIN_KEUANGAN") return null;
  return payload;
}

function requireFinance(req, res) {
  try {
    const user = verifyToken(req);
    if (!user) {
      sendJson(res, 401, { error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." });
      return null;
    }
    return user;
  } catch (error) {
    sendJson(res, 401, { error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." });
    return null;
  }
}

function getDateRange(period) {
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

module.exports = {
  query,
  sendJson,
  handleError,
  getDateRange,
  signToken,
  requireFinance,
};
