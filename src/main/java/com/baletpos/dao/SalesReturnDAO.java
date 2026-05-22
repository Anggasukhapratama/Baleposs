package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.SalesReturn;
import com.baletpos.model.SalesReturnItem;
import com.baletpos.model.StockMovement;
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

public class SalesReturnDAO {
    private static final Logger logger = LoggerFactory.getLogger(SalesReturnDAO.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String COLLECTION = "sales_returns";
    private static final String ITEMS_COL = "sales_return_items";
    private static final String STOCK_MOV_COL = "stock_movements";

    public void createReturn(SalesReturn salesReturn) throws Exception {
        Firestore db = FirestoreHelper.getDb();
        WriteBatch batch = db.batch();

        try {
            // 1. Generate Return Number
            String returnNo = generateReturnNumber();
            salesReturn.setReturnNo(returnNo);

            // 2. Generate Return ID & Insert Header
            Long returnId = FirestoreHelper.getNextId(COLLECTION);
            salesReturn.setId(returnId);

            DocumentReference retRef = db.collection(COLLECTION).document(String.valueOf(returnId));
            batch.set(retRef, returnToMap(salesReturn));

            // 3. Process Items
            for (SalesReturnItem item : salesReturn.getItems()) {
                item.setSalesReturnId(returnId);
                
                Long itemId = FirestoreHelper.getNextId(ITEMS_COL);
                item.setId(itemId);

                DocumentReference itemRef = db.collection(ITEMS_COL).document(String.valueOf(itemId));
                batch.set(itemRef, returnItemToMap(item));

                // Update Stock (ADD)
                DocumentReference prodRef = db.collection("products").document(String.valueOf(item.getProductId()));
                batch.update(prodRef, "stock", FieldValue.increment(item.getQtyReturn()));

                // Insert Movement
                Long movId = FirestoreHelper.getNextId(STOCK_MOV_COL);
                StockMovement mv = new StockMovement();
                mv.setId(movId);
                mv.setProductId(item.getProductId());
                mv.setMovementType(StockMovement.MovementType.SALE_RETURN);
                mv.setReferenceType("SALES_RETURN");
                mv.setReferenceId(returnId);
                mv.setQuantityChange(item.getQtyReturn()); // Positive
                mv.setCreatedBy(salesReturn.getUserId());
                mv.setNotes("Return " + returnNo);

                DocumentReference movRef = db.collection(STOCK_MOV_COL).document(String.valueOf(movId));
                batch.set(movRef, stockMovementToMap(mv));
            }

            batch.commit().get();
            logger.info("Sales Return created successfully: {}", returnNo);

        } catch (Exception e) {
            logger.error("Failed to create sales return", e);
            throw new Exception("Failed to create sales return: " + e.getMessage(), e);
        }
    }

    private String generateReturnNumber() {
        String datePart = LocalDate.now().format(NO_FMT);
        String prefix = "RT-" + datePart + "-";

        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("return_number", prefix)
                    .whereLessThan("return_number", prefix + "\uf8ff")
                    .orderBy("return_number", Query.Direction.DESCENDING)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            int nextNum = 1;
            if (!querySnapshot.isEmpty()) {
                String lastNo = querySnapshot.getDocuments().get(0).getString("return_number");
                if (lastNo != null && lastNo.length() > prefix.length()) {
                    try {
                        String numPart = lastNo.substring(prefix.length());
                        nextNum = Integer.parseInt(numPart) + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return String.format("%s%04d", prefix, nextNum);
        } catch (Exception e) {
            logger.error("Error generating return number", e);
            return prefix + System.currentTimeMillis();
        }
    }

    public int getReturnedQuantity(Long saleId, Long productId) {
        try {
            Firestore db = FirestoreHelper.getDb();
            // This queries flat sales_return_items collection.
            // Since we don't store saleId in return_items (only sales_return_id), we need a sub-query or denormalize.
            // Wait, sale_id is in header. It's better to find headers with saleId first.
            ApiFuture<QuerySnapshot> retFuture = db.collection(COLLECTION)
                    .whereEqualTo("sale_id", saleId)
                    .get();

            List<Long> returnIds = new ArrayList<>();
            for (QueryDocumentSnapshot doc : retFuture.get().getDocuments()) {
                returnIds.add(doc.getLong("id"));
            }

            if (returnIds.isEmpty()) return 0;

            int totalQty = 0;
            // Get all items matching these returnIds
            for (Long rId : returnIds) {
                ApiFuture<QuerySnapshot> itemsFuture = db.collection(ITEMS_COL)
                        .whereEqualTo("sales_return_id", rId)
                        .whereEqualTo("product_id", productId)
                        .get();
                for (QueryDocumentSnapshot doc : itemsFuture.get().getDocuments()) {
                    Long qty = doc.getLong("quantity");
                    if (qty != null) {
                        totalQty += qty.intValue();
                    }
                }
            }
            return totalQty;

        } catch (Exception e) {
            logger.error("Failed to fetch returned quantity", e);
            return 0;
        }
    }

    public List<SalesReturn> findAll() {
        List<SalesReturn> list = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                list.add(mapDocumentToReturn(doc));
            }
        } catch (Exception e) {
            logger.error("Failed to fetch sales returns", e);
        }
        return list;
    }

    public SalesReturn findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                SalesReturn ret = mapDocumentToReturn(doc);
                ret.setItems(findItemsByReturnId(ret.getId()));
                return ret;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch sales return by id", e);
        }
        return null;
    }

    public List<SalesReturnItem> findItemsByReturnId(Long returnId) {
        List<SalesReturnItem> items = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(ITEMS_COL)
                    .whereEqualTo("sales_return_id", returnId)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                SalesReturnItem item = new SalesReturnItem();
                item.setId(doc.getLong("id"));
                item.setSalesReturnId(doc.getLong("sales_return_id"));
                item.setSaleItemId(doc.getLong("sale_item_id"));
                item.setProductId(doc.getLong("product_id"));
                
                Long qty = doc.getLong("quantity");
                item.setQtyReturn(qty != null ? qty.intValue() : 0);
                
                Double unitPrice = doc.getDouble("unit_price");
                item.setUnitPrice(unitPrice != null ? BigDecimal.valueOf(unitPrice) : BigDecimal.ZERO);
                
                Double hpp = doc.getDouble("hpp_per_unit");
                item.setSnapshotHpp(hpp != null ? BigDecimal.valueOf(hpp) : BigDecimal.ZERO);
                
                Double subtotal = doc.getDouble("subtotal");
                item.setLineTotal(subtotal != null ? BigDecimal.valueOf(subtotal) : BigDecimal.ZERO);
                
                item.setProductSku(doc.getString("product_sku"));
                item.setProductName(doc.getString("product_name"));
                
                items.add(item);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch return items", e);
        }
        return items;
    }

    // Mapping logic -----------------------------------------------------------

    private Map<String, Object> returnToMap(SalesReturn ret) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ret.getId());
        map.put("return_number", ret.getReturnNo());
        map.put("sale_id", ret.getSaleId());
        
        // Denormalize sale invoice
        if (ret.getSaleId() != null) {
            try {
                DocumentSnapshot saleDoc = FirestoreHelper.getDb().collection("sales").document(String.valueOf(ret.getSaleId())).get().get();
                if (saleDoc.exists()) {
                    map.put("invoice_number", saleDoc.getString("invoice_number"));
                }
            } catch (Exception ignored) {}
        }
        
        map.put("created_at", LocalDateTime.now().format(DB_DATE_FMT));
        map.put("reason", ret.getNotes());
        map.put("total_amount", ret.getTotalAmount() != null ? ret.getTotalAmount().doubleValue() : 0.0);
        map.put("status", "COMPLETED");
        map.put("created_by", ret.getUserId());
        
        if (ret.getUserId() != null) {
            try {
                DocumentSnapshot userDoc = FirestoreHelper.getDb().collection("users").document(String.valueOf(ret.getUserId())).get().get();
                if (userDoc.exists()) {
                    map.put("user_name", userDoc.getString("full_name"));
                }
            } catch (Exception ignored) {}
        }
        
        return map;
    }

    private Map<String, Object> returnItemToMap(SalesReturnItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("sales_return_id", item.getSalesReturnId());
        map.put("sale_item_id", item.getSaleItemId());
        map.put("product_id", item.getProductId());
        
        // Denormalize product
        if (item.getProductId() != null) {
            try {
                DocumentSnapshot prodDoc = FirestoreHelper.getDb().collection("products").document(String.valueOf(item.getProductId())).get().get();
                if (prodDoc.exists()) {
                    map.put("product_sku", prodDoc.getString("sku"));
                    map.put("product_name", prodDoc.getString("name"));
                }
            } catch (Exception ignored) {}
        }
        
        map.put("quantity", item.getQtyReturn());
        map.put("unit_price", item.getUnitPrice() != null ? item.getUnitPrice().doubleValue() : 0.0);
        map.put("hpp_per_unit", item.getSnapshotHpp() != null ? item.getSnapshotHpp().doubleValue() : 0.0);
        map.put("subtotal", item.getLineTotal() != null ? item.getLineTotal().doubleValue() : 0.0);
        
        return map;
    }

    private SalesReturn mapDocumentToReturn(DocumentSnapshot doc) {
        SalesReturn ret = new SalesReturn();
        ret.setId(doc.getLong("id"));
        ret.setReturnNo(doc.getString("return_number"));
        ret.setSaleId(doc.getLong("sale_id"));
        
        String createdAt = doc.getString("created_at");
        if (createdAt != null) {
            ret.setCreatedAt(LocalDateTime.parse(createdAt, DB_DATE_FMT));
        }
        
        ret.setNotes(doc.getString("reason"));
        
        Double total = doc.getDouble("total_amount");
        ret.setTotalAmount(total != null ? BigDecimal.valueOf(total) : BigDecimal.ZERO);
        
        ret.setSaleInvoiceNumber(doc.getString("invoice_number"));
        ret.setUserId(doc.getLong("created_by"));
        ret.setCreatedByName(doc.getString("user_name"));
        
        return ret;
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


