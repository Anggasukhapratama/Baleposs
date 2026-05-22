package com.baletpos.util;

import com.baletpos.config.FirebaseConfig;
import com.baletpos.config.FirestoreHelper;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import java.util.List;

public class TestLogin {
    public static void main(String[] args) {
        try {
            FirebaseConfig.initialize();
            Firestore db = FirestoreHelper.getDb();
            
            System.out.println("Fetching all users:");
            List<QueryDocumentSnapshot> docs = db.collection("users").get().get().getDocuments();
            if (docs.isEmpty()) {
                System.out.println("No users found in collection.");
            }
            
            for (QueryDocumentSnapshot doc : docs) {
                System.out.println("User ID: " + doc.getId());
                System.out.println("  username: " + doc.getString("username"));
                System.out.println("  is_active: " + doc.get("is_active") + " (Class: " + (doc.get("is_active") != null ? doc.get("is_active").getClass() : "null") + ")");
                System.out.println("  password_hash: " + doc.getString("password_hash"));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }
}
