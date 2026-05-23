const { query, sendJson, handleError, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    if (req.method === "POST") {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
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
      return sendJson(res, 200, { ok: true });
    }

    const rows = await query(
      `SELECT code, name, contact, phone, email, address
       FROM suppliers
       WHERE is_active = 1
       ORDER BY name ASC
       LIMIT 300`,
      []
    );
    return sendJson(res, 200, { suppliers: rows });
  } catch (error) {
    return handleError(res, error);
  }
};
