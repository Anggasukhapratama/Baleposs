package com.baletpos.controller;

import com.baletpos.dao.ReportDAO;
import com.baletpos.dao.ReportDAO.ReportRow;
import com.baletpos.util.PdfExportUtil;
import com.baletpos.util.ExcelExportUtil;
import java.util.Arrays;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Controller untuk Laporan Laba Rugi
 */
public class ProfitLossReportController {

    @FXML
    private DatePicker startDatePicker;

    @FXML
    private DatePicker endDatePicker;

    @FXML
    private Label grossRevenueLabel;

    @FXML
    private Label salesReturnsLabel;

    @FXML
    private Label netRevenueLabel;

    @FXML
    private Label grossCogsLabel;

    @FXML
    private Label cogsReversalLabel;

    @FXML
    private Label netCogsLabel;

    @FXML
    private Label grossProfitLabel;

    @FXML
    private Label grossMarginLabel;

    @FXML
    private Label totalExpenseLabel;

    @FXML
    private Label netProfitLabel;

    @FXML
    private Label netMarginLabel;

    private final ReportDAO reportDAO = new ReportDAO();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private ReportRow currentReport;

    @FXML
    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);

        // Default to current month
        LocalDate now = LocalDate.now();
        startDatePicker.setValue(now.withDayOfMonth(1));
        endDatePicker.setValue(now.withDayOfMonth(now.lengthOfMonth()));
    }

    @FXML
    private void handleGenerateReport() {
        LocalDate startDate = startDatePicker.getValue();
        LocalDate endDate = endDatePicker.getValue();

        if (startDate == null || endDate == null) {
            showError("Pilih periode tanggal");
            return;
        }

        if (startDate.isAfter(endDate)) {
            showError("Tanggal awal tidak boleh setelah tanggal akhir");
            return;
        }

        currentReport = reportDAO.getProfitLossReport(startDate, endDate);

        // Update labels
        grossRevenueLabel.setText(currencyFormat.format(currentReport.getBigDecimal("gross_revenue")));
        salesReturnsLabel.setText("(" + currencyFormat.format(currentReport.getBigDecimal("sales_returns")) + ")");
        netRevenueLabel.setText(currencyFormat.format(currentReport.getBigDecimal("net_revenue")));

        grossCogsLabel.setText(currencyFormat.format(currentReport.getBigDecimal("gross_cogs")));
        cogsReversalLabel.setText("(" + currencyFormat.format(currentReport.getBigDecimal("cogs_reversal")) + ")");
        netCogsLabel.setText(currencyFormat.format(currentReport.getBigDecimal("net_cogs")));

        grossProfitLabel.setText(currencyFormat.format(currentReport.getBigDecimal("gross_profit")));
        grossMarginLabel.setText(currentReport.getBigDecimal("gross_margin_percent") + "%");

        totalExpenseLabel.setText("(" + currencyFormat.format(currentReport.getBigDecimal("total_expense")) + ")");

        BigDecimal netProfit = currentReport.getBigDecimal("net_profit");
        netProfitLabel.setText(currencyFormat.format(netProfit));
        netMarginLabel.setText(currentReport.getBigDecimal("net_margin_percent") + "%");

        // Color coding for profit/loss
        if (netProfit.compareTo(BigDecimal.ZERO) >= 0) {
            netProfitLabel.setStyle("-fx-text-fill: #1E40AF; -fx-font-weight: bold; -fx-font-size: 18px;");
        } else {
            netProfitLabel.setStyle("-fx-text-fill: #1F3A8A; -fx-font-weight: bold; -fx-font-size: 18px;");
        }
    }

    @FXML
    private void handleExport() {
        if (currentReport == null) {
            showError("Generate laporan terlebih dahulu sebelum export");
            return;
        }

        try {
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();

            File pdfFile = PdfExportUtil.exportProfitLossReport(currentReport, startDate, endDate);

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
    private void handleExportExcel() {
        if (currentReport == null) {
            showError("Generate laporan terlebih dahulu sebelum export");
            return;
        }

        ObservableList<PLRow> data = FXCollections.observableArrayList();
        data.add(new PLRow("PENDAPATAN", ""));
        data.add(new PLRow("  Penjualan Kotor", currencyFormat.format(currentReport.getBigDecimal("gross_revenue"))));
        data.add(new PLRow("  Retur Penjualan",
                "(" + currencyFormat.format(currentReport.getBigDecimal("sales_returns")) + ")"));
        data.add(new PLRow("  Pendapatan Bersih", currencyFormat.format(currentReport.getBigDecimal("net_revenue"))));
        data.add(new PLRow("", ""));

        data.add(new PLRow("HARGA POKOK PENJUALAN", ""));
        data.add(new PLRow("  HPP Penjualan", currencyFormat.format(currentReport.getBigDecimal("gross_cogs"))));
        data.add(new PLRow("  HPP Retur",
                "(" + currencyFormat.format(currentReport.getBigDecimal("cogs_reversal")) + ")"));
        data.add(new PLRow("  HPP Bersih", currencyFormat.format(currentReport.getBigDecimal("net_cogs"))));
        data.add(new PLRow("", ""));

        data.add(new PLRow("LABA KOTOR", currencyFormat.format(currentReport.getBigDecimal("gross_profit"))));
        data.add(new PLRow("Margin Kotor", currentReport.getBigDecimal("gross_margin_percent") + "%"));
        data.add(new PLRow("", ""));

        data.add(new PLRow("BIAYA OPERASIONAL",
                "(" + currencyFormat.format(currentReport.getBigDecimal("total_expense")) + ")"));
        data.add(new PLRow("", ""));

        data.add(new PLRow("LABA BERSIH", currencyFormat.format(currentReport.getBigDecimal("net_profit"))));
        data.add(new PLRow("Margin Bersih", currentReport.getBigDecimal("net_margin_percent") + "%"));

        String title = "Laporan Laba Rugi " + startDatePicker.getValue() + " sd " + endDatePicker.getValue();

        ExcelExportUtil.export(
                (Stage) grossRevenueLabel.getScene().getWindow(),
                title,
                Arrays.asList("Keterangan", "Jumlah"),
                Arrays.asList(
                        (PLRow row) -> row.getDescription(),
                        (PLRow row) -> row.getAmount()),
                data);
    }

    public static class PLRow {
        private String description;
        private String amount;

        public PLRow(String d, String a) {
            this.description = d;
            this.amount = a;
        }

        public String getDescription() {
            return description;
        }

        public String getAmount() {
            return amount;
        }
    }
}


