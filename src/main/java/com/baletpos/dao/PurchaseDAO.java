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

public class PurchaseDAO {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseDAO.class);
    private static final DateTimeFormatter PO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String COLLECTION = "purchases";
    private static final String ITEMS_COL = "purchase_items";
    private static final String STOCK_MOV_COL = "stock_movements";

    public void createPurchase(Purchase purchase, boolean updateProductHpp) throws Exception {
        Firestore db = FirestoreHelper.getDb();
        WriteBatch batch = db.batch();

        try {
            // 1. Generate PO Number
            String poNumber = generatePONumber();
            purchase.setPurchaseNumber(poNumber);
            purchase.setPurchaseDate(LocalDateTime.now());

            // 2. Generate Purchase ID
            Long purchaseId = FirestoreHelper.getNextId(COLLECTION);
            purchase.setId(purchaseId);

            DocumentReference purchaseRef = db.collection(COLLECTION).document(String.valueOf(purchaseId));
            batch.set(purchaseRef, purchaseToMap(purchase));

            // 3. Process Items
            for (PurchaseItem item : purchase.getItems()) {
                item.setPurchaseId(purchaseId);

                // Insert Item
                Long itemId = FirestoreHelper.getNextId(ITEMS_COL);
                item.setId(itemId);
                DocumentReference itemRef = db.collection(ITEMS_COL).document(String.valueOf(itemId));
                batch.set(itemRef, purchaseItemToMap(item));

                // Update Stock (Add)
                DocumentReference prodRef = db.collection("products").document(String.valueOf(item.getProductId()));
                batch.update(prodRef, "stock", FieldValue.increment(item.getQuantity()));

                // Update HPP if requested
                if (updateProductHpp) {
                    DocumentSnapshot prodDoc = prodRef.get().get();
                    if (prodDoc.exists()) {
                        Double marginPercent = prodDoc.getDouble("margin_percent");
                        if (marginPercent == null) marginPercent = 10.0;
                        
                        BigDecimal newHpp = item.getHppPerUnit();
                        BigDecimal marginAmount = newHpp.multiply(BigDecimal.valueOf(marginPercent))
                                .divide(BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
                        BigDecimal sellingPrice = newHpp.add(marginAmount).setScale(0, java.math.RoundingMode.HALF_UP);
                        
                        Map<String, Object> hppUpdates = new HashMap<>();
                        hppUpdates.put("hpp", newHpp.intValue());
                        hppUpdates.put("selling_price", sellingPrice.intValue());
                        hppUpdates.put("updated_at", LocalDateTime.now().format(DB_DATE_FMT));
                        batch.update(prodRef, hppUpdates);
                    }
                }

                // Create Stock Movement
                Long movId = FirestoreHelper.getNextId(STOCK_MOV_COL);
                StockMovement movement = new StockMovement();
                movement.setId(movId);
                movement.setProductId(item.getProductId());
                movement.setMovementType(StockMovement.MovementType.PURCHASE_IN);
                movement.setReferenceType("PURCHASE");
                movement.setReferenceId(purchaseId);
                movement.setQuantityChange(item.getQuantity());
                movement.setCreatedBy(purchase.getCreatedBy());
                movement.setNotes("Pembelian " + poNumber);

                DocumentReference movRef = db.collection(STOCK_MOV_COL).document(String.valueOf(movId));
                batch.set(movRef, stockMovementToMap(movement));
            }

            batch.commit().get();
            logger.info("Purchase transaction completed successfully: {}", poNumber);

        } catch (Exception e) {
            logger.error("Transaction failed: {}", e.getMessage());
            throw new Exception("Transaction failed: " + e.getMessage(), e);
        }
    }

    public List<Purchase> findAll() {
        List<Purchase> purchases = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .orderBy("purchase_date", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                purchases.add(mapDocumentToPurchase(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all purchases", e);
        }
        return purchases;
    }

    public List<Purchase> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Purchase> purchases = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            
            LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
            LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(2100, 12, 31);
            String startStr = effectiveStart.atStartOfDay().format(DB_DATE_FMT);
            String endStr = effectiveEnd.plusDays(1).atStartOfDay().format(DB_DATE_FMT);

            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("purchase_date", startStr)
                    .whereLessThan("purchase_date", endStr)
                    .orderBy("purchase_date", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                purchases.add(mapDocumentToPurchase(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding purchases by date range", e);
        }
        return purchases;
    }

    public Purchase findByIdWithItems(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                Purchase purchase = mapDocumentToPurchase(doc);
                purchase.setItems(findPurchaseItems(id));
                return purchase;
            }
        } catch (Exception e) {
            logger.error("Error finding purchase by id: {}", id, e);
        }
        return null;
    }

    public Purchase findByNumber(String poNumber) {
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("purchase_number", poNumber)
                    .limit(1)
                    .get();

            QuerySnapshot qs = future.get();
            if (!qs.isEmpty()) {
                Purchase purchase = mapDocumentToPurchase(qs.getDocuments().get(0));
                purchase.setItems(findPurchaseItems(purchase.getId()));
                return purchase;
            }
        } catch (Exception e) {
            logger.error("Error finding purchase by number: {}", poNumber, e);
        }
        return null;
    }

    private List<PurchaseItem> findPurchaseItems(Long purchaseId) {
        List<PurchaseItem> items = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(ITEMS_COL)
                    .whereEqualTo("purchase_id", purchaseId)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                items.add(mapDocumentToPurchaseItem(doc));
            }
        } catch (Exception e) {
            logger.error("Error fetching purchase items", e);
        }
        return items;
    }

    private String generatePONumber() {
        String datePart = LocalDate.now().format(PO_DATE_FMT);
        String prefix = "PO-" + datePart + "-";

        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("purchase_number", prefix)
                    .whereLessThan("purchase_number", prefix + "\uf8ff")
                    .orderBy("purchase_number", Query.Direction.DESCENDING)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            int nextNum = 1;
            if (!querySnapshot.isEmpty()) {
                String lastInv = querySnapshot.getDocuments().get(0).getString("purchase_number");
                if (lastInv != null && lastInv.length() > prefix.length()) {
                    try {
                        String numPart = lastInv.substring(prefix.length());
                        nextNum = Integer.parseInt(numPart) + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return String.format("%s%04d", prefix, nextNum);
        } catch (Exception e) {
            logger.error("Error generating po number", e);
            return prefix + System.currentTimeMillis();
        }
    }

    // Mapping Methods --------------------------------------------------------

    private Map<String, Object> purchaseToMap(Purchase p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("purchase_number", p.getPurchaseNumber());
        map.put("supplier_id", p.getSupplierId());
        
        if (p.getSupplierId() != null) {
            try {
                DocumentSnapshot suppDoc = FirestoreHelper.getDb().collection("suppliers").document(String.valueOf(p.getSupplierId())).get().get();
                if (suppDoc.exists()) {
                    map.put("supplier_name", suppDoc.getString("name"));
                    map.put("supplier_code", suppDoc.getString("code"));
                }
            } catch (Exception ignored) {}
        }

        map.put("purchase_date", p.getPurchaseDate().format(DB_DATE_FMT));
        map.put("total_amount", p.getTotalAmount() != null ? p.getTotalAmount().longValue() : 0L);
        map.put("notes", p.getNotes());
        map.put("status", p.getStatus() != null ? p.getStatus().name() : "COMPLETED");
        map.put("created_by", p.getCreatedBy());
        map.put("created_at", LocalDateTime.now().format(DB_DATE_FMT));
        return map;
    }

    private Purchase mapDocumentToPurchase(DocumentSnapshot doc) {
        Purchase p = new Purchase();
        p.setId(doc.getLong("id"));
        p.setPurchaseNumber(doc.getString("purchase_number"));
        p.setSupplierId(doc.getLong("supplier_id"));
        p.setSupplierCode(doc.getString("supplier_code"));
        p.setSupplierName(doc.getString("supplier_name"));

        String dateStr = doc.getString("purchase_date");
        if (dateStr != null) {
            p.setPurchaseDate(LocalDateTime.parse(dateStr, DB_DATE_FMT));
        }

        p.setTotalAmount(BigDecimal.valueOf(doc.getLong("total_amount") != null ? doc.getLong("total_amount") : 0));
        p.setNotes(doc.getString("notes"));
        
        String status = doc.getString("status");
        if (status != null) p.setStatus(Purchase.Status.valueOf(status));
        
        p.setCreatedBy(doc.getLong("created_by"));
        p.setCreatedByName(doc.getString("created_by_name"));

        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null) {
            p.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        return p;
    }

    private Map<String, Object> purchaseItemToMap(PurchaseItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("purchase_id", item.getPurchaseId());
        map.put("product_id", item.getProductId());
        map.put("product_sku", item.getProductSku());
        map.put("product_name", item.getProductName()); // Denormalized
        map.put("quantity", item.getQuantity());
        map.put("hpp_per_unit", item.getHppPerUnit() != null ? item.getHppPerUnit().longValue() : 0L);
        map.put("subtotal", item.getSubtotal() != null ? item.getSubtotal().longValue() : 0L);
        return map;
    }

    private PurchaseItem mapDocumentToPurchaseItem(DocumentSnapshot doc) {
        PurchaseItem item = new PurchaseItem();
        item.setId(doc.getLong("id"));
        item.setPurchaseId(doc.getLong("purchase_id"));
        item.setProductId(doc.getLong("product_id"));
        item.setProductSku(doc.getString("product_sku"));
        item.setProductName(doc.getString("product_name"));
        
        Long qty = doc.getLong("quantity");
        item.setQuantity(qty != null ? qty.intValue() : 0);
        
        item.setHppPerUnit(BigDecimal.valueOf(doc.getLong("hpp_per_unit") != null ? doc.getLong("hpp_per_unit") : 0));
        item.setSubtotal(BigDecimal.valueOf(doc.getLong("subtotal") != null ? doc.getLong("subtotal") : 0));
        
        return item;
    }

    private Map<String, Object> stockMovementToMap(StockMovement sm) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", sm.getId());
        map.put("product_id", sm.getProductId());
        map.put("movement_type", sm.getMovementType() != null ? sm.getMovementType().name() : null);
        map.put("reference_type", sm.getReferenceType());
        map.put("reference_id", sm.getReferenceId());
        map.put("quantity_change", sm.getQuantityChange());
        map.put("notes", sm.getNotes());
        map.put("created_by", sm.getCreatedBy());
        map.put("created_at", LocalDateTime.now().format(DB_DATE_FMT));
        return map;
    }
}


