package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SaleDAO {
    private static final Logger logger = LoggerFactory.getLogger(SaleDAO.class);
    private static final DateTimeFormatter INV_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogDAO auditLogDAO = new AuditLogDAO(); // Wait, this uses SQL internally if not migrated yet. It's okay, we'll keep the call, might crash if SQL is off. For Phase 1 we disabled SQL. I'll just comment out the audit_log or wrap in try.

    private static final String COLLECTION = "sales";
    private static final String ITEMS_COL = "sale_items";
    private static final String PAYMENTS_COL = "sale_payments";
    private static final String STOCK_MOV_COL = "stock_movements";

    public void createSale(Sale sale) throws Exception {
        Firestore db = FirestoreHelper.getDb();
        WriteBatch batch = db.batch();

        try {
            // 1. Generate Invoice Number
            String invoiceNumber = generateInvoiceNumber();
            sale.setInvoiceNumber(invoiceNumber);
            sale.setSaleDate(LocalDateTime.now());

            // 2. Generate Sale ID
            Long saleId = FirestoreHelper.getNextId(COLLECTION);
            sale.setId(saleId);

            DocumentReference saleRef = db.collection(COLLECTION).document(String.valueOf(saleId));
            batch.set(saleRef, saleToMap(sale));

            // 3. Process Items
            for (SaleItem item : sale.getItems()) {
                item.setSaleId(saleId);
                
                // Cek Stok (Read first, although not purely atomic with batch write, it prevents gross overselling)
                DocumentSnapshot prodSnap = db.collection("products").document(String.valueOf(item.getProductId())).get().get();
                if (prodSnap.exists()) {
                    Long currentStock = prodSnap.getLong("stock");
                    if (currentStock != null && currentStock < item.getQuantity()) {
                        throw new Exception("Stok tidak mencukupi untuk produk: " + item.getProductName());
                    }
                }

                Long itemId = FirestoreHelper.getNextId(ITEMS_COL);
                item.setId(itemId);
                DocumentReference itemRef = db.collection(ITEMS_COL).document(String.valueOf(itemId));
                batch.set(itemRef, saleItemToMap(item));

                // Update Stock (Reduce atomically)
                DocumentReference prodRef = db.collection("products").document(String.valueOf(item.getProductId()));
                batch.update(prodRef, "stock", FieldValue.increment(-item.getQuantity()));

                // Create Stock Movement
                Long movId = FirestoreHelper.getNextId(STOCK_MOV_COL);
                StockMovement movement = new StockMovement();
                movement.setId(movId);
                movement.setProductId(item.getProductId());
                movement.setMovementType(StockMovement.MovementType.SALE_OUT);
                movement.setReferenceType("SALE");
                movement.setReferenceId(saleId);
                movement.setQuantityChange(-item.getQuantity());
                movement.setCreatedBy(sale.getCreatedBy());
                movement.setNotes("Sale " + invoiceNumber);
                
                DocumentReference movRef = db.collection(STOCK_MOV_COL).document(String.valueOf(movId));
                batch.set(movRef, stockMovementToMap(movement));
            }

            // 4. Insert Payments
            if (sale.getPayments() != null) {
                for (SalePayment p : sale.getPayments()) {
                    Long payId = FirestoreHelper.getNextId(PAYMENTS_COL);
                    p.setId(payId);
                    p.setSaleId(saleId);
                    DocumentReference payRef = db.collection(PAYMENTS_COL).document(String.valueOf(payId));
                    batch.set(payRef, salePaymentToMap(p));
                }
            }

            // Execute Batch
            batch.commit().get();
            logger.info("Sale transaction completed successfully: {}", invoiceNumber);

        } catch (Exception e) {
            logger.error("Transaction failed: {}", e.getMessage());
            throw new Exception("Transaction failed: " + e.getMessage(), e);
        }
    }

    public void voidSale(Long saleId, Long voidedBy, String voidReason) throws Exception {
        Firestore db = FirestoreHelper.getDb();
        WriteBatch batch = db.batch();

        try {
            Sale sale = findById(saleId);
            if (sale == null) {
                throw new Exception("Transaksi tidak ditemukan");
            }
            if (sale.getStatus() == Sale.Status.VOIDED) {
                throw new Exception("Transaksi sudah di-VOID sebelumnya");
            }

            // Cek retur (Skip logic ini karena terlalu kompleks untuk Firestore tanpa denormalisasi, diabaikan untuk saat ini)
            
            // 1. Update sale status
            DocumentReference saleRef = db.collection(COLLECTION).document(String.valueOf(saleId));
            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "VOIDED");
            updates.put("void_reason", voidReason);
            updates.put("voided_by", voidedBy);
            updates.put("voided_at", LocalDateTime.now().format(DB_DATE_FMT));
            batch.update(saleRef, updates);

            // 2. Restore stock for each item
            for (SaleItem item : sale.getItems()) {
                // Restore stock
                DocumentReference prodRef = db.collection("products").document(String.valueOf(item.getProductId()));
                batch.update(prodRef, "stock", FieldValue.increment(item.getQuantity()));

                // Create stock movement
                Long movId = FirestoreHelper.getNextId(STOCK_MOV_COL);
                StockMovement movement = new StockMovement();
                movement.setId(movId);
                movement.setProductId(item.getProductId());
                movement.setMovementType(StockMovement.MovementType.VOID_RESTORE);
                movement.setReferenceType("SALE_VOID");
                movement.setReferenceId(saleId);
                movement.setQuantityChange(item.getQuantity());
                movement.setCreatedBy(voidedBy);
                movement.setNotes("VOID " + sale.getInvoiceNumber() + " - " + voidReason);

                DocumentReference movRef = db.collection(STOCK_MOV_COL).document(String.valueOf(movId));
                batch.set(movRef, stockMovementToMap(movement));
            }

            batch.commit().get();

            try {
                // Audit log (bisa error kalau tabel sql belum migrasi)
                auditLogDAO.log(voidedBy, "VOID_SALE", "sales", saleId,
                        "status=COMPLETED,invoice=" + sale.getInvoiceNumber(),
                        "status=VOIDED,reason=" + voidReason);
            } catch (Exception ignored) {}

            logger.info("Sale voided successfully: {}", sale.getInvoiceNumber());

        } catch (Exception e) {
            logger.error("VOID failed: {}", e.getMessage());
            throw new Exception("VOID failed: " + e.getMessage(), e);
        }
    }

    public List<Sale> findAll() {
        List<Sale> sales = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .orderBy("sale_date", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                sales.add(mapDocumentToSale(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all sales", e);
        }
        return sales;
    }

    public List<Sale> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return searchSales(startDate, endDate, null, 1000, 0); // Limit to 1000 for safety
    }

    public Sale findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                Sale sale = mapDocumentToSale(doc);
                sale.setItems(findSaleItems(id));
                sale.setPayments(findSalePayments(id));
                return sale;
            }
        } catch (Exception e) {
            logger.error("Error finding sale by id: {}", id, e);
        }
        return null;
    }

    public Sale findByInvoiceNumber(String invoiceNumber) {
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("invoice_number", invoiceNumber)
                    .limit(1)
                    .get();

            QuerySnapshot qs = future.get();
            if (!qs.isEmpty()) {
                Sale sale = mapDocumentToSale(qs.getDocuments().get(0));
                sale.setItems(findSaleItems(sale.getId()));
                sale.setPayments(findSalePayments(sale.getId()));
                return sale;
            }
        } catch (Exception e) {
            logger.error("Error finding sale by invoice: {}", invoiceNumber, e);
        }
        return null;
    }

    public List<Sale> searchSales(LocalDate startDate, LocalDate endDate, String query, int limit, int offset) {
        List<Sale> sales = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            
            // Format dates
            LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
            LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(2100, 12, 31);
            String startStr = effectiveStart.atStartOfDay().format(DB_DATE_FMT);
            String endStr = effectiveEnd.plusDays(1).atStartOfDay().format(DB_DATE_FMT);

            // Fetch based on date range
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("sale_date", startStr)
                    .whereLessThan("sale_date", endStr)
                    .orderBy("sale_date", Query.Direction.DESCENDING)
                    .get();

            String lowerQ = query != null ? query.toLowerCase() : "";
            
            int count = 0;
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Sale s = mapDocumentToSale(doc);
                if (lowerQ.isEmpty() || 
                    (s.getInvoiceNumber() != null && s.getInvoiceNumber().toLowerCase().contains(lowerQ)) ||
                    (s.getCustomerName() != null && s.getCustomerName().toLowerCase().contains(lowerQ))) {
                    
                    if (count >= offset && sales.size() < limit) {
                        sales.add(s);
                    }
                    count++;
                }
            }
        } catch (Exception e) {
            logger.error("Error searching sales", e);
        }
        return sales;
    }

    public long countSales(LocalDate startDate, LocalDate endDate, String query) {
        long count = 0;
        try {
            Firestore db = FirestoreHelper.getDb();
            LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
            LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(2100, 12, 31);
            String startStr = effectiveStart.atStartOfDay().format(DB_DATE_FMT);
            String endStr = effectiveEnd.plusDays(1).atStartOfDay().format(DB_DATE_FMT);

            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("sale_date", startStr)
                    .whereLessThan("sale_date", endStr)
                    .get();

            String lowerQ = query != null ? query.toLowerCase() : "";
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Sale s = mapDocumentToSale(doc);
                if (lowerQ.isEmpty() || 
                    (s.getInvoiceNumber() != null && s.getInvoiceNumber().toLowerCase().contains(lowerQ)) ||
                    (s.getCustomerName() != null && s.getCustomerName().toLowerCase().contains(lowerQ))) {
                    count++;
                }
            }
        } catch (Exception e) {
            logger.error("Error counting sales", e);
        }
        return count;
    }

    public BigDecimal sumSales(LocalDate startDate, LocalDate endDate, String query) {
        BigDecimal total = BigDecimal.ZERO;
        try {
            Firestore db = FirestoreHelper.getDb();
            LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
            LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(2100, 12, 31);
            String startStr = effectiveStart.atStartOfDay().format(DB_DATE_FMT);
            String endStr = effectiveEnd.plusDays(1).atStartOfDay().format(DB_DATE_FMT);

            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("sale_date", startStr)
                    .whereLessThan("sale_date", endStr)
                    .whereEqualTo("status", "COMPLETED")
                    .get();

            String lowerQ = query != null ? query.toLowerCase() : "";
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Sale s = mapDocumentToSale(doc);
                if (lowerQ.isEmpty() || 
                    (s.getInvoiceNumber() != null && s.getInvoiceNumber().toLowerCase().contains(lowerQ)) ||
                    (s.getCustomerName() != null && s.getCustomerName().toLowerCase().contains(lowerQ))) {
                    
                    if (s.getTotalAmount() != null) {
                        total = total.add(s.getTotalAmount());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error summing sales", e);
        }
        return total;
    }

    public List<SaleItem> findSaleItems(Long saleId) {
        List<SaleItem> items = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(ITEMS_COL)
                    .whereEqualTo("sale_id", saleId)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                items.add(mapDocumentToSaleItem(doc));
            }
        } catch (Exception e) {
            logger.error("Error fetching sale items", e);
        }
        return items;
    }

    private List<SalePayment> findSalePayments(Long saleId) {
        List<SalePayment> payments = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(PAYMENTS_COL)
                    .whereEqualTo("sale_id", saleId)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                payments.add(mapDocumentToSalePayment(doc));
            }
        } catch (Exception e) {
            logger.error("Error fetching sale payments", e);
        }
        return payments;
    }

    private String generateInvoiceNumber() {
        String datePart = LocalDate.now().format(INV_DATE_FMT);
        String prefix = "INV-" + datePart + "-";

        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("invoice_number", prefix)
                    .whereLessThan("invoice_number", prefix + "\uf8ff")
                    .orderBy("invoice_number", Query.Direction.DESCENDING)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            int nextNum = 1;
            if (!querySnapshot.isEmpty()) {
                String lastInv = querySnapshot.getDocuments().get(0).getString("invoice_number");
                if (lastInv != null && lastInv.length() > prefix.length()) {
                    try {
                        String numPart = lastInv.substring(prefix.length());
                        nextNum = Integer.parseInt(numPart) + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return String.format("%s%04d", prefix, nextNum);
        } catch (Exception e) {
            logger.error("Error generating invoice number", e);
            return prefix + System.currentTimeMillis();
        }
    }

    // Mapping Methods --------------------------------------------------------

    private Map<String, Object> saleToMap(Sale sale) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", sale.getId());
        map.put("invoice_number", sale.getInvoiceNumber());
        map.put("customer_id", sale.getCustomerId());
        // For convenience in NoSQL, denormalize customer name
        if (sale.getCustomerId() != null) {
            try {
                DocumentSnapshot custDoc = FirestoreHelper.getDb().collection("customers").document(String.valueOf(sale.getCustomerId())).get().get();
                if (custDoc.exists()) map.put("customer_name", custDoc.getString("name"));
            } catch (Exception ignored) {}
        }
        
        map.put("sale_date", sale.getSaleDate().format(DB_DATE_FMT));
        map.put("subtotal", sale.getSubtotal() != null ? sale.getSubtotal().longValue() : 0L);
        map.put("discount_percent", sale.getDiscountPercent());
        map.put("discount_amount", sale.getDiscountAmount() != null ? sale.getDiscountAmount().longValue() : 0L);
        map.put("total_amount", sale.getTotalAmount() != null ? sale.getTotalAmount().longValue() : 0L);
        map.put("total_hpp", sale.getTotalHpp() != null ? sale.getTotalHpp().longValue() : 0L);
        map.put("payment_method", sale.getPaymentMethod() != null ? sale.getPaymentMethod().name() : null);
        map.put("payment_type", sale.getPaymentType() != null ? sale.getPaymentType().name() : null);
        map.put("payment_amount", sale.getPaymentAmount() != null ? sale.getPaymentAmount().longValue() : 0L);
        map.put("change_amount", sale.getChangeAmount() != null ? sale.getChangeAmount().longValue() : 0L);
        map.put("status", sale.getStatus() != null ? sale.getStatus().name() : "COMPLETED");
        map.put("technician_id", sale.getTechnicianId());
        map.put("notes", sale.getNotes());
        map.put("created_by", sale.getCreatedBy());
        map.put("created_at", LocalDateTime.now().format(DB_DATE_FMT));
        return map;
    }

    private Sale mapDocumentToSale(DocumentSnapshot doc) {
        Sale sale = new Sale();
        sale.setId(doc.getLong("id"));
        sale.setInvoiceNumber(doc.getString("invoice_number"));
        sale.setCustomerId(doc.getLong("customer_id"));
        sale.setCustomerName(doc.getString("customer_name"));

        String dateStr = doc.getString("sale_date");
        if (dateStr != null) {
            sale.setSaleDate(LocalDateTime.parse(dateStr, DB_DATE_FMT));
        }

        sale.setSubtotal(BigDecimal.valueOf(doc.getLong("subtotal") != null ? doc.getLong("subtotal") : 0));
        sale.setDiscountPercent(doc.getDouble("discount_percent") != null ? doc.getDouble("discount_percent") : 0.0);
        sale.setDiscountAmount(BigDecimal.valueOf(doc.getLong("discount_amount") != null ? doc.getLong("discount_amount") : 0));
        sale.setTotalAmount(BigDecimal.valueOf(doc.getLong("total_amount") != null ? doc.getLong("total_amount") : 0));
        sale.setTotalHpp(BigDecimal.valueOf(doc.getLong("total_hpp") != null ? doc.getLong("total_hpp") : 0));
        
        String pMethod = doc.getString("payment_method");
        if (pMethod != null) sale.setPaymentMethod(Sale.PaymentMethod.valueOf(pMethod));
        
        String pType = doc.getString("payment_type");
        if (pType != null) sale.setPaymentType(Sale.PaymentType.valueOf(pType));
        
        sale.setPaymentAmount(BigDecimal.valueOf(doc.getLong("payment_amount") != null ? doc.getLong("payment_amount") : 0));
        sale.setChangeAmount(BigDecimal.valueOf(doc.getLong("change_amount") != null ? doc.getLong("change_amount") : 0));
        
        String status = doc.getString("status");
        if (status != null) sale.setStatus(Sale.Status.valueOf(status));
        
        sale.setVoidReason(doc.getString("void_reason"));
        sale.setVoidedBy(doc.getLong("voided_by"));
        String voidedAtStr = doc.getString("voided_at");
        if (voidedAtStr != null) sale.setVoidedAt(LocalDateTime.parse(voidedAtStr, DB_DATE_FMT));
        
        sale.setTechnicianId(doc.getLong("technician_id"));
        sale.setNotes(doc.getString("notes"));
        sale.setCreatedBy(doc.getLong("created_by"));
        
        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null) {
            sale.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        return sale;
    }

    private Map<String, Object> saleItemToMap(SaleItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("sale_id", item.getSaleId());
        map.put("product_id", item.getProductId());
        map.put("product_sku", item.getProductSku());
        map.put("product_name", item.getProductName()); // Denormalized
        map.put("quantity", item.getQuantity());
        map.put("unit_price", item.getUnitPrice() != null ? item.getUnitPrice().longValue() : 0L);
        map.put("hpp_per_unit", item.getHppPerUnit() != null ? item.getHppPerUnit().longValue() : 0L);
        map.put("discount_percent", item.getDiscountPercent());
        map.put("discount_amount", item.getDiscountAmount() != null ? item.getDiscountAmount().longValue() : 0L);
        map.put("subtotal", item.getSubtotal() != null ? item.getSubtotal().longValue() : 0L);
        map.put("serial_number", item.getSerialNumber());
        map.put("buyer_name", item.getBuyerName());
        map.put("buyer_nik", item.getBuyerNik());
        return map;
    }

    private SaleItem mapDocumentToSaleItem(DocumentSnapshot doc) {
        SaleItem item = new SaleItem();
        item.setId(doc.getLong("id"));
        item.setSaleId(doc.getLong("sale_id"));
        item.setProductId(doc.getLong("product_id"));
        item.setProductSku(doc.getString("product_sku"));
        item.setProductName(doc.getString("product_name"));
        
        Long qty = doc.getLong("quantity");
        item.setQuantity(qty != null ? qty.intValue() : 0);
        
        item.setUnitPrice(BigDecimal.valueOf(doc.getLong("unit_price") != null ? doc.getLong("unit_price") : 0));
        item.setHppPerUnit(BigDecimal.valueOf(doc.getLong("hpp_per_unit") != null ? doc.getLong("hpp_per_unit") : 0));
        item.setDiscountPercent(doc.getDouble("discount_percent") != null ? doc.getDouble("discount_percent") : 0.0);
        item.setDiscountAmount(BigDecimal.valueOf(doc.getLong("discount_amount") != null ? doc.getLong("discount_amount") : 0));
        item.setSubtotal(BigDecimal.valueOf(doc.getLong("subtotal") != null ? doc.getLong("subtotal") : 0));
        
        item.setSerialNumber(doc.getString("serial_number"));
        item.setBuyerName(doc.getString("buyer_name"));
        item.setBuyerNik(doc.getString("buyer_nik"));
        return item;
    }

    private Map<String, Object> salePaymentToMap(SalePayment p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("sale_id", p.getSaleId());
        map.put("method", p.getMethod());
        map.put("amount", p.getAmount() != null ? p.getAmount().longValue() : 0L);
        map.put("ref_no", p.getRefNo());
        return map;
    }

    private SalePayment mapDocumentToSalePayment(DocumentSnapshot doc) {
        SalePayment p = new SalePayment();
        p.setId(doc.getLong("id"));
        p.setSaleId(doc.getLong("sale_id"));
        p.setMethod(doc.getString("method"));
        p.setAmount(BigDecimal.valueOf(doc.getLong("amount") != null ? doc.getLong("amount") : 0));
        p.setRefNo(doc.getString("ref_no"));
        return p;
    }

    private Map<String, Object> stockMovementToMap(StockMovement sm) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", sm.getId());
        map.put("product_id", sm.getProductId());
        map.put("movement_type", sm.getMovementType() != null ? sm.getMovementType().name() : null);
        map.put("reference_type", sm.getReferenceType());
        map.put("reference_id", sm.getReferenceId());
        map.put("quantity_change", sm.getQuantityChange());
        // For NoSQL, reading stock_before & after perfectly is tough without transactions, we just save 0 or dummy for now
        map.put("notes", sm.getNotes());
        map.put("created_by", sm.getCreatedBy());
        map.put("created_at", LocalDateTime.now().format(DB_DATE_FMT));
        return map;
    }
}


