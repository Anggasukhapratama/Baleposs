package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ReportDAO {
    private static final Logger logger = LoggerFactory.getLogger(ReportDAO.class);
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static class ReportRow {
        private Map<String, Object> data = new HashMap<>();

        public void put(String key, Object value) { data.put(key, value); }
        public Object get(String key) { return data.get(key); }
        public String getString(String key) { Object val = data.get(key); return val != null ? val.toString() : ""; }
        public Long getLong(String key) { Object val = data.get(key); if (val instanceof Number) return ((Number) val).longValue(); return 0L; }
        public Integer getInt(String key) { Object val = data.get(key); if (val instanceof Number) return ((Number) val).intValue(); return 0; }
        public BigDecimal getBigDecimal(String key) { Object val = data.get(key); if (val instanceof Number) return BigDecimal.valueOf(((Number) val).longValue()); return BigDecimal.ZERO; }
        public Map<String, Object> getData() { return data; }
    }

    public List<ReportRow> getSalesReportLaptop(LocalDate startDate, LocalDate endDate) {
        List<ReportRow> newLaptops = getSalesReportByProductType(startDate, endDate, "LAPTOP_NEW");
        List<ReportRow> secondLaptops = getSalesReportByProductType(startDate, endDate, "LAPTOP_SECOND");
        List<ReportRow> combined = new ArrayList<>(newLaptops);
        combined.addAll(secondLaptops);
        return combined;
    }

    public List<ReportRow> getSalesReportPeripheral(LocalDate startDate, LocalDate endDate) {
        return getSalesReportByProductType(startDate, endDate, "PERIPHERAL");
    }

    public List<ReportRow> getSalesReportService(LocalDate startDate, LocalDate endDate) {
        return getSalesReportByProductType(startDate, endDate, "SERVICE");
    }

    public List<ReportRow> getSalesReportByProductType(LocalDate startDate, LocalDate endDate, String productType) {
        List<ReportRow> rows = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            String startStr = startDate.atStartOfDay().format(DB_DATE_FMT);
            String endStr = endDate.atTime(23, 59, 59).format(DB_DATE_FMT);

            // Fetch products of specific type
            List<QueryDocumentSnapshot> products = db.collection("products")
                    .whereEqualTo("product_type_code", productType)
                    .get().get().getDocuments();

            if (products.isEmpty()) return rows;

            Map<Long, QueryDocumentSnapshot> productMap = products.stream()
                    .collect(Collectors.toMap(doc -> doc.getLong("id"), doc -> doc));

            // Fetch valid sales
            List<QueryDocumentSnapshot> sales = db.collection("sales")
                    .whereGreaterThanOrEqualTo("sale_date", startStr)
                    .whereLessThanOrEqualTo("sale_date", endStr)
                    .whereEqualTo("status", "COMPLETED")
                    .get().get().getDocuments();

            Set<Long> validSaleIds = sales.stream()
                    .map(doc -> doc.getLong("id"))
                    .collect(Collectors.toSet());

            if (validSaleIds.isEmpty()) return rows;

            // In-memory aggregation
            Map<Long, ReportRow> aggMap = new HashMap<>();

            List<QueryDocumentSnapshot> allItems = db.collection("sale_items").get().get().getDocuments();

            for (QueryDocumentSnapshot itemDoc : allItems) {
                Long saleId = itemDoc.getLong("sale_id");
                if (validSaleIds.contains(saleId)) {
                    Long productId = itemDoc.getLong("product_id");
                    if (productMap.containsKey(productId)) {
                        QueryDocumentSnapshot prodDoc = productMap.get(productId);
                        ReportRow row = aggMap.computeIfAbsent(productId, k -> {
                            ReportRow r = new ReportRow();
                            r.put("sku", prodDoc.getString("sku"));
                            r.put("product_name", prodDoc.getString("name"));
                            r.put("qty_sold", 0);
                            r.put("revenue", 0L);
                            r.put("cogs", 0L);
                            return r;
                        });

                        int qty = itemDoc.getLong("quantity") != null ? itemDoc.getLong("quantity").intValue() : 0;
                        long subtotal = itemDoc.getDouble("subtotal") != null ? itemDoc.getDouble("subtotal").longValue() : 0L;
                        long hpp = itemDoc.getDouble("hpp_per_unit") != null ? itemDoc.getDouble("hpp_per_unit").longValue() : 0L;
                        
                        row.put("qty_sold", row.getInt("qty_sold") + qty);
                        row.put("revenue", row.getLong("revenue") + subtotal);
                        row.put("cogs", row.getLong("cogs") + (qty * hpp));
                        row.put("gross_profit", row.getLong("revenue") - row.getLong("cogs"));
                    }
                }
            }
            
            rows.addAll(aggMap.values());
            rows.sort((a, b) -> b.getLong("revenue").compareTo(a.getLong("revenue")));

        } catch (Exception e) {
            logger.error("Error getting sales report for {}", productType, e);
        }
        return rows;
    }

    public List<ReportRow> getInventoryValuationReport() {
        List<ReportRow> rows = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            List<QueryDocumentSnapshot> products = db.collection("products")
                    .whereEqualTo("is_active", 1)
                    .get().get().getDocuments();
                    
            if (products.isEmpty()) {
                products = db.collection("products")
                    .whereEqualTo("is_active", true)
                    .get().get().getDocuments();
            }

            for (QueryDocumentSnapshot doc : products) {
                ReportRow row = new ReportRow();
                row.put("sku", doc.getString("sku"));
                row.put("product_name", doc.getString("name"));
                row.put("product_type", doc.getString("product_type_code"));
                int stock = doc.getLong("stock") != null ? doc.getLong("stock").intValue() : 0;
                long hpp = doc.getDouble("hpp") != null ? doc.getDouble("hpp").longValue() : 0L;
                long sell = doc.getDouble("selling_price") != null ? doc.getDouble("selling_price").longValue() : 0L;
                
                row.put("stock_qty", stock);
                row.put("hpp", hpp);
                row.put("selling_price", sell);
                row.put("total_hpp_value", stock * hpp);
                row.put("total_sell_value", stock * sell);
                rows.add(row);
            }
            rows.sort(Comparator.comparing(r -> r.getString("product_type") + r.getString("sku")));
            
        } catch (Exception e) {
            logger.error("Error getting inventory valuation report", e);
        }
        return rows;
    }

    public ReportRow getInventoryTotals() {
        ReportRow totals = new ReportRow();
        totals.put("total_qty", 0);
        totals.put("total_hpp_value", 0L);
        totals.put("total_sell_value", 0L);

        try {
            Firestore db = FirestoreHelper.getDb();
            List<QueryDocumentSnapshot> products = db.collection("products").whereEqualTo("is_active", 1).get().get().getDocuments();
            if(products.isEmpty()) products = db.collection("products").whereEqualTo("is_active", true).get().get().getDocuments();

            int totalQty = 0;
            long totalHpp = 0;
            long totalSell = 0;

            for (QueryDocumentSnapshot doc : products) {
                int stock = doc.getLong("stock") != null ? doc.getLong("stock").intValue() : 0;
                long hpp = doc.getDouble("hpp") != null ? doc.getDouble("hpp").longValue() : 0L;
                long sell = doc.getDouble("selling_price") != null ? doc.getDouble("selling_price").longValue() : 0L;

                totalQty += stock;
                totalHpp += (stock * hpp);
                totalSell += (stock * sell);
            }
            
            totals.put("total_qty", totalQty);
            totals.put("total_hpp_value", totalHpp);
            totals.put("total_sell_value", totalSell);

        } catch (Exception e) {
            logger.error("Error getting inventory totals", e);
        }
        return totals;
    }

    // Profit & Loss
    public ReportRow getProfitLossReport(LocalDate startDate, LocalDate endDate) {
        ReportRow report = new ReportRow();
        String startStr = startDate.atStartOfDay().format(DB_DATE_FMT);
        String endStr = endDate.atTime(23, 59, 59).format(DB_DATE_FMT);

        try {
            Firestore db = FirestoreHelper.getDb();

            // Gross Revenue & Sale IDs
            List<QueryDocumentSnapshot> sales = db.collection("sales")
                    .whereGreaterThanOrEqualTo("sale_date", startStr)
                    .whereLessThanOrEqualTo("sale_date", endStr)
                    .whereEqualTo("status", "COMPLETED")
                    .get().get().getDocuments();

            long grossRevenueVal = 0;
            Set<Long> validSaleIds = new HashSet<>();
            for (QueryDocumentSnapshot sale : sales) {
                validSaleIds.add(sale.getLong("id"));
                grossRevenueVal += sale.getDouble("total_amount") != null ? sale.getDouble("total_amount").longValue() : 0;
            }

            // Sales Returns (Value) & Return IDs
            List<QueryDocumentSnapshot> returns = db.collection("sales_returns")
                    .whereGreaterThanOrEqualTo("created_at", startStr)
                    .whereLessThanOrEqualTo("created_at", endStr)
                    .whereEqualTo("status", "COMPLETED")
                    .get().get().getDocuments();

            long salesReturnsVal = 0;
            Set<Long> validReturnIds = new HashSet<>();
            for (QueryDocumentSnapshot ret : returns) {
                validReturnIds.add(ret.getLong("id"));
                salesReturnsVal += ret.getDouble("total_amount") != null ? ret.getDouble("total_amount").longValue() : 0;
            }

            // COGS (Requires sale_items)
            long grossCogsVal = 0;
            List<QueryDocumentSnapshot> allSaleItems = db.collection("sale_items").get().get().getDocuments();
            for (QueryDocumentSnapshot item : allSaleItems) {
                if (validSaleIds.contains(item.getLong("sale_id"))) {
                    long qty = item.getLong("quantity") != null ? item.getLong("quantity") : 0;
                    long hpp = item.getDouble("hpp_per_unit") != null ? item.getDouble("hpp_per_unit").longValue() : 0;
                    grossCogsVal += (qty * hpp);
                }
            }

            // COGS Reversal (Requires sales_return_items)
            long cogsReversalVal = 0;
            List<QueryDocumentSnapshot> allReturnItems = db.collection("sales_return_items").get().get().getDocuments();
            for (QueryDocumentSnapshot item : allReturnItems) {
                if (validReturnIds.contains(item.getLong("sales_return_id"))) {
                    long qty = item.getLong("quantity") != null ? item.getLong("quantity") : 0;
                    long hpp = item.getDouble("hpp_per_unit") != null ? item.getDouble("hpp_per_unit").longValue() : 0;
                    cogsReversalVal += (qty * hpp);
                }
            }

            // Expenses
            long totalExpenseVal = 0;
            List<QueryDocumentSnapshot> expenses = db.collection("expenses")
                    .whereGreaterThanOrEqualTo("expense_date", startStr)
                    .whereLessThanOrEqualTo("expense_date", endStr)
                    .get().get().getDocuments();
            for (QueryDocumentSnapshot exp : expenses) {
                totalExpenseVal += exp.getDouble("amount") != null ? exp.getDouble("amount").longValue() : 0;
            }

            BigDecimal grossRevenue = BigDecimal.valueOf(grossRevenueVal);
            BigDecimal salesReturns = BigDecimal.valueOf(salesReturnsVal);
            BigDecimal netRevenue = grossRevenue.subtract(salesReturns);
            BigDecimal grossCogs = BigDecimal.valueOf(grossCogsVal);
            BigDecimal cogsReversal = BigDecimal.valueOf(cogsReversalVal);
            BigDecimal netCogs = grossCogs.subtract(cogsReversal);
            BigDecimal grossProfit = netRevenue.subtract(netCogs);
            BigDecimal totalExpense = BigDecimal.valueOf(totalExpenseVal);
            BigDecimal netProfit = grossProfit.subtract(totalExpense);

            report.put("gross_revenue", grossRevenue);
            report.put("sales_returns", salesReturns);
            report.put("net_revenue", netRevenue);
            report.put("gross_cogs", grossCogs);
            report.put("cogs_reversal", cogsReversal);
            report.put("net_cogs", netCogs);
            report.put("gross_profit", grossProfit);
            report.put("total_expense", totalExpense);
            report.put("net_profit", netProfit);
            report.put("start_date", startDate.toString());
            report.put("end_date", endDate.toString());

            if (netRevenue.compareTo(BigDecimal.ZERO) > 0) {
                report.put("gross_margin_percent", grossProfit.multiply(BigDecimal.valueOf(100)).divide(netRevenue, 2, java.math.RoundingMode.HALF_UP));
                report.put("net_margin_percent", netProfit.multiply(BigDecimal.valueOf(100)).divide(netRevenue, 2, java.math.RoundingMode.HALF_UP));
            } else {
                report.put("gross_margin_percent", BigDecimal.ZERO);
                report.put("net_margin_percent", BigDecimal.ZERO);
            }

        } catch (Exception e) {
            logger.error("Error generating profit/loss report", e);
        }
        return report;
    }

    public List<ReportRow> getExpenseReport(LocalDate startDate, LocalDate endDate) {
        List<ReportRow> rows = new ArrayList<>();
        String startStr = startDate.atStartOfDay().format(DB_DATE_FMT);
        String endStr = endDate.atTime(23, 59, 59).format(DB_DATE_FMT);

        try {
            Firestore db = FirestoreHelper.getDb();
            List<QueryDocumentSnapshot> expenses = db.collection("expenses")
                    .whereGreaterThanOrEqualTo("expense_date", startStr)
                    .whereLessThanOrEqualTo("expense_date", endStr)
                    .get().get().getDocuments();

            Map<Long, ReportRow> agg = new HashMap<>();
            
            Map<Long, String> expenseCodes = new HashMap<>();
            List<QueryDocumentSnapshot> codesDocs = db.collection("expense_codes").get().get().getDocuments();
            for (QueryDocumentSnapshot c : codesDocs) {
                expenseCodes.put(c.getLong("id"), c.getString("code") + "|" + c.getString("name"));
            }

            for (QueryDocumentSnapshot exp : expenses) {
                Long codeId = exp.getLong("expense_code_id");
                ReportRow row = agg.computeIfAbsent(codeId, k -> {
                    ReportRow r = new ReportRow();
                    String info = expenseCodes.getOrDefault(k, "UNKNOWN|Unknown");
                    r.put("expense_code", info.split("\\|")[0]);
                    r.put("expense_name", info.split("\\|")[1]);
                    r.put("total_amount", 0L);
                    r.put("transaction_count", 0);
                    return r;
                });
                
                long amt = exp.getDouble("amount") != null ? exp.getDouble("amount").longValue() : 0L;
                row.put("total_amount", row.getLong("total_amount") + amt);
                row.put("transaction_count", row.getInt("transaction_count") + 1);
            }

            rows.addAll(agg.values());
            rows.sort((a, b) -> b.getLong("total_amount").compareTo(a.getLong("total_amount")));

        } catch (Exception e) {
            logger.error("Error getting expense report", e);
        }
        return rows;
    }

    public ReportRow getDashboardSummary(LocalDate date) {
        ReportRow summary = new ReportRow();
        summary.put("today_sales_count", 0);
        summary.put("today_sales_total", 0L);
        summary.put("low_stock_count", 0);
        summary.put("total_products", 0);
        summary.put("month_revenue", 0L);

        try {
            Firestore db = FirestoreHelper.getDb();
            String todayStart = date.atStartOfDay().format(DB_DATE_FMT);
            String todayEnd = date.atTime(23, 59, 59).format(DB_DATE_FMT);

            List<QueryDocumentSnapshot> todaySales = db.collection("sales")
                    .whereGreaterThanOrEqualTo("sale_date", todayStart)
                    .whereLessThanOrEqualTo("sale_date", todayEnd)
                    .whereEqualTo("status", "COMPLETED")
                    .get().get().getDocuments();

            summary.put("today_sales_count", todaySales.size());
            long todayTotal = todaySales.stream().mapToLong(doc -> doc.getDouble("total_amount") != null ? doc.getDouble("total_amount").longValue() : 0).sum();
            summary.put("today_sales_total", todayTotal);

            // Month sales
            LocalDate monthStart = date.withDayOfMonth(1);
            LocalDate monthEnd = date.withDayOfMonth(date.lengthOfMonth());
            String monthStartStr = monthStart.atStartOfDay().format(DB_DATE_FMT);
            String monthEndStr = monthEnd.atTime(23, 59, 59).format(DB_DATE_FMT);

            List<QueryDocumentSnapshot> monthSales = db.collection("sales")
                    .whereGreaterThanOrEqualTo("sale_date", monthStartStr)
                    .whereLessThanOrEqualTo("sale_date", monthEndStr)
                    .whereEqualTo("status", "COMPLETED")
                    .get().get().getDocuments();

            long monthTotal = monthSales.stream().mapToLong(doc -> doc.getDouble("total_amount") != null ? doc.getDouble("total_amount").longValue() : 0).sum();
            summary.put("month_revenue", monthTotal);

            // Products
            List<QueryDocumentSnapshot> products = db.collection("products").get().get().getDocuments();
            int activeProducts = 0;
            int lowStock = 0;
            for (QueryDocumentSnapshot p : products) {
                Object activeObj = p.get("is_active");
                boolean isActive = false;
                if (activeObj instanceof Number) isActive = ((Number) activeObj).intValue() == 1;
                else if (activeObj instanceof Boolean) isActive = (Boolean) activeObj;
                
                if (isActive) {
                    activeProducts++;
                    long stock = p.getLong("stock") != null ? p.getLong("stock") : 0;
                    if (stock <= 0) lowStock++;
                }
            }
            summary.put("total_products", activeProducts);
            summary.put("low_stock_count", lowStock);

        } catch (Exception e) {
            logger.error("Error getting dashboard summary", e);
        }
        return summary;
    }

    public List<ReportRow> getStockMovementReport(Long productId, LocalDate startDate, LocalDate endDate) {
        List<ReportRow> rows = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            String startStr = startDate.atStartOfDay().format(DB_DATE_FMT);
            String endStr = endDate.atTime(23, 59, 59).format(DB_DATE_FMT);

            List<QueryDocumentSnapshot> movements = db.collection("stock_movements")
                    .whereEqualTo("product_id", productId)
                    .get().get().getDocuments();

            Map<Long, String> users = new HashMap<>();
            List<QueryDocumentSnapshot> usersDocs = db.collection("users").get().get().getDocuments();
            for (QueryDocumentSnapshot u : usersDocs) {
                users.put(u.getLong("id"), u.getString("full_name"));
            }

            for (QueryDocumentSnapshot doc : movements) {
                String createdAt = doc.getString("created_at");
                if (createdAt != null && createdAt.compareTo(startStr) >= 0 && createdAt.compareTo(endStr) <= 0) {
                    ReportRow row = new ReportRow();
                    row.put("id", doc.getLong("id"));
                    row.put("movement_type", doc.getString("movement_type"));
                    row.put("reference_type", doc.getString("reference_type"));
                    row.put("reference_id", doc.getLong("reference_id"));
                    row.put("quantity_change", doc.getLong("quantity_change") != null ? doc.getLong("quantity_change").intValue() : 0);
                    row.put("stock_before", doc.getLong("stock_before") != null ? doc.getLong("stock_before").intValue() : 0);
                    row.put("stock_after", doc.getLong("stock_after") != null ? doc.getLong("stock_after").intValue() : 0);
                    row.put("notes", doc.getString("notes"));
                    row.put("created_at", createdAt);
                    
                    Long createdBy = doc.getLong("created_by");
                    row.put("created_by_name", users.getOrDefault(createdBy, "Unknown"));
                    rows.add(row);
                }
            }

            rows.sort((a, b) -> b.getString("created_at").compareTo(a.getString("created_at")));

        } catch (Exception e) {
            logger.error("Error getting stock movement report", e);
        }
        return rows;
    }
}


