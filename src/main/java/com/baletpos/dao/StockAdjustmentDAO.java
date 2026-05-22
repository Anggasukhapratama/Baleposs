package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.StockAdjustment;
import com.baletpos.model.StockAdjustmentItem;
import com.baletpos.model.StockMovement;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockAdjustmentDAO {
    private static final Logger logger = LoggerFactory.getLogger(StockAdjustmentDAO.class);
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String COLLECTION = "stock_adjustments";
    private static final String STOCK_MOV_COL = "stock_movements";

    public void createAdjustment(StockAdjustment adj) throws Exception {
        Firestore db = FirestoreHelper.getDb();
        WriteBatch batch = db.batch();

        try {
            String adjNo = generateAdjNumber();
            adj.setAdjNo(adjNo);

            if (adj.getItems() == null || adj.getItems().isEmpty()) {
                throw new Exception("Item mutasi tidak ditemukan");
            }

            StockAdjustmentItem item = adj.getItems().get(0);
            adj.setProductId(item.getProductId());
            adj.setQuantityChange(item.getQtyDelta());
            adj.setNotes(item.getNote());

            Long adjId = FirestoreHelper.getNextId(COLLECTION);
            adj.setId(adjId);

            DocumentReference adjRef = db.collection(COLLECTION).document(String.valueOf(adjId));
            batch.set(adjRef, adjustmentToMap(adj));

            // Update Stock (Delta can be negative)
            DocumentReference prodRef = db.collection("products").document(String.valueOf(item.getProductId()));
            batch.update(prodRef, "stock", FieldValue.increment(item.getQtyDelta()));

            // Insert Movement
            Long movId = FirestoreHelper.getNextId(STOCK_MOV_COL);
            StockMovement mv = new StockMovement();
            mv.setId(movId);
            mv.setProductId(item.getProductId());
            mv.setMovementType(StockMovement.MovementType.ADJUSTMENT);
            mv.setReferenceType("STOCK_ADJUSTMENT");
            mv.setReferenceId(adjId);
            mv.setQuantityChange(item.getQtyDelta());
            mv.setCreatedBy(adj.getUserId());
            mv.setNotes("Adj " + adjNo + ": " + item.getNote());

            DocumentReference movRef = db.collection(STOCK_MOV_COL).document(String.valueOf(movId));
            batch.set(movRef, stockMovementToMap(mv));

            batch.commit().get();
            logger.info("Stock Adjustment created: {}", adjNo);

        } catch (Exception e) {
            logger.error("Failed to create adjustment", e);
            throw new Exception("Failed to create adjustment: " + e.getMessage(), e);
        }
    }

    private String generateAdjNumber() {
        String datePart = LocalDate.now().format(NO_FMT);
        String prefix = "ADJ-" + datePart + "-";

        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("adjustment_number", prefix)
                    .whereLessThan("adjustment_number", prefix + "\uf8ff")
                    .orderBy("adjustment_number", Query.Direction.DESCENDING)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            int nextNum = 1;
            if (!querySnapshot.isEmpty()) {
                String lastNo = querySnapshot.getDocuments().get(0).getString("adjustment_number");
                if (lastNo != null && lastNo.length() > prefix.length()) {
                    try {
                        String numPart = lastNo.substring(prefix.length());
                        nextNum = Integer.parseInt(numPart) + 1;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return String.format("%s%04d", prefix, nextNum);
        } catch (Exception e) {
            logger.error("Error generating adj number", e);
            return prefix + System.currentTimeMillis();
        }
    }

    public List<StockAdjustment> findAll() {
        List<StockAdjustment> list = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .orderBy("adjustment_date", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                list.add(mapDocumentToAdjustment(doc));
            }
        } catch (Exception e) {
            logger.error("Failed to fetch stock adjustments", e);
        }
        return list;
    }

    public StockAdjustment findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                StockAdjustment adj = mapDocumentToAdjustment(doc);
                adj.setItems(findItemsByAdjustmentId(adj.getId()));
                return adj;
            }
        } catch (Exception e) {
            logger.error("Failed to fetch stock adjustment by id", e);
        }
        return null;
    }

    public List<StockAdjustmentItem> findItemsByAdjustmentId(Long adjustmentId) {
        List<StockAdjustmentItem> items = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(adjustmentId)).get().get();
            if (doc.exists()) {
                StockAdjustmentItem item = new StockAdjustmentItem();
                item.setId(doc.getLong("id"));
                item.setProductId(doc.getLong("product_id"));
                Long qtyDelta = doc.getLong("quantity_change");
                item.setQtyDelta(qtyDelta != null ? qtyDelta.intValue() : 0);
                item.setNote(doc.getString("notes"));
                item.setProductSku(doc.getString("product_sku"));
                item.setProductName(doc.getString("product_name"));
                items.add(item);
            }
        } catch (Exception e) {
            logger.error("Failed to fetch adjustment items", e);
        }
        return items;
    }

    // Mapping logic -----------------------------------------------------------

    private Map<String, Object> adjustmentToMap(StockAdjustment adj) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", adj.getId());
        map.put("adjustment_number", adj.getAdjNo());
        map.put("product_id", adj.getProductId());

        // Denormalized product info
        if (adj.getProductId() != null) {
            try {
                DocumentSnapshot prodDoc = FirestoreHelper.getDb().collection("products")
                        .document(String.valueOf(adj.getProductId())).get().get();
                if (prodDoc.exists()) {
                    map.put("product_sku", prodDoc.getString("sku"));
                    map.put("product_name", prodDoc.getString("name"));
                }
            } catch (Exception ignored) {
            }
        }

        map.put("adjustment_date", LocalDateTime.now().format(DB_DATE_FMT));
        map.put("quantity_change", adj.getQuantityChange());
        map.put("reason", adj.getReason());
        map.put("notes", adj.getNotes());
        map.put("created_by", adj.getUserId());

        if (adj.getUserId() != null) {
            try {
                DocumentSnapshot userDoc = FirestoreHelper.getDb().collection("users")
                        .document(String.valueOf(adj.getUserId())).get().get();
                if (userDoc.exists()) {
                    map.put("user_name", userDoc.getString("full_name"));
                }
            } catch (Exception ignored) {
            }
        }

        return map;
    }

    private StockAdjustment mapDocumentToAdjustment(DocumentSnapshot doc) {
        StockAdjustment adj = new StockAdjustment();
        adj.setId(doc.getLong("id"));
        adj.setAdjNo(doc.getString("adjustment_number"));
        adj.setProductId(doc.getLong("product_id"));

        Long qtyChange = doc.getLong("quantity_change");
        adj.setQuantityChange(qtyChange != null ? qtyChange.intValue() : 0);

        adj.setReason(doc.getString("reason"));
        adj.setNotes(doc.getString("notes"));

        String createdAt = doc.getString("adjustment_date");
        if (createdAt != null) {
            adj.setCreatedAt(LocalDateTime.parse(createdAt, DB_DATE_FMT));
        }

        adj.setUserId(doc.getLong("created_by"));
        adj.setCreatedByName(doc.getString("user_name"));
        adj.setProductSku(doc.getString("product_sku"));
        adj.setProductName(doc.getString("product_name"));

        return adj;
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
