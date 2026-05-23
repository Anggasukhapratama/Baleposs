const { query, sendJson, handleError, getDateRange, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const period = new URL(req.url, "http://localhost").searchParams.get("period") || "all";
    const { start, end } = getDateRange(period);

    const [summaryRows, paymentRows, trendRows, transactionRows, approvalRows] = await Promise.all([
      query(
        `WITH sales_summary AS (
           SELECT
             COALESCE(SUM(total_amount), 0)::bigint AS revenue,
             COALESCE(SUM(total_hpp), 0)::bigint AS hpp
           FROM sales
           WHERE sale_date BETWEEN $1 AND $2
             AND status = 'COMPLETED'
         ),
         returns_summary AS (
           SELECT COALESCE(SUM(total_amount), 0)::bigint AS returns
           FROM sales_returns
           WHERE return_date BETWEEN $1 AND $2
             AND status = 'COMPLETED'
         ),
         expense_summary AS (
           SELECT COALESCE(SUM(amount), 0)::bigint AS expenses
           FROM expenses
           WHERE expense_date BETWEEN $1::date AND $2::date
         )
         SELECT
           sales_summary.revenue,
           returns_summary.returns,
           (sales_summary.revenue - returns_summary.returns)::bigint AS net_revenue,
           (sales_summary.revenue - returns_summary.returns - sales_summary.hpp)::bigint AS gross_profit,
           expense_summary.expenses,
           (sales_summary.revenue - returns_summary.returns - sales_summary.hpp - expense_summary.expenses)::bigint AS net_profit
         FROM sales_summary, returns_summary, expense_summary`,
        [start, end]
      ),
      query(
        `SELECT method AS name, COALESCE(SUM(amount), 0)::bigint AS amount
         FROM sale_payments sp
         JOIN sales s ON s.id = sp.sale_id
         WHERE s.sale_date BETWEEN $1 AND $2
           AND s.status = 'COMPLETED'
         GROUP BY method
         ORDER BY amount DESC`,
        [start, end]
      ),
      query(
        `SELECT
           to_char(day::date, 'Dy') AS day,
           COALESCE(SUM(s.total_amount), 0)::bigint AS revenue,
           COALESCE(SUM(s.total_amount - s.total_hpp), 0)::bigint AS profit
         FROM generate_series(
           (COALESCE((SELECT MAX(sale_date)::date FROM sales WHERE status = 'COMPLETED' AND sale_date BETWEEN $1 AND $2), CURRENT_DATE) - INTERVAL '6 days')::date,
           COALESCE((SELECT MAX(sale_date)::date FROM sales WHERE status = 'COMPLETED' AND sale_date BETWEEN $1 AND $2), CURRENT_DATE),
           INTERVAL '1 day'
         ) day
         LEFT JOIN sales s ON s.sale_date::date = day::date AND s.status = 'COMPLETED'
         GROUP BY day
         ORDER BY day`,
        [start, end]
      ),
      query(
        `SELECT invoice_number AS invoice, sale_date AS time, payment_method AS method,
                total_amount::bigint AS total, status
         FROM sales
         ORDER BY sale_date DESC
         LIMIT 10`,
        []
      ),
      query(
        `SELECT e.expense_number AS title,
                COALESCE(ec.name, 'Biaya') || ' - ' || e.amount::text AS meta,
                'Review' AS status
         FROM expenses e
         LEFT JOIN expense_codes ec ON ec.id = e.expense_code_id
         ORDER BY e.created_at DESC
         LIMIT 5`,
        []
      ),
    ]);

    return sendJson(res, 200, {
      summary: summaryRows[0],
      payments: paymentRows,
      trend: trendRows,
      transactions: transactionRows,
      approvals: approvalRows,
    });
  } catch (error) {
    return handleError(res, error);
  }
};
