const { query, sendJson, handleError, getDateRange, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const period = new URL(req.url, "http://localhost").searchParams.get("period") || "all";
    const { start, end } = getDateRange(period);
    const rows = await query(
      `SELECT e.expense_number AS code,
              COALESCE(ec.name, '-') AS category,
              e.description AS note,
              e.amount::bigint AS total,
              'Disetujui' AS status
       FROM expenses e
       LEFT JOIN expense_codes ec ON ec.id = e.expense_code_id
       WHERE e.expense_date BETWEEN $1::date AND $2::date
       ORDER BY e.expense_date DESC, e.id DESC
       LIMIT 200`,
      [start, end]
    );
    return sendJson(res, 200, { expenses: rows });
  } catch (error) {
    return handleError(res, error);
  }
};
