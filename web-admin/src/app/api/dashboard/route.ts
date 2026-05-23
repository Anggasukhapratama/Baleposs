import { NextResponse } from "next/server";
import { query, requireFinance, getDateRange } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const url = new URL(request.url);
    const period = url.searchParams.get("period") || "all";
    const search = url.searchParams.get("search") || "";
    const searchPattern = search ? `%${search}%` : "";

    const { start, end } = getDateRange(period);

    // Calculate previous period dates for trend comparisons
    let prevStart = "";
    let prevEnd = "";
    const hasTrend = period !== "all";

    if (hasTrend) {
      const startDate = new Date(start);
      const endDate = new Date(end);
      const diff = endDate.getTime() - startDate.getTime() + 1;
      prevStart = new Date(startDate.getTime() - diff).toISOString();
      prevEnd = new Date(endDate.getTime() - diff).toISOString();
    }

    // Build conditional search SQL fragments
    const salesSearchFilter = searchPattern ? `AND (s.invoice_number ILIKE $3 OR s.payment_method ILIKE $3)` : "";
    const expensesSearchFilter = searchPattern ? `AND (e.expense_number ILIKE $3 OR e.description ILIKE $3 OR ec.name ILIKE $3)` : "";
    const returnsSearchFilter = searchPattern ? `AND (sr.return_number ILIKE $3 OR s.invoice_number ILIKE $3)` : "";

    // Queries parameters
    const currentParamsSummary = searchPattern ? [start, end, searchPattern] : [start, end];
    const prevParamsSummary = searchPattern ? [prevStart, prevEnd, searchPattern] : [prevStart, prevEnd];

    // SQL for summary counts
    const summaryQuery = (isCurrent: boolean) => `
      WITH sales_summary AS (
        SELECT
          COALESCE(SUM(s.total_amount), 0)::bigint AS revenue,
          COALESCE(SUM(s.total_hpp), 0)::bigint AS hpp
        FROM sales s
        WHERE s.sale_date BETWEEN $1 AND $2
          AND s.status = 'COMPLETED'
          ${salesSearchFilter}
      ),
      returns_summary AS (
        SELECT
          COALESCE(SUM(sr.total_amount), 0)::bigint AS returns,
          COALESCE(SUM(sri.quantity * sri.hpp_per_unit), 0)::bigint AS cogs_reversal
        FROM sales_returns sr
        JOIN sales s ON s.id = sr.sale_id
        LEFT JOIN sales_return_items sri ON sri.sales_return_id = sr.id
        WHERE sr.return_date BETWEEN $1 AND $2
          AND sr.status = 'COMPLETED'
          ${returnsSearchFilter}
      ),
      expense_summary AS (
        SELECT COALESCE(SUM(e.amount), 0)::bigint AS expenses
        FROM expenses e
        LEFT JOIN expense_codes ec ON ec.id = e.expense_code_id
        WHERE e.expense_date BETWEEN $1::date AND $2::date
          ${expensesSearchFilter}
      )
      SELECT
        sales_summary.revenue,
        returns_summary.returns,
        (sales_summary.revenue - returns_summary.returns)::bigint AS net_revenue,
        (sales_summary.revenue - returns_summary.returns - sales_summary.hpp + returns_summary.cogs_reversal)::bigint AS gross_profit,
        expense_summary.expenses,
        (sales_summary.revenue - returns_summary.returns - sales_summary.hpp + returns_summary.cogs_reversal - expense_summary.expenses)::bigint AS net_profit
      FROM sales_summary, returns_summary, expense_summary
    `;

    // Fetch summaries in parallel
    const summaryPromises: Promise<any[]>[] = [
      query(summaryQuery(true), currentParamsSummary)
    ];

    if (hasTrend) {
      summaryPromises.push(query(summaryQuery(false), prevParamsSummary));
    }

    const summaries = await Promise.all(summaryPromises);
    const currentSummary = summaries[0][0];
    const prevSummary = hasTrend ? summaries[1][0] : null;

    // Calculate Trend Percentages
    const calcTrend = (curr: number, prev: number) => {
      if (!hasTrend || !prev || prev === 0) return 0;
      return parseFloat((((curr - prev) / prev) * 100).toFixed(1));
    };

    const trends = {
      net_revenue: prevSummary ? calcTrend(Number(currentSummary.net_revenue), Number(prevSummary.net_revenue)) : 0,
      gross_profit: prevSummary ? calcTrend(Number(currentSummary.gross_profit), Number(prevSummary.gross_profit)) : 0,
      expenses: prevSummary ? calcTrend(Number(currentSummary.expenses), Number(prevSummary.expenses)) : 0,
      net_profit: prevSummary ? calcTrend(Number(currentSummary.net_profit), Number(prevSummary.net_profit)) : 0,
    };

    // SQL for Payment Methods
    const paymentParams = searchPattern ? [start, end, searchPattern] : [start, end];
    const paymentRows = await query<any>(
      `SELECT s.payment_method AS name, COALESCE(SUM(sp.amount), 0)::bigint AS amount
       FROM sale_payments sp
       JOIN sales s ON s.id = sp.sale_id
       WHERE s.sale_date BETWEEN $1 AND $2
         AND s.status = 'COMPLETED'
         ${salesSearchFilter}
       GROUP BY s.payment_method
       ORDER BY amount DESC`,
      paymentParams
    );

    // SQL for Trend charts
    const trendParams = searchPattern ? [start, end, searchPattern] : [start, end];
    const trendRows = await query<any>(
      `SELECT
         to_char(d.day::date, 'Dy') AS day,
         d.day::date AS raw_date,
         COALESCE(SUM(s.total_amount), 0)::bigint AS revenue,
         COALESCE(SUM(s.total_amount - s.total_hpp), 0)::bigint AS profit
       FROM generate_series(
         (COALESCE((SELECT MAX(sale_date)::date FROM sales WHERE status = 'COMPLETED' AND sale_date BETWEEN $1 AND $2 ${salesSearchFilter.replace(/s\./g, "sales.")}), CURRENT_DATE) - INTERVAL '6 days')::date,
         COALESCE((SELECT MAX(sale_date)::date FROM sales WHERE status = 'COMPLETED' AND sale_date BETWEEN $1 AND $2 ${salesSearchFilter.replace(/s\./g, "sales.")}), CURRENT_DATE),
         INTERVAL '1 day'
       ) d(day)
       LEFT JOIN sales s ON s.sale_date::date = d.day::date AND s.status = 'COMPLETED' ${salesSearchFilter}
       GROUP BY d.day
       ORDER BY d.day ASC`,
      trendParams
    );

    // SQL for Transactions (filter by search and date range)
    const transactionParams = searchPattern ? [start, end, searchPattern] : [start, end];
    const transactionRows = await query<any>(
      `SELECT s.invoice_number AS invoice, s.sale_date AS time, s.payment_method AS method,
              s.total_amount::bigint AS total, s.status
       FROM sales s
       WHERE s.sale_date BETWEEN $1 AND $2
         ${salesSearchFilter}
       ORDER BY s.sale_date DESC
       LIMIT 10`,
      transactionParams
    );

    // SQL for Expense approvals
    const approvalRows = await query<any>(
      `SELECT e.expense_number AS title,
              COALESCE(ec.name, 'Biaya') || ' - ' || e.amount::text AS meta,
              'Review' AS status
       FROM expenses e
       LEFT JOIN expense_codes ec ON ec.id = e.expense_code_id
       ORDER BY e.created_at DESC
       LIMIT 5`,
      []
    );

    return NextResponse.json({
      summary: currentSummary,
      trends,
      payments: paymentRows,
      trend: trendRows.map((r: any) => ({
        day: r.day,
        raw_date: r.raw_date,
        revenue: Number(r.revenue),
        profit: Number(r.profit)
      })),
      transactions: transactionRows,
      approvals: approvalRows,
    });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
