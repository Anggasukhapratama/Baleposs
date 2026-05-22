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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Controller untuk Laporan Aset / Inventory Valuation
 */
public class InventoryReportController {

    @FXML
    private ComboBox<String> filterCombo;

    @FXML
    private TextField searchField;

    @FXML
    private TableView<ReportRow> reportTable;

    @FXML
    private TableColumn<ReportRow, String> skuCol;

    @FXML
    private TableColumn<ReportRow, String> nameCol;

    @FXML
    private TableColumn<ReportRow, String> typeCol;

    @FXML
    private TableColumn<ReportRow, String> qtyCol;

    @FXML
    private TableColumn<ReportRow, String> hppCol;

    @FXML
    private TableColumn<ReportRow, String> sellPriceCol;

    @FXML
    private TableColumn<ReportRow, String> hppValueCol;

    @FXML
    private TableColumn<ReportRow, String> sellValueCol;

    @FXML
    private Label totalQtyLabel;

    @FXML
    private Label totalHppValueLabel;

    @FXML
    private Label totalSellValueLabel;

    @FXML
    private Label cardAllCount;
    @FXML
    private Label cardLaptopNewCount;
    @FXML
    private Label cardLaptopSecondCount;
    @FXML
    private Label cardSparepartCount;
    @FXML
    private Label cardPeripheralCount;
    @FXML
    private Label cardServiceCount;

    @FXML
    private VBox tableContainer;

    private final ReportDAO reportDAO = new ReportDAO();
    private final ObservableList<ReportRow> reportData = FXCollections.observableArrayList();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    private List<ReportRow> allData = new ArrayList<>();
    private List<ReportRow> filteredData = new ArrayList<>();
    private PaginationControl pagination;
    private String currentCategory = "SEMUA";
    private String currentSearchQuery = "";

    @FXML
    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);

        // Setup filter combo
        filterCombo.setItems(FXCollections.observableArrayList("SEMUA", "LAPTOP_NEW", "LAPTOP_SECOND",
                "SPAREPARTS", "PERIPHERAL", "SERVICE"));
        filterCombo.setValue("SEMUA");
        filterCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentCategory = newVal != null ? newVal : "SEMUA";
            applyFilters();
        });

        setupTable();
        setupPagination();
        loadData();

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                currentSearchQuery = newVal != null ? newVal.trim().toLowerCase() : "";
                applyFilters();
            });
        }
    }

    private void setupTable() {
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        skuCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getString("sku")));
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getString("product_name")));
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getString("product_type")));
        qtyCol.setCellValueFactory(
                cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getInt("stock_qty"))));
        hppCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getLong("hpp"))));
        sellPriceCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getLong("selling_price"))));
        hppValueCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getLong("total_hpp_value"))));
        sellValueCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getLong("total_sell_value"))));

        setColumnGrow(skuCol, 1.1);
        setColumnGrow(nameCol, 2.6);
        setColumnGrow(typeCol, 1.2);
        setColumnGrow(qtyCol, 0.7);
        setColumnGrow(hppCol, 1.2);
        setColumnGrow(sellPriceCol, 1.2);
        setColumnGrow(hppValueCol, 1.3);
        setColumnGrow(sellValueCol, 1.3);

        reportTable.setItems(reportData);
    }

    private void setColumnGrow(TableColumn<?, ?> column, double weight) {
        column.setMaxWidth(1f * Integer.MAX_VALUE);
        column.setPrefWidth(weight);
    }

    private void loadData() {
        allData = reportDAO.getInventoryValuationReport();
        updateCategoryCards();
        applyFilters();
    }

    private void updateCategoryCards() {
        if (allData == null) {
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (ReportRow row : allData) {
            String type = row.getString("product_type");
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        int total = allData.size();

        if (cardAllCount != null)
            cardAllCount.setText(String.valueOf(total));
        if (cardLaptopNewCount != null)
            cardLaptopNewCount.setText(String.valueOf(counts.getOrDefault("LAPTOP_NEW", 0)));
        if (cardLaptopSecondCount != null)
            cardLaptopSecondCount.setText(String.valueOf(counts.getOrDefault("LAPTOP_SECOND", 0)));
        if (cardSparepartCount != null)
            cardSparepartCount.setText(String.valueOf(counts.getOrDefault("SPAREPARTS", 0)));
        if (cardPeripheralCount != null)
            cardPeripheralCount.setText(String.valueOf(counts.getOrDefault("PERIPHERAL", 0)));
        if (cardServiceCount != null)
            cardServiceCount.setText(String.valueOf(counts.getOrDefault("SERVICE", 0)));
    }

    private void applyFilters() {
        filteredData = new ArrayList<>();

        for (ReportRow row : allData) {
            boolean categoryOk = "SEMUA".equals(currentCategory)
                    || currentCategory.equals(row.getString("product_type"));
            if (!categoryOk) {
                continue;
            }

            if (!currentSearchQuery.isEmpty()) {
                String sku = row.getString("sku").toLowerCase();
                String name = row.getString("product_name").toLowerCase();
                if (!sku.contains(currentSearchQuery) && !name.contains(currentSearchQuery)) {
                    continue;
                }
            }
            filteredData.add(row);
        }

        if (pagination != null) {
            pagination.resetToFirstPage();
        }
        updatePage();
    }

    private void calculateTotals(List<ReportRow> rows) {
        int totalQty = rows.stream().mapToInt(r -> r.getInt("stock_qty")).sum();
        long totalHppValue = rows.stream().mapToLong(r -> r.getLong("total_hpp_value")).sum();
        long totalSellValue = rows.stream().mapToLong(r -> r.getLong("total_sell_value")).sum();

        totalQtyLabel.setText("Total Qty: " + totalQty + " unit");
        totalHppValueLabel.setText("Total Nilai HPP: " + currencyFormat.format(totalHppValue));
        totalSellValueLabel.setText("Total Nilai Jual: " + currencyFormat.format(totalSellValue));
    }

    @FXML
    private void handleRefresh() {
        loadData();
    }

    private void setupPagination() {
        pagination = new PaginationControl();
        pagination.setOnPageChange(this::updatePage);
        if (tableContainer != null) {
            tableContainer.getChildren().add(pagination);
        }
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
    private void handleExport() {
        List<ReportRow> exportData = getExportData();
        if (exportData.isEmpty()) {
            showInfo("Tidak ada data untuk diexport");
            return;
        }

        try {
            File pdfFile = PdfExportUtil.exportInventoryReport(exportData);

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

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Informasi");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleExportExcel() {
        List<ReportRow> exportData = getExportData();
        if (exportData.isEmpty()) {
            showInfo("Data kosong atau belum dimuat (coba Refresh)");
            return;
        }

        ExcelExportUtil.export(
                (Stage) reportTable.getScene().getWindow(),
                "Laporan Inventory Assets",
                Arrays.asList("SKU", "Nama Produk", "Kategori", "Stok", "HPP/Unit", "Harga Jual", "Nilai HPP Total",
                        "Nilai Jual Total"),
                Arrays.asList(
                        (ReportRow row) -> row.getString("sku"),
                        (ReportRow row) -> row.getString("product_name"),
                        (ReportRow row) -> row.getString("product_type"),
                        (ReportRow row) -> row.getInt("stock_qty"),
                        (ReportRow row) -> row.getLong("hpp"),
                        (ReportRow row) -> row.getLong("selling_price"),
                        (ReportRow row) -> row.getLong("total_hpp_value"),
                        (ReportRow row) -> row.getLong("total_sell_value")),
                FXCollections.observableArrayList(exportData));
    }

    private List<ReportRow> getExportData() {
        return filteredData != null ? filteredData : reportData.stream().toList();
    }
}


