package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class UserDAO {
    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Optional<User> findByUsername(String username) {
        try {
            Firestore db = FirestoreHelper.getDb();
            // Melakukan query ke collection "users"
            ApiFuture<QuerySnapshot> future = db.collection("users")
                    .whereEqualTo("username", username)
                    .whereEqualTo("is_active", 1L)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            if (!querySnapshot.isEmpty()) {
                QueryDocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                return Optional.of(mapDocumentToUser(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding user by username: {}", username, e);
        }
        return Optional.empty();
    }

    public Optional<User> findFirstActiveByRoles(List<User.Role> roles) {
        try {
            Firestore db = FirestoreHelper.getDb();
            // Firestore tidak support "whereIn" untuk semua versi,
            // jadi kita query semua user aktif lalu filter di Java.
            ApiFuture<QuerySnapshot> future = db.collection("users")
                    .whereEqualTo("is_active", 1L)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            for (QueryDocumentSnapshot doc : querySnapshot.getDocuments()) {
                String roleStr = doc.getString("role");
                if (roleStr != null) {
                    try {
                        User.Role role = User.Role.valueOf(roleStr);
                        if (roles.contains(role)) {
                            return Optional.of(mapDocumentToUser(doc));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Role tidak dikenal, skip
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error finding first active user by roles: {}", roles, e);
        }
        return Optional.empty();
    }

    private User mapDocumentToUser(QueryDocumentSnapshot doc) {
        User user = new User();
        // ID sekarang diambil dari field id bertipe Long
        user.setId(doc.getLong("id"));
        user.setUsername(doc.getString("username"));
        user.setPasswordHash(doc.getString("password_hash"));
        user.setFullName(doc.getString("full_name"));
        
        String roleStr = doc.getString("role");
        if (roleStr != null) {
            user.setRole(User.Role.valueOf(roleStr));
        }
        
        Long isActive = doc.getLong("is_active");
        user.setActive(isActive != null && isActive == 1);
        
        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null) {
            user.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }
        
        String updatedAtStr = doc.getString("updated_at");
        if (updatedAtStr != null) {
            user.setUpdatedAt(LocalDateTime.parse(updatedAtStr, DB_DATE_FMT));
        }
        
        return user;
    }
}


