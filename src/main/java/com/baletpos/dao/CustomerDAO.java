package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.Customer;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CustomerDAO {
    private static final Logger logger = LoggerFactory.getLogger(CustomerDAO.class);
    private static final String COLLECTION = "customers";

    public List<Customer> findAll() {
        List<Customer> customers = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("is_active", 1)
                    .get();
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                customers.add(mapDocumentToCustomer(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all customers", e);
        }
        return customers;
    }

    public List<Customer> search(String query) {
        // Firestore is very limited for LIKE %search%. We pull all active and filter in memory
        List<Customer> customers = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("is_active", 1)
                    .get();
            String lowerQuery = query != null ? query.toLowerCase() : "";
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Customer c = mapDocumentToCustomer(doc);
                if (c.getName().toLowerCase().contains(lowerQuery) || 
                    (c.getPhone() != null && c.getPhone().toLowerCase().contains(lowerQuery)) ||
                    (c.getEmail() != null && c.getEmail().toLowerCase().contains(lowerQuery))) {
                    customers.add(c);
                }
            }
        } catch (Exception e) {
            logger.error("Error searching customers", e);
        }
        return customers;
    }

    public Optional<Customer> findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                return Optional.of(mapDocumentToCustomer(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding customer by id: {}", id, e);
        }
        return Optional.empty();
    }

    public List<Customer> findAllPaged(int limit, int offset, String query) {
        // Implement simple in-memory pagination since Firestore cursor pagination is complex for random offset
        List<Customer> allFiltered = search(query);
        allFiltered.sort((c1, c2) -> c1.getName().compareToIgnoreCase(c2.getName()));
        
        int start = Math.min(offset, allFiltered.size());
        int end = Math.min(start + limit, allFiltered.size());
        return allFiltered.subList(start, end);
    }

    public int countFiltered(String query) {
        return search(query).size();
    }

    public void save(Customer customer) {
        if (customer.getId() == null) {
            insert(customer);
        } else {
            update(customer);
        }
    }

    public void insert(Customer customer) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Long newId = FirestoreHelper.getNextId(COLLECTION);
            customer.setId(newId);
            customer.setActive(true);

            db.collection(COLLECTION).document(String.valueOf(newId)).set(customerToMap(customer)).get();
            logger.info("Customer created: {}", customer.getName());
        } catch (Exception e) {
            logger.error("Error inserting customer", e);
        }
    }

    public void update(Customer customer) {
        try {
            Firestore db = FirestoreHelper.getDb();
            db.collection(COLLECTION).document(String.valueOf(customer.getId())).set(customerToMap(customer)).get();
            logger.info("Customer updated: {}", customer.getName());
        } catch (Exception e) {
            logger.error("Error updating customer", e);
        }
    }

    public void delete(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Map<String, Object> update = new HashMap<>();
            update.put("is_active", 0);
            db.collection(COLLECTION).document(String.valueOf(id)).update(update).get();
            logger.info("Customer deleted (soft): {}", id);
        } catch (Exception e) {
            logger.error("Error deleting customer", e);
        }
    }

    private Map<String, Object> customerToMap(Customer c) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", c.getId());
        map.put("name", c.getName());
        map.put("phone", c.getPhone());
        map.put("address", c.getAddress());
        map.put("email", c.getEmail());
        map.put("notes", c.getNotes());
        map.put("is_active", c.isActive() ? 1 : 0);
        return map;
    }

    private Customer mapDocumentToCustomer(DocumentSnapshot doc) {
        Customer c = new Customer();
        c.setId(doc.getLong("id"));
        c.setName(doc.getString("name"));
        c.setPhone(doc.getString("phone"));
        c.setAddress(doc.getString("address"));
        c.setEmail(doc.getString("email"));
        c.setNotes(doc.getString("notes"));
        
        Long isActive = doc.getLong("is_active");
        c.setActive(isActive != null && isActive == 1);
        return c;
    }
}


