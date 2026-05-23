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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class FirebaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    public static void initialize() {
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(resolveCredentials())
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                logger.info("Firebase SDK berhasil diinisialisasi!");
                checkAndSeedDefaultUsers();
            }

        } catch (Exception e) {
            logger.error("Gagal inisialisasi Firebase!", e);
            throw new RuntimeException("Gagal inisialisasi Firebase: " + e.getMessage(), e);
        }
    }

    private static GoogleCredentials resolveCredentials() throws IOException {
        // 1) Prefer: GOOGLE_APPLICATION_CREDENTIALS (path ke service account json)
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            java.io.File file = new java.io.File(credentialsPath);
            if (!file.exists()) {
                throw new IOException("GOOGLE_APPLICATION_CREDENTIALS path tidak ditemukan: " + file.getAbsolutePath());
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                return GoogleCredentials.fromStream(fis);
            }
        }

        // 2) Fallback: FIREBASE_SERVICE_ACCOUNT_JSON (isi json dalam env)
        // Catatan: tidak wajib dipakai, hanya membantu skenario tanpa file.
        String serviceAccountJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {
            String normalized = serviceAccountJson.replace("\\n", "\n");
            byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
            return GoogleCredentials.fromStream(new java.io.ByteArrayInputStream(bytes));
        }

        // 3) Legacy fallback: serviceAccountKey.json di working dir / folder project
        java.io.File file = new java.io.File("serviceAccountKey.json");
        if (!file.exists()) {
            file = new java.io.File("baletpos-new/serviceAccountKey.json");
        }
        if (!file.exists()) {
            throw new IOException(
                    "service account json tidak ditemukan. Set GOOGLE_APPLICATION_CREDENTIALS (path ke file json) " +
                            "atau FIREBASE_SERVICE_ACCOUNT_JSON (isi json), atau pastikan file serviceAccountKey.json ada. " +
                            "Working dir: " + new java.io.File(".").getAbsolutePath());
        }

        try (FileInputStream serviceAccount = new FileInputStream(file)) {
            return GoogleCredentials.fromStream(serviceAccount);
        }
    }

    private static void checkAndSeedDefaultUsers() {
        // In database-only deployments, Firebase may be intentionally disabled.
        // Keep this method defensive.
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection("users")
                    .whereEqualTo("username", "admin")
                    .get();

            QuerySnapshot querySnapshot = future.get();

            if (querySnapshot.isEmpty()) {
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

