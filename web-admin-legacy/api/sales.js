const { query, sendJson, handleError, getDateRange, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const period = new URL(req.url, "http://localhost").searchParams.get("period") || "all";
    const { start, end } = getDateRange(period);
    const rows = await query(
      `SELECT s.invoice_number AS invoice,
              s.sale_date AS time,
              COALESCE(u.full_name, '-') AS cashier,
              s.payment_method AS method,
              s.total_amount::bigint AS total,
              s.status
       FROM sales s
       LEFT JOIN users u ON u.id = s.created_by
       WHERE s.sale_date BETWEEN $1 AND $2
       ORDER BY s.sale_date DESC
       LIMIT 200`,
      [start, end]
    );
    return sendJson(res, 200, { sales: rows });
  } catch (error) {
    return handleError(res, error);
  }
};
