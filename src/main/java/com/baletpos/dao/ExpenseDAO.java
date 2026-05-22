package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.Expense;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExpenseDAO {
    private static final Logger logger = LoggerFactory.getLogger(ExpenseDAO.class);
    private static final DateTimeFormatter EXP_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String COLLECTION = "expenses";

    public void create(Expense expense) throws Exception {
        Firestore db = FirestoreHelper.getDb();
        try {
            Long id = FirestoreHelper.getNextId(COLLECTION);
            expense.setId(id);
            
            String expenseNumber = generateExpenseNumber();
            expense.setExpenseNumber(expenseNumber);

            DocumentReference docRef = db.collection(COLLECTION).document(String.valueOf(id));
            docRef.set(expenseToMap(expense)).get();
            
            logger.info("Expense created successfully: {}", expenseNumber);
        } catch (Exception e) {
            logger.error("Error creating expense", e);
            throw new Exception("Error creating expense: " + e.getMessage(), e);
        }
    }

    public List<Expense> findAll() {
        List<Expense> expenses = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .orderBy("expense_date", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                expenses.add(mapDocumentToExpense(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all expenses", e);
        }
        return expenses;
    }

    public List<Expense> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Expense> expenses = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            String startStr = startDate.toString();
            String endStr = endDate.toString();

            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("expense_date", startStr)
                    .whereLessThanOrEqualTo("expense_date", endStr)
                    .orderBy("expense_date", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                expenses.add(mapDocumentToExpense(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding expenses by date range", e);
        }
        return expenses;
    }

    public List<Expense> findByDateRangeAndCode(LocalDate startDate, LocalDate endDate, Long expenseCodeId) {
        List<Expense> expenses = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            String startStr = startDate.toString();
            String endStr = endDate.toString();

            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("expense_code_id", expenseCodeId)
                    .whereGreaterThanOrEqualTo("expense_date", startStr)
                    .whereLessThanOrEqualTo("expense_date", endStr)
                    .orderBy("expense_date", Query.Direction.DESCENDING)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                expenses.add(mapDocumentToExpense(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding expenses by date range and code", e);
        }
        return expenses;
    }

    public BigDecimal getTotalByDateRange(LocalDate startDate, LocalDate endDate) {
        BigDecimal total = BigDecimal.ZERO;
        List<Expense> expenses = findByDateRange(startDate, endDate);
        for (Expense e : expenses) {
            if (e.getAmount() != null) {
                total = total.add(e.getAmount());
            }
        }
        return total;
    }

    public Optional<Expense> findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                return Optional.of(mapDocumentToExpense(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding expense by id: {}", id, e);
        }
        return Optional.empty();
    }

    public void update(Expense expense) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentReference docRef = db.collection(COLLECTION).document(String.valueOf(expense.getId()));
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("expense_code_id", expense.getExpenseCodeId());
            updates.put("expense_date", expense.getExpenseDate().toString());
            updates.put("amount", expense.getAmount().intValue());
            updates.put("description", expense.getDescription());
            
            // Also update denormalized fields
            try {
                DocumentSnapshot ecDoc = db.collection("expense_codes").document(String.valueOf(expense.getExpenseCodeId())).get().get();
                if (ecDoc.exists()) {
                    updates.put("expense_code", ecDoc.getString("code"));
                    updates.put("expense_code_name", ecDoc.getString("name"));
                }
            } catch (Exception ignored) {}
            
            docRef.update(updates).get();
        } catch (Exception e) {
            logger.error("Error updating expense", e);
            throw new RuntimeException("Error updating expense: " + e.getMessage());
        }
    }

    public void delete(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            db.collection(COLLECTION).document(String.valueOf(id)).delete().get();
        } catch (Exception e) {
            logger.error("Error deleting expense", e);
            throw new RuntimeException("Error deleting expense: " + e.getMessage());
        }
    }

    private String generateExpenseNumber() {
        String datePart = LocalDate.now().format(EXP_DATE_FMT);
        String prefix = "EXP-" + datePart + "-";

        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereGreaterThanOrEqualTo("expense_number", prefix)
                    .whereLessThan("expense_number", prefix + "\uf8ff")
                    .orderBy("expense_number", Query.Direction.DESCENDING)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            int nextNum = 1;
            if (!querySnapshot.isEmpty()) {
                String lastInv = querySnapshot.getDocuments().get(0).getString("expense_number");
                if (lastInv != null && lastInv.length() > prefix.length()) {
                    try {
                        String numPart = lastInv.substring(prefix.length());
                        nextNum = Integer.parseInt(numPart) + 1;
                    } catch (NumberFormatException ignored) {}
                }
            }
            return String.format("%s%04d", prefix, nextNum);
        } catch (Exception e) {
            logger.error("Error generating expense number", e);
            return prefix + System.currentTimeMillis();
        }
    }

    private Map<String, Object> expenseToMap(Expense e) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", e.getId());
        map.put("expense_number", e.getExpenseNumber());
        map.put("expense_code_id", e.getExpenseCodeId());
        
        if (e.getExpenseCodeId() != null) {
            try {
                DocumentSnapshot ecDoc = FirestoreHelper.getDb().collection("expense_codes").document(String.valueOf(e.getExpenseCodeId())).get().get();
                if (ecDoc.exists()) {
                    map.put("expense_code", ecDoc.getString("code"));
                    map.put("expense_code_name", ecDoc.getString("name"));
                }
            } catch (Exception ignored) {}
        }

        map.put("expense_date", e.getExpenseDate().toString());
        map.put("amount", e.getAmount() != null ? e.getAmount().intValue() : 0);
        map.put("description", e.getDescription());
        map.put("created_by", e.getCreatedBy());
        map.put("created_at", LocalDateTime.now().format(DB_DATE_FMT));
        
        if (e.getCreatedBy() != null) {
            try {
                DocumentSnapshot userDoc = FirestoreHelper.getDb().collection("users").document(String.valueOf(e.getCreatedBy())).get().get();
                if (userDoc.exists()) {
                    map.put("created_by_name", userDoc.getString("full_name"));
                }
            } catch (Exception ignored) {}
        }
        
        return map;
    }

    private Expense mapDocumentToExpense(DocumentSnapshot doc) {
        Expense e = new Expense();
        e.setId(doc.getLong("id"));
        e.setExpenseNumber(doc.getString("expense_number"));
        e.setExpenseCodeId(doc.getLong("expense_code_id"));
        e.setExpenseCode(doc.getString("expense_code"));
        e.setExpenseCodeName(doc.getString("expense_code_name"));

        String dateStr = doc.getString("expense_date");
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                e.setExpenseDate(LocalDate.parse(dateStr.substring(0, 10)));
            } catch (Exception ex) {
                logger.warn("Failed to parse expense_date: {}", dateStr);
            }
        }

        e.setAmount(BigDecimal.valueOf(doc.getLong("amount") != null ? doc.getLong("amount") : 0));
        e.setDescription(doc.getString("description"));
        e.setCreatedBy(doc.getLong("created_by"));
        e.setCreatedByName(doc.getString("created_by_name"));

        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null && !createdAtStr.isBlank()) {
            try {
                e.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
            } catch (Exception ex) {
                logger.warn("Failed to parse created_at: {}", createdAtStr);
            }
        }

        return e;
    }
}


