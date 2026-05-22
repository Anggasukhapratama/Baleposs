package com.baletpos.util;

import com.baletpos.model.Sale;
import com.baletpos.model.SaleItem;
import com.baletpos.model.SalePayment;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ReceiptFormatter {

    // SPEC: 80 Characters for 9.5 inch Continuous Form
    private static final int WIDTH = 80;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DecimalFormat CURRENCY_FMT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("id", "ID"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        CURRENCY_FMT = new DecimalFormat("#,###", symbols);
    }

    public static String format(Sale sale) {
        StringBuilder sb = new StringBuilder();

        // ==================================================
        // HEADER
        // ==================================================
        sb.append(center("BALET COMPUTER")).append("\n");
        sb.append(center("Jalan Prof. Moh. Yamin No 57")).append("\n");
        sb.append(center("Kudaile Slawi Kab. Tegal")).append("\n");
        sb.append(separator()).append("\n");

        sb.append(pair("No Nota", ": " + sale.getInvoiceNumber(), "Tanggal", ": " + sale.getSaleDate().format(DATE_FMT)));
        sb.append("\n");

        String cashier = sale.getCreatedByName() != null ? sale.getCreatedByName() : "System";
        String customer = sale.getCustomerName() != null ? sale.getCustomerName() : "Umum";

        sb.append(pair("Kasir", ": " + cashier, "Pelanggan", ": " + customer));
        sb.append("\n\n");
        sb.append(separator()).append("\n");

        // ==================================================
        // TABEL ITEM
        // ==================================================
        // Header Tabel (80 columns)
        // No(3) | Kode(13) | Nama Barang(25) | Harga(11) | Qty(4) | Diskon(10) | Subtotal(14)
        sb.append("No  Kode          Nama Barang               Harga       Qty Diskon     Subtotal\n");
        sb.append(separator()).append("\n");

        int no = 1;
        for (SaleItem item : sale.getItems()) {
            String noStr = padRight(String.valueOf(no), 3);

            String sku = item.getProductSku();
            if (sku.length() > 13) sku = sku.substring(0, 13);
            sku = padRight(sku, 13);

            String name = item.getProductName();
            if (name.length() > 25) name = name.substring(0, 25);
            name = padRight(name, 25);

            String price = padLeft(formatMoney(item.getUnitPrice()), 11);
            String qty = padLeft(String.valueOf(item.getQuantity()), 4);

            String disc = formatMoney(item.getDiscountAmount());
            if (item.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0) disc = "0";
            disc = padLeft(disc, 10);

            String subtotal = padLeft(formatMoney(item.getSubtotal()), 14);

            sb.append(noStr).append(" ").append(sku).append(" ").append(name).append(" ")
              .append(price).append(" ").append(qty).append(" ").append(disc).append(subtotal).append("\n");

            if (item.getSerialNumber() != null && !item.getSerialNumber().isBlank()) {
                sb.append("    SN: ").append(item.getSerialNumber());
                if (item.getBuyerName() != null && !item.getBuyerName().isBlank()) {
                    sb.append(" | Pembeli: ").append(item.getBuyerName());
                }
                sb.append("\n");
            }
            if (item.getBonusProductName() != null && !item.getBonusProductName().isBlank()) {
                sb.append("    Bonus: ").append(item.getBonusProductName()).append("\n");
            }
            if (item.getWarrantyLabel() != null && !item.getWarrantyLabel().isBlank()) {
                sb.append("    Garansi: ").append(item.getWarrantyLabel()).append("\n");
            }
            no++;
        }
        sb.append(separator()).append("\n");

        // ==================================================
        // RINGKASAN PEMBAYARAN
        // ==================================================
        sb.append(padLeft("Total Harga : " + padLeft(formatMoney(sale.getSubtotal()), 14), WIDTH)).append("\n");
        sb.append(padLeft("Diskon : " + padLeft(formatMoney(sale.getDiscountAmount()), 14), WIDTH)).append("\n");
        sb.append(padLeft("TOTAL BAYAR : " + padLeft(formatMoney(sale.getTotalAmount()), 14), WIDTH)).append("\n\n");

        // Pembayaran
        if (sale.getPaymentType() == Sale.PaymentType.SPLIT && sale.getPayments() != null) {
            for (SalePayment p : sale.getPayments()) {
                String label = p.getMethod().replace("PAYLATER_", "").replace("_", " ");
                sb.append(padLeft("Bayar " + padRight(label, 12) + " : " + padLeft(formatMoney(p.getAmount()), 14), WIDTH)).append("\n");
            }
        } else {
            String method = sale.getPaymentMethod() != null ? sale.getPaymentMethod().name().replace("_", " ") : "CASH";
            sb.append(padLeft("Bayar " + padRight(method, 12) + " : " + padLeft(formatMoney(sale.getPaymentAmount()), 14), WIDTH)).append("\n");
        }

        sb.append(padLeft("Kembali : " + padLeft(formatMoney(sale.getChangeAmount()), 14), WIDTH)).append("\n\n");
        sb.append(separator()).append("\n");

        // ==================================================
        // FOOTER
        // ==================================================
        sb.append(center("Barang yang sudah dibeli tidak dapat dikembalikan / ditukar")).append("\n\n");
        sb.append(center("*** TERIMA KASIH ATAS KUNJUNGAN ANDA ***")).append("\n");
        sb.append(separator()).append("\n");

        return sb.toString();
    }

    private static String separator() {
        return "-".repeat(WIDTH);
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) return "0";
        return CURRENCY_FMT.format(amount);
    }

    private static String center(String s) {
        if (s.length() >= WIDTH) return s;
        int padding = (WIDTH - s.length()) / 2;
        return " ".repeat(padding) + s;
    }

    // Helper: 2 columns Key-Value pairs
    private static String pair(String k1, String v1, String k2, String v2) {
        String left = padRight(k1, 10) + padRight(v1, 28);
        String right = padRight(k2, 12) + v2;
        return padRight(left + right, WIDTH);
    }

    // Helper: Pad Right (Text align Left)
    private static String padRight(String s, int n) {
        if (s.length() > n)
            return s;
        return String.format("%-" + n + "s", s);
    }

    // Helper: Pad Left (Number align Right)
    private static String padLeft(String s, int n) {
        if (s.length() > n)
            return s;
        return String.format("%" + n + "s", s);
    }
}


