package com.baletpos.config;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class FirestoreHelper {
    private static final Logger logger = LoggerFactory.getLogger(FirestoreHelper.class);

    public static Firestore getDb() {
        return FirestoreClient.getFirestore();
    }

    /**
     * Membantu generate ID unik berupa angka urut (Auto-Increment) seperti di SQLite.
     * Ini digunakan agar semua kode Java yang mengharapkan ID berupa angka (Long) tidak perlu diubah.
     */
    public static Long getNextId(String collectionName) {
        try {
            Firestore db = getDb();
            DocumentReference counterRef = db.collection("counters").document(collectionName);

            return db.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(counterRef).get();
                long newId = 1L;

                if (snapshot.exists() && snapshot.contains("current_id")) {
                    newId = snapshot.getLong("current_id") + 1;
                }

                Map<String, Object> update = new HashMap<>();
                update.put("current_id", newId);
                transaction.set(counterRef, update);

                return newId;
            }).get();
        } catch (Exception e) {
            logger.error("Gagal mendapatkan ID urut baru untuk " + collectionName, e);
            throw new RuntimeException("Firestore auto-increment error", e);
        }
    }
}
