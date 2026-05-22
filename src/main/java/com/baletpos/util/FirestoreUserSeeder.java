package com.baletpos.util;

import com.baletpos.config.FirebaseConfig;
import com.baletpos.config.FirestoreHelper;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FirestoreUserSeeder {
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        System.out.println("Starting Firestore User Seeder...");
        try {
            // Initialize Firebase
            FirebaseConfig.initialize();
            Firestore db = FirestoreHelper.getDb();

            seedUser(db, "admin", "admin123", "Administrator", "ADMIN");
            seedUser(db, "kasir", "kasir123", "Kasir Utama", "KASIR");

            System.out.println("Seeding completed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

    private static void seedUser(Firestore db, String username, String password, String fullName, String role) throws Exception {
        String hash = PasswordUtil.hashPassword(password);
        String now = LocalDateTime.now().format(DB_DATE_FMT);

        // Cek apakah user sudah ada
        List<QueryDocumentSnapshot> existing = db.collection("users")
                .whereEqualTo("username", username)
                .get().get().getDocuments();

        if (!existing.isEmpty()) {
            System.out.println("User '" + username + "' already exists. Updating password...");
            for (QueryDocumentSnapshot doc : existing) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("password_hash", hash);
                updates.put("full_name", fullName);
                updates.put("role", role);
                updates.put("is_active", 1);
                updates.put("updated_at", now);
                doc.getReference().update(updates).get();
            }
        } else {
            System.out.println("Inserting new user '" + username + "'...");
            long newId = FirestoreHelper.getNextId("users");
            
            Map<String, Object> user = new HashMap<>();
            user.put("id", newId);
            user.put("username", username);
            user.put("password_hash", hash);
            user.put("full_name", fullName);
            user.put("role", role);
            user.put("is_active", 1);
            user.put("created_at", now);
            user.put("updated_at", now);

            db.collection("users").document(String.valueOf(newId)).set(user).get();
        }
    }
}
