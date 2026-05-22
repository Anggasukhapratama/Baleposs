package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.AuditLog;
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

public class AuditLogDAO {
    private static final Logger logger = LoggerFactory.getLogger(AuditLogDAO.class);
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String COLLECTION = "audit_logs";

    public void insert(AuditLog auditLog) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Long id = FirestoreHelper.getNextId(COLLECTION);
            auditLog.setId(id);

            DocumentReference docRef = db.collection(COLLECTION).document(String.valueOf(id));
            docRef.set(logToMap(auditLog));

        } catch (Exception e) {
            logger.error("Error inserting audit log", e);
        }
    }

    public void log(Long userId, String action, String tableName, Long recordId, String oldValues, String newValues) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUserId(userId);
        auditLog.setAction(action);
        auditLog.setTableName(tableName);
        auditLog.setRecordId(recordId);
        auditLog.setOldValues(oldValues);
        auditLog.setNewValues(newValues);
        insert(auditLog);
    }

    public List<AuditLog> findAll() {
        List<AuditLog> logs = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .limit(500)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                logs.add(mapDocumentToLog(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all audit logs", e);
        }
        return logs;
    }

    public List<AuditLog> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<AuditLog> logs = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            String startStr = startDate.atStartOfDay().format(DB_DATE_FMT);
            String endStr = endDate.atTime(23, 59, 59).format(DB_DATE_FMT);

            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("created_at", startStr)
                    .whereLessThanOrEqualTo("created_at", endStr)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                logs.add(mapDocumentToLog(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding audit logs by date range", e);
        }
        return logs;
    }

    public List<AuditLog> findByAction(String action) {
        List<AuditLog> logs = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("action", action)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                logs.add(mapDocumentToLog(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding audit logs by action: {}", action, e);
        }
        return logs;
    }

    public List<AuditLog> findByUserId(Long userId) {
        List<AuditLog> logs = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("user_id", userId)
                    .orderBy("created_at", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                logs.add(mapDocumentToLog(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding audit logs by user id: {}", userId, e);
        }
        return logs;
    }

    private Map<String, Object> logToMap(AuditLog log) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", log.getId());
        map.put("user_id", log.getUserId());
        
        // Denormalize user
        if (log.getUserId() != null) {
            try {
                DocumentSnapshot userDoc = FirestoreHelper.getDb().collection("users").document(String.valueOf(log.getUserId())).get().get();
                if (userDoc.exists()) {
                    map.put("username", userDoc.getString("username"));
                }
            } catch (Exception ignored) {}
        }
        
        map.put("action", log.getAction());
        map.put("table_name", log.getTableName());
        map.put("record_id", log.getRecordId());
        map.put("old_values", log.getOldValues());
        map.put("new_values", log.getNewValues());
        map.put("ip_address", log.getIpAddress());
        map.put("created_at", LocalDateTime.now().format(DB_DATE_FMT));
        
        return map;
    }

    private AuditLog mapDocumentToLog(DocumentSnapshot doc) {
        AuditLog log = new AuditLog();
        log.setId(doc.getLong("id"));
        log.setUserId(doc.getLong("user_id"));
        log.setUsername(doc.getString("username"));
        log.setAction(doc.getString("action"));
        log.setTableName(doc.getString("table_name"));
        
        Long recordId = doc.getLong("record_id");
        if (recordId != null) {
            log.setRecordId(recordId);
        }
        
        log.setOldValues(doc.getString("old_values"));
        log.setNewValues(doc.getString("new_values"));
        log.setIpAddress(doc.getString("ip_address"));
        
        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null && !createdAtStr.isBlank()) {
            try {
                log.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
            } catch (Exception ex) {}
        }
        
        return log;
    }
}


