package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.ExpenseCode;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object untuk Kode Biaya
 */
public class ExpenseCodeDAO {
    private static final Logger logger = LoggerFactory.getLogger(ExpenseCodeDAO.class);
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String COLLECTION = "expense_codes";

    public List<ExpenseCode> findAll() {
        List<ExpenseCode> codes = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("is_active", 1)
                    .orderBy("code")
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                codes.add(mapDocumentToExpenseCode(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all expense codes", e);
        }
        return codes;
    }

    public Optional<ExpenseCode> findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                return Optional.of(mapDocumentToExpenseCode(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding expense code by id: {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<ExpenseCode> findByCode(String code) {
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("code", code)
                    .whereEqualTo("is_active", 1)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            if (!querySnapshot.isEmpty()) {
                return Optional.of(mapDocumentToExpenseCode(querySnapshot.getDocuments().get(0)));
            }
        } catch (Exception e) {
            logger.error("Error finding expense code by code: {}", code, e);
        }
        return Optional.empty();
    }

    public void save(ExpenseCode expenseCode) {
        if (expenseCode.getId() == null) {
            insert(expenseCode);
        } else {
            update(expenseCode);
        }
    }

    private void insert(ExpenseCode expenseCode) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Long newId = FirestoreHelper.getNextId(COLLECTION);
            expenseCode.setId(newId);
            expenseCode.setActive(true);
            expenseCode.setCreatedAt(LocalDateTime.now());

            db.collection(COLLECTION).document(String.valueOf(newId)).set(expenseCodeToMap(expenseCode)).get();
            logger.info("ExpenseCode created: {}", expenseCode.getCode());
        } catch (Exception e) {
            logger.error("Error inserting expense code", e);
            throw new RuntimeException("Gagal menyimpan kode biaya", e);
        }
    }

    private void update(ExpenseCode expenseCode) {
        try {
            Firestore db = FirestoreHelper.getDb();
            db.collection(COLLECTION).document(String.valueOf(expenseCode.getId())).set(expenseCodeToMap(expenseCode)).get();
            logger.info("ExpenseCode updated: {}", expenseCode.getCode());
        } catch (Exception e) {
            logger.error("Error updating expense code", e);
            throw new RuntimeException("Error updating expense code: " + e.getMessage(), e);
        }
    }

    public void delete(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Map<String, Object> update = new HashMap<>();
            update.put("is_active", 0);
            db.collection(COLLECTION).document(String.valueOf(id)).update(update).get();
            logger.info("ExpenseCode deleted (soft): {}", id);
        } catch (Exception e) {
            logger.error("Error deleting expense code", e);
            throw new RuntimeException("Error deleting expense code: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> expenseCodeToMap(ExpenseCode ec) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", ec.getId());
        map.put("code", ec.getCode());
        map.put("name", ec.getName());
        map.put("description", ec.getDescription());
        map.put("is_active", ec.isActive() ? 1 : 0);
        
        if (ec.getCreatedAt() != null) {
            map.put("created_at", ec.getCreatedAt().format(DB_DATE_FMT));
        }
        return map;
    }

    private ExpenseCode mapDocumentToExpenseCode(DocumentSnapshot doc) {
        ExpenseCode ec = new ExpenseCode();
        ec.setId(doc.getLong("id"));
        ec.setCode(doc.getString("code"));
        ec.setName(doc.getString("name"));
        ec.setDescription(doc.getString("description"));
        
        Long isActive = doc.getLong("is_active");
        ec.setActive(isActive != null && isActive == 1);

        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null) {
            ec.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        return ec;
    }
}


