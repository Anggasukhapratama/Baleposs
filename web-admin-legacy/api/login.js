const bcrypt = require("bcryptjs");
const { query, sendJson, handleError, signToken } = require("./_db");

module.exports = async function handler(req, res) {
  if (req.method !== "POST") {
    return sendJson(res, 405, { error: "Method not allowed" });
  }

  try {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
    const username = String(body.username || "").trim();
    const password = String(body.password || "");

    const rows = await query(
      `SELECT id, username, password_hash, full_name, role
       FROM users
       WHERE username = $1 AND is_active = 1
       LIMIT 1`,
      [username]
    );

    const user = rows[0];
    if (!user || user.role !== "ADMIN_KEUANGAN") {
      return sendJson(res, 401, { error: "Akses ditolak" });
    }

    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) {
      return sendJson(res, 401, { error: "Username atau password salah" });
    }

    return sendJson(res, 200, {
      token: signToken(user),
      user: {
        id: user.id,
        username: user.username,
        fullName: user.full_name,
        role: user.role,
      },
    });
  } catch (error) {
    return handleError(res, error);
  }
};
