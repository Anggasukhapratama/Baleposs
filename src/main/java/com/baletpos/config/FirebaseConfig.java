package com.baletpos.config;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class FirebaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    public static void initialize() {
        try {
            // Path menuju file JSON service account yang didownload dari Firebase Console
            java.io.File file = new java.io.File("serviceAccountKey.json");
            if (!file.exists()) {
                file = new java.io.File("baletpos-new/serviceAccountKey.json");
            }
            if (!file.exists()) {
                 throw new IOException("File serviceAccountKey.json tidak ditemukan di direktori kerja: " + new java.io.File(".").getAbsolutePath());
            }

            FileInputStream serviceAccount = new FileInputStream(file);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                logger.info("Firebase SDK berhasil diinisialisasi!");
                
                checkAndSeedDefaultUsers();
            }

        } catch (IOException e) {
            logger.error("Gagal inisialisasi Firebase!", e);
            throw new RuntimeException("Gagal inisialisasi Firebase: " + e.getMessage(), e);
        }
    }

    private static void checkAndSeedDefaultUsers() {
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection("users").whereEqualTo("username", "admin").get();
            QuerySnapshot querySnapshot = future.get();
            
            if (querySnapshot.isEmpty()) {
                // Buat admin user
                Long id = FirestoreHelper.getNextId("users");
                Map<String, Object> adminUser = new HashMap<>();
                adminUser.put("id", id);
                adminUser.put("username", "admin");
                adminUser.put("password_hash", com.baletpos.util.PasswordUtil.hashPassword("admin123"));
                adminUser.put("full_name", "Administrator");
                adminUser.put("role", "ADMIN");
                adminUser.put("is_active", 1L);
                adminUser.put("created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                
                db.collection("users").document(String.valueOf(id)).set(adminUser).get();
                logger.info("Default admin user created in Firestore!");
            }
        } catch (Exception e) {
            logger.error("Error seeding default users", e);
        }
    }
}
