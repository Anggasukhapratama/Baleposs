package com.baletpos.service;

import com.baletpos.model.Sale;
import com.baletpos.model.SaleItem;
import com.baletpos.model.SalePayment;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.DashedLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.DashedBorder;
import com.itextpdf.layout.element.*;

import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PdfService {
    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);
    private static final String RECEIPT_DIR = System.getProperty("user.home") + "/.baletpos/receipts/";

    private static final DecimalFormat CURRENCY;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("id", "ID"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        CURRENCY = new DecimalFormat("#,###", symbols);
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm",
            new Locale("id", "ID"));

    public void generateReceipt(Sale sale) {
        try {
            ensureDirectoryExists();
            String dest = RECEIPT_DIR + sale.getInvoiceNumber() + ".pdf";

            PdfWriter writer = new PdfWriter(dest);
            PdfDocument pdf = new PdfDocument(writer);

            // Format ukuran kertas 24cm (Lebar 9.5 inch = ~24.13cm) x Full halaman (11 inch = ~27.94cm)
            // 1 inch = 72 points -> 9.5 * 72 = 684, 11 * 72 = 792
            com.itextpdf.kernel.geom.PageSize customPageSize = new com.itextpdf.kernel.geom.PageSize(684f, 792f);
            pdf.setDefaultPageSize(customPageSize);

            Document doc = new Document(pdf);
            doc.setMargins(20, 20, 20, 20);

            PdfFont font = PdfFontFactory.createFont(StandardFonts.COURIER);
            doc.setFont(font);
            doc.setFontSize(10);

            // ==========================================
            // 1. HEADER (TEXT LEFT + LOGO RIGHT)
            // ==========================================
            Table headerTable = new Table(UnitValue.createPercentArray(new float[] { 85, 15 }));
            headerTable.setWidth(UnitValue.createPercentValue(100));
            headerTable.setBorder(Border.NO_BORDER);

            Cell leftCell = new Cell();
            leftCell.setBorder(Border.NO_BORDER);
            leftCell.setTextAlignment(TextAlignment.LEFT);
            leftCell.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.TOP);

            Paragraph storeName = new Paragraph("BALET COMPUTER")
                    .setBold()
                    .setFontSize(16)
                    .setMarginTop(0)
                    .setMarginBottom(0)
                    .setTextAlignment(TextAlignment.LEFT);

            Paragraph storeAddr = new Paragraph("Jalan Prof. Moh. Yamin No 57 Kudaile Slawi Kab. Tegal")
                    .setFontSize(10)
                    .setMarginTop(2)
                    .setMarginBottom(0)
                    .setTextAlignment(TextAlignment.LEFT);

            leftCell.add(storeName);
            leftCell.add(storeAddr);

            Cell rightCell = new Cell();
            rightCell.setBorder(Border.NO_BORDER);
            rightCell.setTextAlignment(TextAlignment.RIGHT);
            rightCell.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.TOP);

            try {
                URL logoUrl = getClass().getResource("/images/logo1.png");
                if (logoUrl != null) {
                    ImageData imageData = ImageDataFactory.create(logoUrl);
                    Image logo = new Image(imageData);
                    logo.setWidth(36);
                    logo.setAutoScale(true);
                    rightCell.add(logo);
                }
            } catch (Exception e) {
                // ignore missing logo
            }

            headerTable.addCell(leftCell);
            headerTable.addCell(rightCell);

            doc.add(headerTable);

            // Dashed Divider under Header
            doc.add(new LineSeparator(new DashedLine(1)).setMarginTop(5).setMarginBottom(10));

            // ==========================================
            // 2. META INFO (Flex Row lookalike)
            // ==========================================
            // Table 2 columns (Left Meta, Right Meta)
            Table metaTable = new Table(UnitValue.createPercentArray(new float[] { 50, 50 }));
            metaTable.setWidth(UnitValue.createPercentValue(100));
            metaTable.setBorder(Border.NO_BORDER);

            // Left Meta
            Table leftMeta = new Table(UnitValue.createPercentArray(new float[] { 30, 70 }));
            leftMeta.addCell(cellNoBorder("No Nota"));
            leftMeta.addCell(cellNoBorder(": " + sale.getInvoiceNumber()));
            leftMeta.addCell(cellNoBorder("Kasir"));
            String cashier = sale.getCreatedByName() != null ? sale.getCreatedByName() : "Admin";
            leftMeta.addCell(cellNoBorder(": " + cashier));

            // Right Meta
            Table rightMeta = new Table(UnitValue.createPercentArray(new float[] { 30, 70 }));
            rightMeta.addCell(cellNoBorder("Tanggal"));
            rightMeta.addCell(cellNoBorder(": " + sale.getSaleDate().format(DATE_FMT)));
            rightMeta.addCell(cellNoBorder("Pelanggan"));
            String cust = sale.getCustomerName() != null ? sale.getCustomerName() : "Umum";
            rightMeta.addCell(cellNoBorder(": " + cust));

            metaTable.addCell(new Cell().add(leftMeta).setBorder(Border.NO_BORDER));
            metaTable.addCell(new Cell().add(rightMeta).setBorder(Border.NO_BORDER));
            doc.add(metaTable);

            // Spacer
            doc.add(new Paragraph(""));

            // ==========================================
            // 3. ITEMS TABLE
            // ==========================================
            // Columns: No(5%), Kode(15%), Nama(35%), Harga(15%), Qty(5%), Diskon(10%),
            // Subtotal(15%)
            float[] colWidths = { 5, 15, 35, 15, 5, 10, 15 };
            Table itemTable = new Table(UnitValue.createPercentArray(colWidths));
            itemTable.setWidth(UnitValue.createPercentValue(100));
            itemTable.setMarginTop(10);
            itemTable.setMarginBottom(10);

            // Header
            String[] headers = { "No", "Kode", "Nama Barang", "Harga", "Qty", "Diskon", "Subtotal" };
            for (int i = 0; i < headers.length; i++) {
                Cell h = new Cell().add(new Paragraph(headers[i]).setBold());
                h.setBorder(Border.NO_BORDER);
                h.setBorderTop(new DashedBorder(1));
                h.setBorderBottom(new DashedBorder(1));
                if (i >= 3)
                    h.setTextAlignment(TextAlignment.RIGHT); // Harga, Qty, Diskon, Subtotal
                if (i == 4)
                    h.setTextAlignment(TextAlignment.CENTER); // Qty Center
                itemTable.addCell(h);
            }

            // Rows
            int no = 1;
            for (SaleItem item : sale.getItems()) {
                itemTable.addCell(cellitem(String.valueOf(no++), TextAlignment.LEFT));
                itemTable.addCell(cellitem(item.getProductSku(), TextAlignment.LEFT));
                itemTable.addCell(cellitem(item.getProductName(), TextAlignment.LEFT));
                itemTable.addCell(cellitem(formatMoney(item.getUnitPrice()), TextAlignment.RIGHT));
                itemTable.addCell(cellitem(String.valueOf(item.getQuantity()), TextAlignment.CENTER));

                String disc = "0";
                if (item.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                    disc = formatMoney(item.getDiscountAmount());
                }
                itemTable.addCell(cellitem(disc, TextAlignment.RIGHT));
                itemTable.addCell(cellitem(formatMoney(item.getSubtotal()), TextAlignment.RIGHT));

                // SN Row if needed (Mock logic, we assume no SN for now or we simulate)
                // if (item has SN) ...
            }
            // Border bottom of last row (optional, user HTML template doesn't explicitly
            // close table bottom border, but looks better)

            doc.add(itemTable);

            // ==========================================
            // 4. TOTALS
            // ==========================================
            // Use Outer Table to align right like flex-end
            Table footerContainer = new Table(UnitValue.createPercentArray(new float[] { 60, 40 })); // 60% empty, 40%
                                                                                                     // totals
            footerContainer.setWidth(UnitValue.createPercentValue(100));
            footerContainer.setBorder(Border.NO_BORDER);

            // Left Side: SN / Warranty Info (sejajar dengan Totals)
            Cell leftInfoCell = new Cell().setBorder(Border.NO_BORDER);
            leftInfoCell.setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.TOP);

            // Check for items with SN/Buyer Info
            boolean hasLaptopInfo = false;
            for (SaleItem item : sale.getItems()) {
                if (item.getSerialNumber() != null && !item.getSerialNumber().isBlank()) {
                    if (!hasLaptopInfo) {
                        leftInfoCell.add(new Paragraph("Detail Garansi & SN:")
                                .setBold().setFontSize(9).setUnderline());
                        hasLaptopInfo = true;
                    }

                    String info = "SN: " + item.getSerialNumber();
                    if (item.getBuyerName() != null && !item.getBuyerName().isBlank()) {
                        info += "\nPembeli: " + item.getBuyerName();
                        if (item.getBuyerNik() != null && !item.getBuyerNik().isBlank()) {
                            info += " (" + item.getBuyerNik() + ")";
                        }
                    }
                    if (item.getBonusProductName() != null && !item.getBonusProductName().isBlank()) {
                        info += "\nBonus: " + item.getBonusProductName();
                    }
                    if (item.getWarrantyLabel() != null && !item.getWarrantyLabel().isBlank()) {
                        info += "\nGaransi: " + item.getWarrantyLabel();
                    }

                    leftInfoCell.add(new Paragraph(info).setFontSize(8).setMarginBottom(4));
                }
            }

            footerContainer.addCell(leftInfoCell);

            // Totals Table (Right Side)
            Table totalsTable = new Table(UnitValue.createPercentArray(new float[] { 50, 50 }));
            totalsTable.setWidth(UnitValue.createPercentValue(100));

            // Total Harga
            addTotalRow(totalsTable, "Total Harga", formatMoney(sale.getSubtotal()), false);
            // Diskon Global
            addTotalRow(totalsTable, "Diskon", formatMoney(sale.getDiscountAmount()), false);

            // Grand Total (Dashed Top)
            addTotalRow(totalsTable, "Total Bayar", formatMoney(sale.getTotalAmount()), true);

            // Payment Details
            if (sale.getPaymentType() == Sale.PaymentType.SPLIT) {
                // Split logic
                Cell detailHeader = new Cell(1, 2).add(new Paragraph("Detail Pembayaran:").setItalic());
                detailHeader.setBorder(Border.NO_BORDER);
                detailHeader.setBorderTop(new DashedBorder(1));
                totalsTable.addCell(detailHeader);

                for (SalePayment p : sale.getPayments()) {
                    String method = formatMethod(p.getMethod());
                    addTotalRow(totalsTable, "Bayar " + method, formatMoney(p.getAmount()), false);
                }
            } else {
                // Single Payment
                String method = sale.getPaymentMethod() != null ? sale.getPaymentMethod().getDisplayName() : "Cash";
                addTotalRow(totalsTable, "Metode Bayar", method, false);
                addTotalRow(totalsTable, "Tunai", formatMoney(sale.getPaymentAmount()), false);
            }

            // Change
            addTotalRow(totalsTable, "Kembali", formatMoney(sale.getChangeAmount()), false);

            footerContainer.addCell(new Cell().add(totalsTable).setBorder(Border.NO_BORDER));
            doc.add(footerContainer);

            // ==========================================
            // 5. FOOTER
            // ==========================================
            doc.add(new LineSeparator(new DashedLine(1)).setMarginTop(20));

            Paragraph footer = new Paragraph()
                    .add("Barang yang sudah dibeli tidak dapat dikembalikan / ditukar.\n")
                    .add("*** TERIMA KASIH ATAS KUNJUNGAN ANDA ***\n")
                    .add("Terima kasih telah berbelanja di Balet Computer. Simpan nota ini untuk klaim garansi SN.")
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(9)
                    .setMarginTop(5);
            doc.add(footer);

            doc.close();
            logger.info("Receipt generated: {}", dest);

            openPdf(dest);

        } catch (Exception e) {
            logger.error("Error generating PDF receipt", e);
        }
    }

    private Cell cellNoBorder(String text) {
        return new Cell().add(new Paragraph(text)).setBorder(Border.NO_BORDER).setPadding(1);
    }

    private Cell cellitem(String text, TextAlignment align) {
        return new Cell().add(new Paragraph(text))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(align)
                .setPadding(3);
    }

    private void addTotalRow(Table table, String label, String value, boolean isGrandTotal) {
        Cell cLabel = new Cell().add(new Paragraph(label));
        cLabel.setBorder(Border.NO_BORDER);

        Cell cValue = new Cell().add(new Paragraph(value));
        cValue.setBorder(Border.NO_BORDER);
        cValue.setTextAlignment(TextAlignment.RIGHT);

        if (isGrandTotal) {
            cLabel.setBold();
            cValue.setBold();
            cLabel.setBorderTop(new DashedBorder(1));
            cValue.setBorderTop(new DashedBorder(1));
        }

        table.addCell(cLabel);
        table.addCell(cValue);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null)
            return "0";
        return CURRENCY.format(amount);
    }

    private String formatMethod(String m) {
        if (m == null)
            return "-";
        m = m.replace("PAYLATER_", "");
        m = m.replace("_", " ");
        return m;
    }

    private void ensureDirectoryExists() {
        File dir = new File(RECEIPT_DIR);
        if (!dir.exists())
            dir.mkdirs();
    }

    private void openPdf(String dest) {
        if (java.awt.Desktop.isDesktopSupported()) {
            try {
                java.awt.Desktop.getDesktop().open(new File(dest));
            } catch (IOException ex) {
                logger.warn("Could not open PDF automatically", ex);
            }
        }
    }

    public static void printInvoice(Sale sale) {
        try {
            new ReceiptPrinterService().printReceipt(sale);
        } catch (Exception e) {
            throw new RuntimeException("Gagal mencetak nota langsung: " + e.getMessage(), e);
        }
    }
}


