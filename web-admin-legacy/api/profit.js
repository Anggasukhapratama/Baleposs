const { query, sendJson, handleError, getDateRange, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const period = new URL(req.url, "http://localhost").searchParams.get("period") || "all";
    const { start, end } = getDateRange(period);
    const rows = await query(
      `WITH sales_summary AS (
         SELECT COALESCE(SUM(total_amount), 0)::bigint AS revenue,
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
       SELECT * FROM sales_summary, returns_summary, expense_summary`,
      [start, end]
    );

    const row = rows[0];
    const revenue = Number(row.revenue || 0);
    const returns = Number(row.returns || 0);
    const hpp = Number(row.hpp || 0);
    const expenses = Number(row.expenses || 0);
    return sendJson(res, 200, {
      rows: [
        ["Penjualan Kotor", revenue],
        ["Retur Penjualan", -returns],
        ["Pendapatan Bersih", revenue - returns, "total"],
        ["HPP Penjualan", -hpp],
        ["Gross Profit", revenue - hpp, "total"],
        ["Biaya Operasional", -expenses],
        ["Laba Bersih", revenue - returns - hpp - expenses, "total"],
      ],
    });
  } catch (error) {
    return handleError(res, error);
  }
};
