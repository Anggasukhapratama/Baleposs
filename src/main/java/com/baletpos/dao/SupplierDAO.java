package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.Supplier;
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

public class SupplierDAO {
    private static final Logger logger = LoggerFactory.getLogger(SupplierDAO.class);
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String COLLECTION = "suppliers";

    public List<Supplier> findAll() {
        List<Supplier> suppliers = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("is_active", 1)
                    .orderBy("code")
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                suppliers.add(mapDocumentToSupplier(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all suppliers", e);
        }
        return suppliers;
    }

    public List<Supplier> findAllPaged(int limit, int offset, String query) {
        List<Supplier> allFiltered = search(query);
        allFiltered.sort((s1, s2) -> s1.getCode().compareToIgnoreCase(s2.getCode()));
        
        int start = Math.min(offset, allFiltered.size());
        int end = Math.min(start + limit, allFiltered.size());
        return allFiltered.subList(start, end);
    }

    public int countFiltered(String query) {
        return search(query).size();
    }

    public Optional<Supplier> findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                return Optional.of(mapDocumentToSupplier(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding supplier by id: {}", id, e);
        }
        return Optional.empty();
    }

    public Optional<Supplier> findByCode(String code) {
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("code", code)
                    .whereEqualTo("is_active", 1)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            if (!querySnapshot.isEmpty()) {
                return Optional.of(mapDocumentToSupplier(querySnapshot.getDocuments().get(0)));
            }
        } catch (Exception e) {
            logger.error("Error finding supplier by code: {}", code, e);
        }
        return Optional.empty();
    }

    public List<Supplier> search(String query) {
        List<Supplier> suppliers = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("is_active", 1)
                    .get();

            String lowerQuery = query != null ? query.toLowerCase() : "";
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Supplier s = mapDocumentToSupplier(doc);
                if (s.getCode().toLowerCase().contains(lowerQuery) || 
                    s.getName().toLowerCase().contains(lowerQuery)) {
                    suppliers.add(s);
                }
            }
        } catch (Exception e) {
            logger.error("Error searching suppliers with query: {}", query, e);
        }
        return suppliers;
    }

    public void save(Supplier supplier) {
        if (supplier.getId() == null) {
            insert(supplier);
        } else {
            update(supplier);
        }
    }

    private void insert(Supplier supplier) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Long newId = FirestoreHelper.getNextId(COLLECTION);
            supplier.setId(newId);
            supplier.setActive(true);
            supplier.setCreatedAt(LocalDateTime.now());

            db.collection(COLLECTION).document(String.valueOf(newId)).set(supplierToMap(supplier)).get();
            logger.info("Supplier created: {}", supplier.getName());
        } catch (Exception e) {
            logger.error("Error inserting supplier", e);
            throw new RuntimeException("Gagal menyimpan supplier", e);
        }
    }

    private void update(Supplier supplier) {
        try {
            Firestore db = FirestoreHelper.getDb();
            db.collection(COLLECTION).document(String.valueOf(supplier.getId())).set(supplierToMap(supplier)).get();
            logger.info("Supplier updated: {}", supplier.getName());
        } catch (Exception e) {
            logger.error("Error updating supplier", e);
            throw new RuntimeException("Error updating supplier: " + e.getMessage(), e);
        }
    }

    public void delete(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Map<String, Object> update = new HashMap<>();
            update.put("is_active", 0);
            db.collection(COLLECTION).document(String.valueOf(id)).update(update).get();
            logger.info("Supplier deleted (soft): {}", id);
        } catch (Exception e) {
            logger.error("Error deleting supplier", e);
            throw new RuntimeException("Error deleting supplier: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> supplierToMap(Supplier s) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", s.getId());
        map.put("code", s.getCode());
        map.put("name", s.getName());
        map.put("contact", s.getContact());
        map.put("address", s.getAddress());
        map.put("phone", s.getPhone());
        map.put("email", s.getEmail());
        map.put("is_active", s.isActive() ? 1 : 0);
        
        if (s.getCreatedAt() != null) {
            map.put("created_at", s.getCreatedAt().format(DB_DATE_FMT));
        }
        return map;
    }

    private Supplier mapDocumentToSupplier(DocumentSnapshot doc) {
        Supplier s = new Supplier();
        s.setId(doc.getLong("id"));
        s.setCode(doc.getString("code"));
        s.setName(doc.getString("name"));
        s.setContact(doc.getString("contact"));
        s.setAddress(doc.getString("address"));
        s.setPhone(doc.getString("phone"));
        s.setEmail(doc.getString("email"));
        
        Long isActive = doc.getLong("is_active");
        s.setActive(isActive != null && isActive == 1);

        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null) {
            s.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        return s;
    }
}


