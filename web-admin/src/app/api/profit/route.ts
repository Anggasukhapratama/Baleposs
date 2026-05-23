import { NextResponse } from "next/server";
import { query, requireFinance, getDateRange } from "@/lib/db";

export async function GET(request: Request) {
  if (!requireFinance(request)) {
    return NextResponse.json({ error: "Sesi tidak valid. Login ulang sebagai Admin Keuangan." }, { status: 401 });
  }

  try {
    const url = new URL(request.url);
    const period = url.searchParams.get("period") || "all";
    const { start, end } = getDateRange(period);

    const rows = await query<any>(
      `WITH sales_summary AS (
         SELECT COALESCE(SUM(total_amount), 0)::bigint AS revenue,
                COALESCE(SUM(total_hpp), 0)::bigint AS hpp
          FROM sales
          WHERE sale_date BETWEEN $1 AND $2
            AND status = 'COMPLETED'
        ),
        returns_summary AS (
          SELECT COALESCE(SUM(sr.total_amount), 0)::bigint AS returns,
                 COALESCE(SUM(sri.quantity * sri.hpp_per_unit), 0)::bigint AS cogs_reversal
          FROM sales_returns sr
          LEFT JOIN sales_return_items sri ON sri.sales_return_id = sr.id
          WHERE sr.return_date BETWEEN $1 AND $2
            AND sr.status = 'COMPLETED'
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
    const cogs_reversal = Number(row.cogs_reversal || 0);
    const hpp = Number(row.hpp || 0);
    const net_cogs = hpp - cogs_reversal;
    const expenses = Number(row.expenses || 0);
    const gross_profit = (revenue - returns) - net_cogs;

    return NextResponse.json({
      rows: [
        ["Penjualan Kotor", revenue],
        ["Retur Penjualan", -returns],
        ["Pendapatan Bersih", revenue - returns, "total"],
        ["HPP Penjualan", -hpp],
        ["Reversal HPP Retur", cogs_reversal],
        ["HPP Bersih (Net COGS)", -net_cogs, "total"],
        ["Laba Kotor (Gross Profit)", gross_profit, "total"],
        ["Biaya Operasional", -expenses],
        ["Laba Bersih (Net Profit)", gross_profit - expenses, "total"],
      ],
    });
  } catch (error: any) {
    console.error(error);
    return NextResponse.json({ error: "Database error", message: error.message }, { status: 500 });
  }
}
