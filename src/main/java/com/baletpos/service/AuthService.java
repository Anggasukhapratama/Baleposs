package com.baletpos.service;

import com.baletpos.config.AuthProvider;

/**
 * Backward-compatible facade.
 *
 * - If APP_AUTH_PROVIDER=DATABASE (default), login uses Postgres/SQLite users table.
 * - If APP_AUTH_PROVIDER=FIRESTORE, login uses Firestore.
 */
public class AuthService {

    private final DatabaseAuthService dbAuthService;
    private final com.baletpos.service.firestore.FirestoreAuthService fsAuthService;

    public AuthService() {
        this.dbAuthService = new DatabaseAuthService();
        this.fsAuthService = new com.baletpos.service.firestore.FirestoreAuthService();
    }

    public boolean login(String username, String password) {
        AuthProvider provider = resolveProvider();
        return switch (provider) {
            case FIRESTORE -> fsAuthService.login(username, password);
            case DATABASE -> dbAuthService.login(username, password);
        };
    }

    public void logout() {
        AuthProvider provider = resolveProvider();
        switch (provider) {
            case FIRESTORE -> fsAuthService.logout();
            case DATABASE -> dbAuthService.logout();
        }
    }

    private AuthProvider resolveProvider() {
        String v = System.getenv("APP_AUTH_PROVIDER");
        if (v == null || v.isBlank()) {
            return AuthProvider.DATABASE;
        }
        return AuthProvider.valueOf(v.trim().toUpperCase());
    }
}

