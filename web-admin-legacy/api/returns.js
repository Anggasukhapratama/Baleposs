const { query, sendJson, handleError, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const [returns, movements] = await Promise.all([
      query(
        `SELECT sr.return_number AS number,
                s.invoice_number AS invoice,
                sr.return_date AS date,
                sr.total_amount::bigint AS total,
                sr.status
         FROM sales_returns sr
         JOIN sales s ON s.id = sr.sale_id
         ORDER BY sr.return_date DESC
         LIMIT 100`,
        []
      ),
      query(
        `SELECT sm.created_at AS date,
                p.name AS product,
                sm.movement_type AS type,
                sm.quantity_change::integer AS qty
         FROM stock_movements sm
         JOIN products p ON p.id = sm.product_id
         ORDER BY sm.created_at DESC
         LIMIT 100`,
        []
      ),
    ]);

    return sendJson(res, 200, { returns, movements });
  } catch (error) {
    return handleError(res, error);
  }
};
