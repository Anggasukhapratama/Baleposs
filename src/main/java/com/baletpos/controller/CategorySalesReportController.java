package com.baletpos.controller;

import com.baletpos.dao.ReportDAO;
import com.baletpos.dao.ReportDAO.ReportRow;
import com.baletpos.util.PdfExportUtil;
import com.baletpos.util.ExcelExportUtil;
import com.baletpos.util.PaginationControl;
import java.util.Arrays;
import javafx.stage.Stage;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controller untuk Laporan Penjualan per Kategori
 */
public class CategorySalesReportController {

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private ComboBox<String> categoryCombo;

    @FXML
    private TextField searchField;

    @FXML
    private TableView<ReportRow> reportTable;

    @FXML
    private TableColumn<ReportRow, String> skuCol;

    @FXML
    private TableColumn<ReportRow, String> nameCol;

    @FXML
    private TableColumn<ReportRow, String> qtyCol;

    @FXML
    private TableColumn<ReportRow, String> revenueCol;

    @FXML
    private TableColumn<ReportRow, String> cogsCol;

    @FXML
    private TableColumn<ReportRow, String> profitCol;

    @FXML
    private Label totalQtyLabel;

    @FXML
    private Label totalRevenueLabel;

    @FXML
    private Label totalCogsLabel;

    @FXML
    private Label totalProfitLabel;

    @FXML
    private VBox tableContainer;

    private final ReportDAO reportDAO = new ReportDAO();
    private final ObservableList<ReportRow> reportData = FXCollections.observableArrayList();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private final List<ReportRow> allData = new ArrayList<>();
    private List<ReportRow> filteredData = new ArrayList<>();
    private PaginationControl pagination;
    private String currentSearchQuery = "";

    @FXML
    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);

        // Setup category combo
        categoryCombo.setItems(FXCollections.observableArrayList("LAPTOP_NEW", "LAPTOP_SECOND", "SPAREPARTS",
                "PERIPHERAL", "SERVICE"));
        categoryCombo.setValue("LAPTOP_NEW");

        // Default to current month
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));

        setupTable();
        setupPagination();

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(newVal));
        }
    }

    private void setupTable() {
        skuCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getString("sku")));
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getString("product_name")));
        qtyCol.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getInt("qty_sold"))));
        revenueCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getLong("revenue"))));
        cogsCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getLong("cogs"))));
        profitCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getLong("gross_profit"))));

        reportTable.setItems(reportData);
    }

    @FXML
    private void handleGenerateReport() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();
        String category = categoryCombo.getValue();

        if (startDate == null || endDate == null) {
            showError("Pilih periode tanggal");
            return;
        }

        if (startDate.isAfter(endDate)) {
            showError("Tanggal awal tidak boleh setelah tanggal akhir");
            return;
        }

        reportData.clear();
        allData.clear();

        List<ReportRow> data;
        switch (category) {
            case "LAPTOP_NEW":
            case "LAPTOP_SECOND":
            case "SPAREPARTS":
                data = reportDAO.getSalesReportByProductType(startDate, endDate, category);
                break;
            case "PERIPHERAL":
                data = reportDAO.getSalesReportPeripheral(startDate, endDate);
                break;
            case "SERVICE":
                data = reportDAO.getSalesReportService(startDate, endDate);
                break;
            default:
                data = List.of();
        }

        allData.addAll(data);
        applyFilter(currentSearchQuery);
    }

    private void calculateTotals(List<ReportRow> rows) {
        int totalQty = rows.stream().mapToInt(r -> r.getInt("qty_sold")).sum();
        long totalRevenue = rows.stream().mapToLong(r -> r.getLong("revenue")).sum();
        long totalCogs = rows.stream().mapToLong(r -> r.getLong("cogs")).sum();
        long totalProfit = totalRevenue - totalCogs;

        totalQtyLabel.setText("Total Qty: " + totalQty);
        totalRevenueLabel.setText("Total Omzet: " + currencyFormat.format(totalRevenue));
        totalCogsLabel.setText("Total HPP: " + currencyFormat.format(totalCogs));
        totalProfitLabel.setText("Total Laba Kotor: " + currencyFormat.format(totalProfit));
    }

    private void setupPagination() {
        pagination = new PaginationControl();
        pagination.setOnPageChange(this::updatePage);
        if (tableContainer != null) {
            tableContainer.getChildren().add(pagination);
        }
    }

    private void applyFilter(String query) {
        currentSearchQuery = query != null ? query.trim().toLowerCase() : "";
        if (currentSearchQuery.isEmpty()) {
            filteredData = new ArrayList<>(allData);
        } else {
            filteredData = new ArrayList<>();
            for (ReportRow row : allData) {
                String sku = row.getString("sku").toLowerCase();
                String name = row.getString("product_name").toLowerCase();
                if (sku.contains(currentSearchQuery) || name.contains(currentSearchQuery)) {
                    filteredData.add(row);
                }
            }
        }
        if (pagination != null) {
            pagination.resetToFirstPage();
        }
        updatePage();
    }

    private void updatePage() {
        if (pagination == null) {
            reportData.setAll(filteredData);
            calculateTotals(filteredData);
            return;
        }

        int total = filteredData != null ? filteredData.size() : 0;
        pagination.update(total);
        reportData.clear();
        if (total == 0) {
            calculateTotals(List.of());
            return;
        }

        int offset = pagination.getOffset();
        int end = Math.min(offset + pagination.getPageSize(), total);
        reportData.addAll(filteredData.subList(offset, end));
        calculateTotals(filteredData);
    }

    @FXML
    private void handleToday() {
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now);
        endDatePicker.setValue(now);
        handleGenerateReport();
    }

    @FXML
    private void handleYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        startDatePicker.setValue(yesterday);
        endDatePicker.setValue(yesterday);
        handleGenerateReport();
    }

    @FXML
    private void handleThisWeek() {
        LocalDate now = LocalDate.now();
        // Assume week starts on Monday
        LocalDate startOfWeek = now.minusDays(now.getDayOfWeek().getValue() - 1);
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        startDatePicker.setValue(startOfWeek);
        endDatePicker.setValue(endOfWeek);
        handleGenerateReport();
    }

    @FXML
    private void handleThisMonth() {
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));
        handleGenerateReport();
    }

    @FXML
    private void handleExport() {
        List<ReportRow> exportData = getExportData();
        if (exportData.isEmpty()) {
            showError("Generate laporan terlebih dahulu sebelum export");
            return;
        }

        try {
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            String category = categoryCombo.getValue();

            File pdfFile = PdfExportUtil.exportCategorySalesReport(
                    exportData, category, startDate, endDate);

            // Show success and offer to open
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Export Berhasil");
            alert.setHeaderText("Laporan berhasil diexport!");
            alert.setContentText("File: " + pdfFile.getAbsolutePath() + "\n\nBuka file sekarang?");

            ButtonType openButton = new ButtonType("Buka File");
            ButtonType closeButton = new ButtonType("Tutup", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(openButton, closeButton);

            alert.showAndWait().ifPresent(response -> {
                if (response == openButton) {
                    try {
                        Desktop.getDesktop().open(pdfFile);
                    } catch (Exception e) {
                        showError("Tidak dapat membuka file: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            showError("Gagal export PDF: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleExportCsv() {
        List<ReportRow> exportData = getExportData();
        if (exportData.isEmpty()) {
            showError("Generate laporan terlebih dahulu sebelum export");
            return;
        }

        try {
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            String category = categoryCombo.getValue();

            File csvFile = com.baletpos.util.CsvExportUtil.exportCategorySalesReport(
                    exportData, category, startDate, endDate);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export CSV Berhasil");
            alert.setContentText("File: " + csvFile.getAbsolutePath());
            alert.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Gagal export CSV: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportExcel() {
        List<ReportRow> exportData = getExportData();
        if (exportData.isEmpty()) {
            showError("Generate laporan terlebih dahulu sebelum export");
            return;
        }

        String category = categoryCombo.getValue();

        ExcelExportUtil.export(
                (Stage) reportTable.getScene().getWindow(),
                "Laporan Penjualan - " + category,
                Arrays.asList("SKU", "Nama Produk", "Qty Terjual", "Omzet", "HPP", "Laba Kotor"),
                Arrays.asList(
                        (ReportRow row) -> row.getString("sku"),
                        (ReportRow row) -> row.getString("product_name"),
                        (ReportRow row) -> row.getInt("qty_sold"),
                        (ReportRow row) -> row.getLong("revenue"),
                        (ReportRow row) -> row.getLong("cogs"),
                        (ReportRow row) -> row.getLong("gross_profit")),
                FXCollections.observableArrayList(exportData));
    }

    private List<ReportRow> getExportData() {
        return filteredData != null ? filteredData : reportData.stream().toList();
    }
}


