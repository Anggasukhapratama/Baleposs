const { query, sendJson, handleError, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const rows = await query(
      `SELECT
         (SELECT COUNT(*)::integer FROM users) AS users,
         (SELECT COUNT(*)::integer FROM sales) AS sales,
         (SELECT COUNT(*)::integer FROM products) AS products`,
      []
    );
    return sendJson(res, 200, {
      ok: true,
      counts: rows[0],
    });
  } catch (error) {
    return handleError(res, error);
  }
};
