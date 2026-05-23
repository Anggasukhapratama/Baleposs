package com.baletpos.dao;

import com.baletpos.config.DatabaseConfig;
import com.baletpos.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public class DatabaseUserDAO {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseUserDAO.class);

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, full_name, role, is_active, created_at, updated_at " +
                "FROM users WHERE username = ? AND is_active = 1 LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (Exception e) {
            logger.error("Error finding user by username in database: {}", username, e);
        }

        return Optional.empty();
    }

    private User mapRow(ResultSet rs) throws Exception {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));

        String role = rs.getString("role");
        if (role != null && !role.isBlank()) {
            // role expected: KASIR, ADMIN_TOKO, ADMIN_KEUANGAN
            u.setRole(User.Role.valueOf(role));
        }

        // is_active may be stored as int/boolean
        u.setActive(rs.getInt("is_active") == 1);

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            u.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            u.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return u;
    }
}

