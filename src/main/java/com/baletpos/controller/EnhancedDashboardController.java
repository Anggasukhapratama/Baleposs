package com.baletpos.controller;

import com.baletpos.dao.ReportDAO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class EnhancedDashboardController {
    private static final Logger logger = LoggerFactory.getLogger(EnhancedDashboardController.class);

    // KPI Labels
    @FXML private Label todaySalesTotalLabel;
    @FXML private Label todaySalesCountLabel;
    @FXML private Label todaySalesTrendLabel;
    @FXML private Label todayTransactionCountLabel;
    @FXML private Label avgTransactionValueLabel;
    @FXML private Label monthRevenueLabel;
    @FXML private ProgressBar monthRevenueProgress;
    @FXML private Label monthRevenueTargetLabel;
    @FXML private Label monthProfitLabel;
    @FXML private Label lowStockCountLabel;
    @FXML private Button lowStockButton;
    @FXML private Label returnAmountLabel;
    @FXML private Label returnCountLabel;

    // Charts
    @FXML private LineChart<String, Number> salesTrendChart;
    @FXML private CategoryAxis salesTrendXAxis;
    @FXML private NumberAxis salesTrendYAxis;
    @FXML private PieChart categoryPieChart;

    // Operational Information Tables
    @FXML private TableView<TopProduct> topProductsTable;
    @FXML private TableColumn<TopProduct, String> productNameCol;
    @FXML private TableColumn<TopProduct, Integer> qtySoldCol;
    @FXML private TableColumn<TopProduct, String> revenueCol;

    @FXML private TableView<CashierPerformance> cashierTable;
    @FXML private TableColumn<CashierPerformance, String> cashierNameCol;
    @FXML private TableColumn<CashierPerformance, Integer> transactionCountCol;
    @FXML private TableColumn<CashierPerformance, String> cashierRevenueCol;

    @FXML private TableView<PaymentMethod> paymentMethodTable;
    @FXML private TableColumn<PaymentMethod, String> paymentMethodCol;
    @FXML private TableColumn<PaymentMethod, String> paymentPercentageCol;

    // Alerts Section
    @FXML private VBox alertsContainer;

    private final ReportDAO reportDAO = new ReportDAO();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    public EnhancedDashboardController() {
        currencyFormat.setMaximumFractionDigits(0);
    }

    @FXML
    public void initialize() {
        setupKpiCards();
        setupCharts();
        setupTables();
        loadDashboardData();
    }

    private void setupKpiCards() {
        // Set up trend indicators and other card elements
        todaySalesTrendLabel.setStyle("-fx-text-fill: #1E40AF;"); // Green for positive trend
    }

    private void setupCharts() {
        // Configure sales trend chart
        salesTrendChart.setCreateSymbols(false);
        salesTrendChart.setLegendVisible(true);
        
        // Configure category pie chart
        categoryPieChart.setLabelsVisible(true);
    }

    private void setupTables() {
        // Top Products Table
        productNameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        qtySoldCol.setCellValueFactory(cellData -> cellData.getValue().qtySoldProperty().asObject());
        revenueCol.setCellValueFactory(cellData -> cellData.getValue().revenueProperty());

        // Cashier Performance Table
        cashierNameCol.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        transactionCountCol.setCellValueFactory(cellData -> cellData.getValue().transactionCountProperty().asObject());
        cashierRevenueCol.setCellValueFactory(cellData -> cellData.getValue().revenueProperty());

        // Payment Methods Table
        paymentMethodCol.setCellValueFactory(cellData -> cellData.getValue().methodProperty());
        paymentPercentageCol.setCellValueFactory(cellData -> cellData.getValue().percentageProperty());
    }

    private void loadDashboardData() {
        try {
            // Load KPI data
            loadKpiData();
            
            // Load chart data
            loadSalesTrendData();
            loadCategoryChartData();
            
            // Load operational data
            loadTopProductsData();
            loadCashierPerformanceData();
            loadPaymentMethodsData();
            
            // Load alerts
            loadAlertsData();
            
        } catch (Exception e) {
            logger.error("Error loading dashboard data", e);
            showAlert("Error", "Gagal memuat data dashboard: " + e.getMessage());
        }
    }

    private void loadKpiData() {
        // Today's sales summary
        ReportDAO.ReportRow todaySummary = reportDAO.getDashboardSummary(LocalDate.now());
        
        int todayCount = todaySummary.getInt("today_sales_count");
        BigDecimal todayTotal = todaySummary.getBigDecimal("today_sales_total");
        
        todaySalesCountLabel.setText(todayCount + " transaksi");
        todaySalesTotalLabel.setText(currencyFormat.format(todayTotal != null ? todayTotal : BigDecimal.ZERO));
        
        // Calculate average transaction value
        if (todayCount > 0) {
            BigDecimal avgValue = todayTotal.divide(BigDecimal.valueOf(todayCount), BigDecimal.ROUND_HALF_UP);
            avgTransactionValueLabel.setText(currencyFormat.format(avgValue));
        } else {
            avgTransactionValueLabel.setText("Rp 0");
        }
        
        // Compare with yesterday
        ReportDAO.ReportRow yesterdaySummary = reportDAO.getDashboardSummary(LocalDate.now().minusDays(1));
        BigDecimal yesterdayTotal = yesterdaySummary.getBigDecimal("today_sales_total");
        
        if (yesterdayTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = todayTotal.subtract(yesterdayTotal);
            BigDecimal percentage = diff.multiply(BigDecimal.valueOf(100))
                    .divide(yesterdayTotal, 2, BigDecimal.ROUND_HALF_UP);
            
            if (diff.compareTo(BigDecimal.ZERO) >= 0) {
                todaySalesTrendLabel.setText("Naik " + percentage.abs() + "%");
                todaySalesTrendLabel.setStyle("-fx-text-fill: #1E40AF;");
            } else {
                todaySalesTrendLabel.setText("Turun " + percentage.abs() + "%");
                todaySalesTrendLabel.setStyle("-fx-text-fill: #1F3A8A;");
            }
        } else {
            todaySalesTrendLabel.setText("N/A");
            todaySalesTrendLabel.setStyle("-fx-text-fill: #6b7280;");
        }
        
        // Month revenue
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        ReportDAO.ReportRow monthSummary = reportDAO.getProfitLossReport(monthStart, monthEnd);
        
        BigDecimal monthRevenue = monthSummary.getBigDecimal("net_revenue");
        BigDecimal monthProfit = monthSummary.getBigDecimal("net_profit");
        
        monthRevenueLabel.setText(currencyFormat.format(monthRevenue));
        
        // Revenue progress (assuming target is 500 million for example)
        BigDecimal target = new BigDecimal("500000000"); // 500 million
        double progress = monthRevenue.divide(target, 4, BigDecimal.ROUND_HALF_UP).doubleValue();
        monthRevenueProgress.setProgress(Math.min(progress, 1.0));
        monthRevenueTargetLabel.setText("Target: " + currencyFormat.format(target));
        
        // Month profit (green for profit, red for loss)
        if (monthProfit.compareTo(BigDecimal.ZERO) >= 0) {
            monthProfitLabel.setText(currencyFormat.format(monthProfit));
            monthProfitLabel.setStyle("-fx-text-fill: #1E40AF; -fx-font-weight: bold;");
        } else {
            monthProfitLabel.setText(currencyFormat.format(monthProfit.abs()));
            monthProfitLabel.setStyle("-fx-text-fill: #1F3A8A; -fx-font-weight: bold;");
        }
        
        // Low stock products
        int lowStockCount = todaySummary.getInt("low_stock_count");
        lowStockCountLabel.setText(lowStockCount + " produk");
        
        // Return data (placeholder - need to implement return data retrieval)
        returnAmountLabel.setText("Rp 0");
        returnCountLabel.setText("0 retur");
    }

    private void loadSalesTrendData() {
        try {
            XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();
            revenueSeries.setName("Omzet");
            
            XYChart.Series<String, Number> profitSeries = new XYChart.Series<>();
            profitSeries.setName("Laba Bersih");

            LocalDate today = LocalDate.now();
            DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("dd/MM", new Locale("id", "ID"));

            // Get data for last 7 days
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                
                ReportDAO.ReportRow dailySummary = reportDAO.getDashboardSummary(date);
                ReportDAO.ReportRow dailyProfit = reportDAO.getProfitLossReport(date, date);

                BigDecimal revenue = dailySummary.getBigDecimal("todaySalesTotal");
                BigDecimal profit = dailyProfit.getBigDecimal("net_profit");

                String dayLabel = date.format(dayFmt);
                revenueSeries.getData().add(new XYChart.Data<>(dayLabel, 
                    (revenue != null ? revenue.doubleValue() : 0) / 1_000_000));
                profitSeries.getData().add(new XYChart.Data<>(dayLabel, 
                    (profit != null ? profit.doubleValue() : 0) / 1_000_000));
            }

            salesTrendChart.getData().clear();
            salesTrendChart.getData().add(revenueSeries);
            salesTrendChart.getData().add(profitSeries);
            
        } catch (Exception e) {
            logger.error("Error loading sales trend data", e);
        }
    }

    private void loadCategoryChartData() {
        try {
            LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
            LocalDate today = LocalDate.now();

            // Get sales by each product type
            var laptopNewSales = reportDAO.getSalesReportByProductType(startOfMonth, today, "LAPTOP_NEW");
            var laptopSecondSales = reportDAO.getSalesReportByProductType(startOfMonth, today, "LAPTOP_SECOND");
            var peripheralSales = reportDAO.getSalesReportByProductType(startOfMonth, today, "PERIPHERAL");
            var serviceSales = reportDAO.getSalesReportByProductType(startOfMonth, today, "SERVICE");

            var pieData = FXCollections.<PieChart.Data>observableArrayList();

            BigDecimal laptopNewTotal = calculateTotal(laptopNewSales);
            BigDecimal laptopSecondTotal = calculateTotal(laptopSecondSales);
            BigDecimal laptopTotal = laptopNewTotal.add(laptopSecondTotal);
            BigDecimal peripheralTotal = calculateTotal(peripheralSales);
            BigDecimal serviceTotal = calculateTotal(serviceSales);

            if (laptopTotal.compareTo(BigDecimal.ZERO) > 0) {
                pieData.add(new PieChart.Data("Laptop", laptopTotal.doubleValue()));
            }
            if (peripheralTotal.compareTo(BigDecimal.ZERO) > 0) {
                pieData.add(new PieChart.Data("Peripheral", peripheralTotal.doubleValue()));
            }
            if (serviceTotal.compareTo(BigDecimal.ZERO) > 0) {
                pieData.add(new PieChart.Data("Service", serviceTotal.doubleValue()));
            }

            if (pieData.isEmpty()) {
                pieData.add(new PieChart.Data("Belum ada data", 1));
            }

            categoryPieChart.setData(pieData);
            categoryPieChart.setLabelsVisible(true);

        } catch (Exception e) {
            logger.error("Error loading category chart data", e);
        }
    }

    private BigDecimal calculateTotal(List<ReportDAO.ReportRow> rows) {
        BigDecimal total = BigDecimal.ZERO;
        for (ReportDAO.ReportRow row : rows) {
            BigDecimal revenue = row.getBigDecimal("revenue");
            if (revenue != null) {
                total = total.add(revenue);
            }
        }
        return total;
    }

    private void loadTopProductsData() {
        // This would need to be implemented based on sales data
        // For now, using mock data
        topProductsTable.getItems().clear();
        
        // In a real implementation, you would query the database for top selling products
        // Example query would aggregate quantities sold by product in the current month
    }

    private void loadCashierPerformanceData() {
        // This would need to be implemented based on sales data by cashier
        cashierTable.getItems().clear();
        
        // In a real implementation, you would query the database for sales grouped by cashier
    }

    private void loadPaymentMethodsData() {
        // This would need to be implemented based on payment method data
        paymentMethodTable.getItems().clear();
        
        // In a real implementation, you would query the database for payment methods used
    }

    private void loadAlertsData() {
        // Clear previous alerts
        alertsContainer.getChildren().clear();
        
        // Add alerts based on conditions
        addAlert("Stok produk menipis", "Beberapa produk mendekati batas minimum stok", "warning");
        addAlert("Produk tidak laku > 30 hari", "Ada produk yang tidak terjual lebih dari 30 hari", "warning");
    }

    private void addAlert(String title, String message, String type) {
        VBox alertBox = new VBox();
        alertBox.getStyleClass().add("alert-box");
        
        if ("error".equals(type)) {
            alertBox.setStyle("-fx-background-color: #DBEAFE; -fx-border-color: #BFDBFE; -fx-border-width: 1; -fx-padding: 10; -fx-spacing: 5;");
        } else if ("warning".equals(type)) {
            alertBox.setStyle("-fx-background-color: #DBEAFE; -fx-border-color: #93C5FD; -fx-border-width: 1; -fx-padding: 10; -fx-spacing: 5;");
        }
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #000;");
        
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-text-fill: #000;");
        
        alertBox.getChildren().addAll(titleLabel, messageLabel);
        alertsContainer.getChildren().add(alertBox);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Data classes for tables
    public static class TopProduct {
        private final String name;
        private final int qtySold;
        private final String revenue;

        public TopProduct(String name, int qtySold, String revenue) {
            this.name = name;
            this.qtySold = qtySold;
            this.revenue = revenue;
        }

        public String getName() { return name; }
        public int getQtySold() { return qtySold; }
        public String getRevenue() { return revenue; }
        
        public javafx.beans.property.StringProperty nameProperty() {
            return new javafx.beans.property.SimpleStringProperty(name);
        }
        
        public javafx.beans.property.IntegerProperty qtySoldProperty() {
            return new javafx.beans.property.SimpleIntegerProperty(qtySold);
        }
        
        public javafx.beans.property.StringProperty revenueProperty() {
            return new javafx.beans.property.SimpleStringProperty(revenue);
        }
    }

    public static class CashierPerformance {
        private final String name;
        private final int transactionCount;
        private final String revenue;

        public CashierPerformance(String name, int transactionCount, String revenue) {
            this.name = name;
            this.transactionCount = transactionCount;
            this.revenue = revenue;
        }

        public String getName() { return name; }
        public int getTransactionCount() { return transactionCount; }
        public String getRevenue() { return revenue; }
        
        public javafx.beans.property.StringProperty nameProperty() {
            return new javafx.beans.property.SimpleStringProperty(name);
        }
        
        public javafx.beans.property.IntegerProperty transactionCountProperty() {
            return new javafx.beans.property.SimpleIntegerProperty(transactionCount);
        }
        
        public javafx.beans.property.StringProperty revenueProperty() {
            return new javafx.beans.property.SimpleStringProperty(revenue);
        }
    }

    public static class PaymentMethod {
        private final String method;
        private final String percentage;

        public PaymentMethod(String method, String percentage) {
            this.method = method;
            this.percentage = percentage;
        }

        public String getMethod() { return method; }
        public String getPercentage() { return percentage; }
        
        public javafx.beans.property.StringProperty methodProperty() {
            return new javafx.beans.property.SimpleStringProperty(method);
        }
        
        public javafx.beans.property.StringProperty percentageProperty() {
            return new javafx.beans.property.SimpleStringProperty(percentage);
        }
    }
}


