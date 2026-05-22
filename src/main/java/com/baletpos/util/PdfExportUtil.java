package com.baletpos.util;

import com.baletpos.dao.ReportDAO.ReportRow;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Utility untuk export laporan ke PDF
 */
public class PdfExportUtil {
        private static final Logger logger = LoggerFactory.getLogger(PdfExportUtil.class);
        private static final NumberFormat CURRENCY_FMT = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
        private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy",
                        new Locale("id", "ID"));
        private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm",
                        new Locale("id", "ID"));

        private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(44, 62, 80);
        private static final DeviceRgb HEADER_BG = new DeviceRgb(52, 73, 94);
        private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(39, 174, 96);
        private static final DeviceRgb DANGER_COLOR = new DeviceRgb(231, 76, 60);

        static {
                CURRENCY_FMT.setMaximumFractionDigits(0);
        }

        /**
         * Export Laporan Laba Rugi ke PDF
         */
        public static File exportProfitLossReport(ReportRow report, LocalDate startDate, LocalDate endDate)
                        throws IOException {
                String fileName = "LapLagiRugi_" + startDate.toString() + "_" + endDate.toString() + ".pdf";
                File outputFile = getOutputFile(fileName);

                try (PdfWriter writer = new PdfWriter(outputFile);
                                PdfDocument pdf = new PdfDocument(writer);
                                Document document = new Document(pdf, PageSize.A4)) {

                        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                        PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                        // Header
                        addHeader(document, boldFont, "LAPORAN LABA RUGI");
                        addSubHeader(document, normalFont,
                                        "Periode: " + startDate.format(DATE_FMT) + " s/d " + endDate.format(DATE_FMT));
                        document.add(new Paragraph("\n"));

                        // Content Table
                        Table table = new Table(UnitValue.createPercentArray(new float[] { 60, 40 }))
                                        .useAllAvailableWidth();

                        // PENDAPATAN
                        addSectionRow(table, "PENDAPATAN", "", boldFont, HEADER_BG);
                        addDataRow(table, "Penjualan Kotor", formatCurrency(report.getBigDecimal("gross_revenue")),
                                        normalFont);
                        addDataRow(table, "Retur Penjualan",
                                        "(" + formatCurrency(report.getBigDecimal("sales_returns")) + ")",
                                        normalFont, DANGER_COLOR);
                        addTotalRow(table, "Pendapatan Bersih", formatCurrency(report.getBigDecimal("net_revenue")),
                                        boldFont);

                        // HARGA POKOK PENJUALAN
                        table.addCell(new Cell(1, 2).add(new Paragraph(" ")));
                        addSectionRow(table, "HARGA POKOK PENJUALAN", "", boldFont, HEADER_BG);
                        addDataRow(table, "HPP Penjualan", formatCurrency(report.getBigDecimal("gross_cogs")),
                                        normalFont);
                        addDataRow(table, "HPP Retur (Reversal)",
                                        "(" + formatCurrency(report.getBigDecimal("cogs_reversal")) + ")",
                                        normalFont, SUCCESS_COLOR);
                        addTotalRow(table, "HPP Bersih", formatCurrency(report.getBigDecimal("net_cogs")), boldFont);

                        // LABA KOTOR
                        table.addCell(new Cell(1, 2).add(new Paragraph(" ")));
                        addHighlightRow(table, "LABA KOTOR", formatCurrency(report.getBigDecimal("gross_profit")),
                                        boldFont,
                                        report.getBigDecimal("gross_profit").compareTo(BigDecimal.ZERO) >= 0
                                                        ? SUCCESS_COLOR
                                                        : DANGER_COLOR);
                        addDataRow(table, "Margin Kotor", report.getBigDecimal("gross_margin_percent") + "%",
                                        normalFont);

                        // BIAYA OPERASIONAL
                        table.addCell(new Cell(1, 2).add(new Paragraph(" ")));
                        addSectionRow(table, "BIAYA OPERASIONAL", "", boldFont, HEADER_BG);
                        addDataRow(table, "Total Biaya",
                                        "(" + formatCurrency(report.getBigDecimal("total_expense")) + ")",
                                        normalFont, DANGER_COLOR);

                        // LABA BERSIH
                        table.addCell(new Cell(1, 2).add(new Paragraph(" ")));
                        BigDecimal netProfit = report.getBigDecimal("net_profit");
                        addHighlightRow(table, "LABA / (RUGI) BERSIH", formatCurrency(netProfit), boldFont,
                                        netProfit.compareTo(BigDecimal.ZERO) >= 0 ? SUCCESS_COLOR : DANGER_COLOR);
                        addDataRow(table, "Margin Bersih", report.getBigDecimal("net_margin_percent") + "%",
                                        normalFont);

                        document.add(table);

                        // Footer
                        addFooter(document, normalFont);

                        logger.info("Profit Loss report exported to: {}", outputFile.getAbsolutePath());
                }

                return outputFile;
        }

        /**
         * Export Laporan Penjualan per Kategori ke PDF
         */
        public static File exportCategorySalesReport(List<ReportRow> data, String category,
                        LocalDate startDate, LocalDate endDate) throws IOException {

                String fileName = "LapPenjualan_" + category + "_" + startDate.toString() + "_" + endDate.toString()
                                + ".pdf";
                File outputFile = getOutputFile(fileName);

                try (PdfWriter writer = new PdfWriter(outputFile);
                                PdfDocument pdf = new PdfDocument(writer);
                                Document document = new Document(pdf, PageSize.A4.rotate())) { // Landscape

                        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                        PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                        // Header
                        addHeader(document, boldFont, "LAPORAN PENJUALAN - " + category);
                        addSubHeader(document, normalFont,
                                        "Periode: " + startDate.format(DATE_FMT) + " s/d " + endDate.format(DATE_FMT));
                        document.add(new Paragraph("\n"));

                        // Table
                        Table table = new Table(UnitValue.createPercentArray(new float[] { 10, 30, 10, 18, 18, 18 }))
                                        .useAllAvailableWidth();

                        // Header Row
                        addTableHeader(table, boldFont, "SKU", "Nama Produk", "Qty", "Omzet", "HPP", "Laba Kotor");

                        // Data Rows
                        long totalQty = 0, totalRevenue = 0, totalCogs = 0, totalProfit = 0;
                        for (ReportRow row : data) {
                                table.addCell(new Cell().add(new Paragraph(row.getString("sku")).setFont(normalFont)
                                                .setFontSize(9)));
                                table.addCell(new Cell()
                                                .add(new Paragraph(row.getString("product_name")).setFont(normalFont)
                                                                .setFontSize(9)));
                                table.addCell(new Cell().add(new Paragraph(String.valueOf(row.getInt("qty_sold")))
                                                .setFont(normalFont)
                                                .setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
                                table.addCell(new Cell().add(new Paragraph(formatCurrency(row.getLong("revenue")))
                                                .setFont(normalFont)
                                                .setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
                                table.addCell(new Cell().add(
                                                new Paragraph(formatCurrency(row.getLong("cogs"))).setFont(normalFont)
                                                                .setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
                                table.addCell(new Cell().add(new Paragraph(formatCurrency(row.getLong("gross_profit")))
                                                .setFont(normalFont).setFontSize(9)
                                                .setTextAlignment(TextAlignment.RIGHT)));

                                totalQty += row.getInt("qty_sold");
                                totalRevenue += row.getLong("revenue");
                                totalCogs += row.getLong("cogs");
                                totalProfit += row.getLong("gross_profit");
                        }

                        // Total Row
                        Cell totalCell = new Cell(1, 2).add(new Paragraph("TOTAL").setFont(boldFont).setFontSize(10));
                        totalCell.setBackgroundColor(new DeviceRgb(240, 240, 240));
                        table.addCell(totalCell);

                        table.addCell(
                                        new Cell()
                                                        .add(new Paragraph(String.valueOf(totalQty)).setFont(boldFont)
                                                                        .setFontSize(10)
                                                                        .setTextAlignment(TextAlignment.RIGHT))
                                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                        table.addCell(
                                        new Cell()
                                                        .add(new Paragraph(formatCurrency(totalRevenue))
                                                                        .setFont(boldFont).setFontSize(10)
                                                                        .setTextAlignment(TextAlignment.RIGHT))
                                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                        table.addCell(
                                        new Cell()
                                                        .add(new Paragraph(formatCurrency(totalCogs)).setFont(boldFont)
                                                                        .setFontSize(10)
                                                                        .setTextAlignment(TextAlignment.RIGHT))
                                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                        table.addCell(
                                        new Cell()
                                                        .add(new Paragraph(formatCurrency(totalProfit))
                                                                        .setFont(boldFont).setFontSize(10)
                                                                        .setTextAlignment(TextAlignment.RIGHT))
                                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));

                        document.add(table);

                        // Summary
                        document.add(new Paragraph("\n"));
                        document.add(
                                        new Paragraph("Total Transaksi: " + data.size() + " produk").setFont(normalFont)
                                                        .setFontSize(10));

                        // Footer
                        addFooter(document, normalFont);

                        logger.info("Category sales report exported to: {}", outputFile.getAbsolutePath());
                }

                return outputFile;
        }

        /**
         * Export Laporan Inventory/Aset ke PDF
         */
        public static File exportInventoryReport(List<ReportRow> data) throws IOException {
                String fileName = "LapAset_" + LocalDate.now().toString() + ".pdf";
                File outputFile = getOutputFile(fileName);

                try (PdfWriter writer = new PdfWriter(outputFile);
                                PdfDocument pdf = new PdfDocument(writer);
                                Document document = new Document(pdf, PageSize.A4.rotate())) {

                        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
                        PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

                        // Header
                        addHeader(document, boldFont, "LAPORAN ASET / INVENTORY VALUATION");
                        addSubHeader(document, normalFont, "Per tanggal: " + LocalDate.now().format(DATE_FMT));
                        document.add(new Paragraph("\n"));

                        // Table
                        Table table = new Table(UnitValue.createPercentArray(new float[] { 12, 28, 10, 8, 14, 14, 14 }))
                                        .useAllAvailableWidth();

                        // Header Row
                        addTableHeader(table, boldFont, "SKU", "Nama Produk", "Kategori", "Stok", "HPP/Unit",
                                        "Nilai HPP",
                                        "Nilai Jual");

                        // Data Rows
                        long totalQty = 0, totalHppValue = 0, totalSellValue = 0;
                        for (ReportRow row : data) {
                                table.addCell(new Cell().add(new Paragraph(row.getString("sku")).setFont(normalFont)
                                                .setFontSize(9)));
                                table.addCell(new Cell()
                                                .add(new Paragraph(row.getString("product_name")).setFont(normalFont)
                                                                .setFontSize(9)));
                                table.addCell(new Cell()
                                                .add(new Paragraph(row.getString("product_type")).setFont(normalFont)
                                                                .setFontSize(9)));
                                table.addCell(new Cell().add(new Paragraph(String.valueOf(row.getInt("stock_qty")))
                                                .setFont(normalFont)
                                                .setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
                                table.addCell(new Cell().add(
                                                new Paragraph(formatCurrency(row.getLong("hpp"))).setFont(normalFont)
                                                                .setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
                                table.addCell(new Cell()
                                                .add(new Paragraph(formatCurrency(row.getLong("total_hpp_value")))
                                                                .setFont(normalFont).setFontSize(9)
                                                                .setTextAlignment(TextAlignment.RIGHT)));
                                table.addCell(new Cell()
                                                .add(new Paragraph(formatCurrency(row.getLong("total_sell_value")))
                                                                .setFont(normalFont).setFontSize(9)
                                                                .setTextAlignment(TextAlignment.RIGHT)));

                                totalQty += row.getInt("stock_qty");
                                totalHppValue += row.getLong("total_hpp_value");
                                totalSellValue += row.getLong("total_sell_value");
                        }

                        // Total Row
                        Cell totalCell = new Cell(1, 3).add(new Paragraph("TOTAL").setFont(boldFont).setFontSize(10));
                        totalCell.setBackgroundColor(new DeviceRgb(240, 240, 240));
                        table.addCell(totalCell);

                        table.addCell(
                                        new Cell()
                                                        .add(new Paragraph(String.valueOf(totalQty)).setFont(boldFont)
                                                                        .setFontSize(10)
                                                                        .setTextAlignment(TextAlignment.RIGHT))
                                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                        table.addCell(new Cell().add(new Paragraph(""))
                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                        table.addCell(
                                        new Cell()
                                                        .add(new Paragraph(formatCurrency(totalHppValue))
                                                                        .setFont(boldFont).setFontSize(10)
                                                                        .setTextAlignment(TextAlignment.RIGHT))
                                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));
                        table.addCell(
                                        new Cell()
                                                        .add(new Paragraph(formatCurrency(totalSellValue))
                                                                        .setFont(boldFont).setFontSize(10)
                                                                        .setTextAlignment(TextAlignment.RIGHT))
                                                        .setBackgroundColor(new DeviceRgb(240, 240, 240)));

                        document.add(table);

                        // Summary
                        document.add(new Paragraph("\n"));
                        document.add(new Paragraph("Total SKU: " + data.size() + " produk").setFont(normalFont)
                                        .setFontSize(10));
                        document.add(new Paragraph("Potensi Laba: " + formatCurrency(totalSellValue - totalHppValue))
                                        .setFont(boldFont).setFontSize(12).setFontColor(SUCCESS_COLOR));

                        // Footer
                        addFooter(document, normalFont);

                        logger.info("Inventory report exported to: {}", outputFile.getAbsolutePath());
                }

                return outputFile;
        }

        // Helper methods
        private static File getOutputFile(String fileName) {
                String userHome = System.getProperty("user.home");
                File reportDir = new File(userHome, "baletpos/reports");
                if (!reportDir.exists()) {
                        reportDir.mkdirs();
                }
                return new File(reportDir, fileName);
        }

        private static void addHeader(Document document, PdfFont font, String title) {
                Paragraph header = new Paragraph("BALET POS")
                                .setFont(font)
                                .setFontSize(20)
                                .setFontColor(PRIMARY_COLOR)
                                .setTextAlignment(TextAlignment.CENTER);
                document.add(header);

                Paragraph subHeader = new Paragraph(title)
                                .setFont(font)
                                .setFontSize(16)
                                .setTextAlignment(TextAlignment.CENTER);
                document.add(subHeader);
        }

        private static void addSubHeader(Document document, PdfFont font, String text) {
                Paragraph subHeader = new Paragraph(text)
                                .setFont(font)
                                .setFontSize(11)
                                .setTextAlignment(TextAlignment.CENTER)
                                .setFontColor(new DeviceRgb(100, 100, 100));
                document.add(subHeader);
        }

        private static void addFooter(Document document, PdfFont font) {
                document.add(new Paragraph("\n\n"));
                document.add(new Paragraph("Dicetak pada: " + LocalDateTime.now().format(DATETIME_FMT))
                                .setFont(font)
                                .setFontSize(9)
                                .setFontColor(new DeviceRgb(150, 150, 150))
                                .setTextAlignment(TextAlignment.RIGHT));
                document.add(new Paragraph("BaletPOS v1.0.0")
                                .setFont(font)
                                .setFontSize(9)
                                .setFontColor(new DeviceRgb(150, 150, 150))
                                .setTextAlignment(TextAlignment.RIGHT));
        }

        private static void addSectionRow(Table table, String label, String value, PdfFont font, DeviceRgb bgColor) {
                table.addCell(
                                new Cell().add(new Paragraph(label).setFont(font).setFontSize(11)
                                                .setFontColor(ColorConstants.WHITE))
                                                .setBackgroundColor(bgColor));
                table.addCell(new Cell().add(new Paragraph(value).setFont(font).setFontSize(11)
                                .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.RIGHT))
                                .setBackgroundColor(bgColor));
        }

        private static void addDataRow(Table table, String label, String value, PdfFont font) {
                table.addCell(new Cell().add(new Paragraph("    " + label).setFont(font).setFontSize(10)));
                table.addCell(new Cell()
                                .add(new Paragraph(value).setFont(font).setFontSize(10)
                                                .setTextAlignment(TextAlignment.RIGHT)));
        }

        private static void addDataRow(Table table, String label, String value, PdfFont font, DeviceRgb valueColor) {
                table.addCell(new Cell().add(new Paragraph("    " + label).setFont(font).setFontSize(10)));
                table.addCell(new Cell().add(new Paragraph(value).setFont(font).setFontSize(10).setFontColor(valueColor)
                                .setTextAlignment(TextAlignment.RIGHT)));
        }

        private static void addTotalRow(Table table, String label, String value, PdfFont font) {
                Cell labelCell = new Cell().add(new Paragraph(label).setFont(font).setFontSize(11));
                labelCell.setBackgroundColor(new DeviceRgb(245, 245, 245));
                table.addCell(labelCell);

                Cell valueCell = new Cell()
                                .add(new Paragraph(value).setFont(font).setFontSize(11)
                                                .setTextAlignment(TextAlignment.RIGHT));
                valueCell.setBackgroundColor(new DeviceRgb(245, 245, 245));
                table.addCell(valueCell);
        }

        private static void addHighlightRow(Table table, String label, String value, PdfFont font,
                        DeviceRgb valueColor) {
                Cell labelCell = new Cell().add(new Paragraph(label).setFont(font).setFontSize(12));
                labelCell.setBackgroundColor(new DeviceRgb(240, 247, 255));
                table.addCell(labelCell);

                Cell valueCell = new Cell()
                                .add(new Paragraph(value).setFont(font).setFontSize(14).setFontColor(valueColor)
                                                .setTextAlignment(TextAlignment.RIGHT));
                valueCell.setBackgroundColor(new DeviceRgb(240, 247, 255));
                table.addCell(valueCell);
        }

        private static void addTableHeader(Table table, PdfFont font, String... headers) {
                for (String header : headers) {
                        Cell cell = new Cell()
                                        .add(new Paragraph(header).setFont(font).setFontSize(10)
                                                        .setFontColor(ColorConstants.WHITE))
                                        .setBackgroundColor(HEADER_BG)
                                        .setTextAlignment(TextAlignment.CENTER);
                        table.addCell(cell);
                }
        }

        private static String formatCurrency(BigDecimal amount) {
                return CURRENCY_FMT.format(amount);
        }

        private static String formatCurrency(long amount) {
                return CURRENCY_FMT.format(amount);
        }
}


