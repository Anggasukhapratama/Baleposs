const { query, sendJson, handleError, getDateRange, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const period = new URL(req.url, "http://localhost").searchParams.get("period") || "all";
    const { start, end } = getDateRange(period);
    const rows = await query(
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
    return sendJson(res, 200, { purchases: rows });
  } catch (error) {
    return handleError(res, error);
  }
};
