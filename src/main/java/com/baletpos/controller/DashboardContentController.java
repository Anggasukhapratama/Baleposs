package com.baletpos.controller;

import com.baletpos.config.DatabaseConfig;
import com.baletpos.dao.ReportDAO;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller untuk Dashboard Content - Style BaletPOS
 */
public class DashboardContentController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardContentController.class);

    private DashboardController parentController;

    // KPI Labels
    @FXML
    private Label todaySalesLabel;
    @FXML
    private Label salesTrendLabel;
    @FXML
    private Label todayTransactionLabel;
    @FXML
    private Label transactionTrendLabel;
    @FXML
    private Label monthExpenseLabel;
    @FXML
    private Label expenseTrendLabel;

    // Chart
    @FXML
    private LineChart<String, Number> salesTrendChart;
    @FXML
    private CategoryAxis chartXAxis;
    @FXML
    private NumberAxis chartYAxis;
    @FXML
    private Label chartSummaryLabel;
    @FXML
    private Label growthBadge;

    // Top Products
    @FXML
    private VBox topProductsContainer;
    @FXML
    private Label topProduct1Name;
    @FXML
    private Label topProduct1Qty;
    @FXML
    private Label topProduct2Name;
    @FXML
    private Label topProduct2Qty;
    @FXML
    private Label topProduct3Name;
    @FXML
    private Label topProduct3Qty;

    // Low Stock
    @FXML
    private VBox lowStockContainer;
    @FXML
    private Label lowStock1Name;
    @FXML
    private Label lowStock1Qty;
    @FXML
    private Label lowStock2Name;
    @FXML
    private Label lowStock2Qty;
    @FXML
    private Label lowStock3Name;
    @FXML
    private Label lowStock3Qty;
    @FXML
    private Button restock1Btn;
    @FXML
    private Button restock2Btn;
    @FXML
    private Button restock3Btn;

    private final ReportDAO reportDAO = new ReportDAO();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    public DashboardContentController() {
        currencyFormat.setMaximumFractionDigits(0);
    }

    @FXML
    public void initialize() {
        loadDashboardData();
    }

    private void loadDashboardData() {
        try {
            loadKpiData();
            loadSalesTrendChart();
            loadTopProducts();
            loadLowStockProducts();
        } catch (Exception e) {
            logger.error("Error loading dashboard data", e);
        }
    }

    private void loadKpiData() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Today's sales
        ReportDAO.ReportRow todaySummary = reportDAO.getDashboardSummary(today);
        ReportDAO.ReportRow yesterdaySummary = reportDAO.getDashboardSummary(yesterday);

        BigDecimal todaySales = todaySummary.getBigDecimal("today_sales_total");
        int todayCount = todaySummary.getInt("today_sales_count");
        BigDecimal yesterdaySales = yesterdaySummary.getBigDecimal("today_sales_total");
        int yesterdayCount = yesterdaySummary.getInt("today_sales_count");

        if (todaySales == null)
            todaySales = BigDecimal.ZERO;
        if (yesterdaySales == null)
            yesterdaySales = BigDecimal.ZERO;

        // Set today's sales label
        if (todaySalesLabel != null) {
            todaySalesLabel.setText(currencyFormat.format(todaySales));
        }

        // Set transaction count
        if (todayTransactionLabel != null) {
            todayTransactionLabel.setText(String.valueOf(todayCount));
        }

        // Calculate sales trend
        if (salesTrendLabel != null) {
            String trend = calculateTrend(todaySales, yesterdaySales);
            salesTrendLabel.setText(trend + " vs Kemarin");
            if (trend.startsWith("+")) {
                salesTrendLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #1E40AF;");
            } else if (trend.startsWith("-")) {
                salesTrendLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #DC2626;");
            }
        }

        // Calculate transaction trend
        if (transactionTrendLabel != null) {
            String trend = calculateTrendInt(todayCount, yesterdayCount);
            transactionTrendLabel.setText(trend + " vs Kemarin");
            if (trend.startsWith("+")) {
                transactionTrendLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #1E40AF;");
            } else if (trend.startsWith("-")) {
                transactionTrendLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #DC2626;");
            }
        }

        // Month expense
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate prevMonthStart = monthStart.minusMonths(1);
        LocalDate prevMonthEnd = prevMonthStart.withDayOfMonth(prevMonthStart.lengthOfMonth());

        ReportDAO.ReportRow thisMonthReport = reportDAO.getProfitLossReport(monthStart, monthEnd);
        ReportDAO.ReportRow prevMonthReport = reportDAO.getProfitLossReport(prevMonthStart, prevMonthEnd);

        BigDecimal thisMonthExpense = thisMonthReport.getBigDecimal("total_expense");
        BigDecimal prevMonthExpense = prevMonthReport.getBigDecimal("total_expense");

        if (thisMonthExpense == null)
            thisMonthExpense = BigDecimal.ZERO;
        if (prevMonthExpense == null)
            prevMonthExpense = BigDecimal.ZERO;

        if (monthExpenseLabel != null) {
            monthExpenseLabel.setText(currencyFormat.format(thisMonthExpense));
        }

        if (expenseTrendLabel != null) {
            String trend = calculateTrend(thisMonthExpense, prevMonthExpense);
            expenseTrendLabel.setText(trend + " vs Bulan Lalu");
            // For expense, negative is good
            if (trend.startsWith("-")) {
                expenseTrendLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #1E40AF;");
            } else if (trend.startsWith("+")) {
                expenseTrendLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #DC2626;");
            }
        }
    }

    private String calculateTrend(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current != null && current.compareTo(BigDecimal.ZERO) > 0) {
                return "+100%";
            }
            return "0%";
        }

        BigDecimal diff = current.subtract(previous);
        BigDecimal pct = diff.multiply(BigDecimal.valueOf(100)).divide(previous, 0, RoundingMode.HALF_UP);

        if (pct.compareTo(BigDecimal.ZERO) >= 0) {
            return "+" + pct.intValue() + "%";
        } else {
            return pct.intValue() + "%";
        }
    }

    private String calculateTrendInt(int current, int previous) {
        if (previous == 0) {
            if (current > 0) {
                return "+100%";
            }
            return "0%";
        }

        int diff = current - previous;
        int pct = (diff * 100) / previous;

        if (pct >= 0) {
            return "+" + pct + "%";
        } else {
            return pct + "%";
        }
    }

    private void loadSalesTrendChart() {
        if (salesTrendChart == null)
            return;

        salesTrendChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Penjualan");

        LocalDate today = LocalDate.now();
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM", new Locale("id", "ID"));

        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal firstMonthSales = BigDecimal.ZERO;
        BigDecimal lastMonthSales = BigDecimal.ZERO;

        // Get last 6 months data
        for (int i = 5; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

            ReportDAO.ReportRow monthReport = reportDAO.getProfitLossReport(monthStart, monthEnd);
            BigDecimal revenue = monthReport.getBigDecimal("gross_revenue");
            if (revenue == null)
                revenue = BigDecimal.ZERO;

            totalSales = totalSales.add(revenue);

            if (i == 5)
                firstMonthSales = revenue;
            if (i == 0)
                lastMonthSales = revenue;

            String monthLabel = monthStart.format(monthFmt);
            series.getData().add(new XYChart.Data<>(monthLabel, revenue.doubleValue() / 1_000_000));
        }

        salesTrendChart.getData().add(series);

        // Update summary
        if (chartSummaryLabel != null) {
            LocalDate start = today.minusMonths(5).withDayOfMonth(1);
            LocalDate end = today;
            String startMonth = start.format(monthFmt);
            String endMonth = end.format(monthFmt);
            chartSummaryLabel.setText(
                    "Total: " + currencyFormat.format(totalSales) + " (" + startMonth + " - " + endMonth + ")");
        }

        // Update growth badge
        if (growthBadge != null) {
            String growth = calculateTrend(lastMonthSales, firstMonthSales);
            growthBadge.setText(growth + " Pertumbuhan");
            if (growth.startsWith("+")) {
                growthBadge.setStyle(
                        "-fx-background-color: #EFF6FF; -fx-text-fill: #1E40AF; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 6 12; -fx-background-radius: 20;");
            } else {
                growthBadge.setStyle(
                        "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF; -fx-font-size: 11px; -fx-font-weight: 700; -fx-padding: 6 12; -fx-background-radius: 20;");
            }
        }
    }

    private void loadTopProducts() {
        try {
            LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
            LocalDate monthEnd = LocalDate.now();

            // Get all sales data
            var allSales = reportDAO.getSalesReportByProductType(monthStart, monthEnd, "LAPTOP_NEW");
            allSales.addAll(reportDAO.getSalesReportByProductType(monthStart, monthEnd, "LAPTOP_SECOND"));
            allSales.addAll(reportDAO.getSalesReportByProductType(monthStart, monthEnd, "PERIPHERAL"));
            allSales.addAll(reportDAO.getSalesReportByProductType(monthStart, monthEnd, "SERVICE"));

            // Sort by quantity sold
            allSales.sort((a, b) -> Integer.compare(b.getInt("qty_sold"), a.getInt("qty_sold")));

            // Top 3
            if (allSales.size() > 0) {
                ReportDAO.ReportRow p1 = allSales.get(0);
                if (topProduct1Name != null)
                    topProduct1Name.setText(truncate(p1.getString("product_name"), 25));
                if (topProduct1Qty != null)
                    topProduct1Qty.setText(p1.getInt("qty_sold") + " Terjual");
            } else {
                if (topProduct1Name != null)
                    topProduct1Name.setText("Belum ada data");
                if (topProduct1Qty != null)
                    topProduct1Qty.setText("-");
            }

            if (allSales.size() > 1) {
                ReportDAO.ReportRow p2 = allSales.get(1);
                if (topProduct2Name != null)
                    topProduct2Name.setText(truncate(p2.getString("product_name"), 25));
                if (topProduct2Qty != null)
                    topProduct2Qty.setText(p2.getInt("qty_sold") + " Terjual");
            } else {
                if (topProduct2Name != null)
                    topProduct2Name.setText("Belum ada data");
                if (topProduct2Qty != null)
                    topProduct2Qty.setText("-");
            }

            if (allSales.size() > 2) {
                ReportDAO.ReportRow p3 = allSales.get(2);
                if (topProduct3Name != null)
                    topProduct3Name.setText(truncate(p3.getString("product_name"), 25));
                if (topProduct3Qty != null)
                    topProduct3Qty.setText(p3.getInt("qty_sold") + " Terjual");
            } else {
                if (topProduct3Name != null)
                    topProduct3Name.setText("Belum ada data");
                if (topProduct3Qty != null)
                    topProduct3Qty.setText("-");
            }

        } catch (Exception e) {
            logger.error("Error loading top products", e);
        }
    }

    private void loadLowStockProducts() {
        try {
            String sql = "SELECT name, stock FROM products WHERE is_active = 1 AND stock <= 5 ORDER BY stock ASC LIMIT 3";

            List<String[]> lowStockList = new ArrayList<>();

            try (Connection conn = DatabaseConfig.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    lowStockList.add(new String[] { rs.getString("name"), String.valueOf(rs.getInt("stock")) });
                }
            }

            // Update labels
            if (lowStockList.size() > 0) {
                String[] item1 = lowStockList.get(0);
                if (lowStock1Name != null)
                    lowStock1Name.setText(truncate(item1[0], 22));
                if (lowStock1Qty != null)
                    lowStock1Qty.setText("Sisa " + item1[1] + " unit");
            } else {
                if (lowStock1Name != null)
                    lowStock1Name.setText("Stok aman");
                if (lowStock1Qty != null)
                    lowStock1Qty.setText("-");
            }

            if (lowStockList.size() > 1) {
                String[] item2 = lowStockList.get(1);
                if (lowStock2Name != null)
                    lowStock2Name.setText(truncate(item2[0], 22));
                if (lowStock2Qty != null)
                    lowStock2Qty.setText("Sisa " + item2[1] + " unit");
            } else {
                if (lowStock2Name != null)
                    lowStock2Name.setText("Stok aman");
                if (lowStock2Qty != null)
                    lowStock2Qty.setText("-");
            }

            if (lowStockList.size() > 2) {
                String[] item3 = lowStockList.get(2);
                if (lowStock3Name != null)
                    lowStock3Name.setText(truncate(item3[0], 22));
                if (lowStock3Qty != null)
                    lowStock3Qty.setText("Sisa " + item3[1] + " unit");
            } else {
                if (lowStock3Name != null)
                    lowStock3Name.setText("Stok aman");
                if (lowStock3Qty != null)
                    lowStock3Qty.setText("-");
            }

        } catch (Exception e) {
            logger.error("Error loading low stock products", e);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Sets the parent dashboard controller for navigation purposes
     */
    public void setParentController(DashboardController parent) {
        this.parentController = parent;
    }
}


