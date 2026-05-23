package com.baletpos.service.firestore;

import com.baletpos.dao.UserDAO;
import com.baletpos.model.User;
import com.baletpos.util.PasswordUtil;
import com.baletpos.util.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class FirestoreAuthService {
    private static final Logger logger = LoggerFactory.getLogger(FirestoreAuthService.class);
    private final UserDAO userDAO;

    public FirestoreAuthService() {
        this.userDAO = new UserDAO();
    }

    public boolean login(String username, String password) {
        try {
            Optional<User> userOpt = userDAO.findByUsername(username);

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (PasswordUtil.checkPassword(password, user.getPasswordHash())) {
                    Session.getInstance().login(user);
                    logger.info("User {} logged in successfully (firestore)", username);
                    return true;
                }
            }
            logger.warn("Failed login attempt for username: {} (firestore)", username);
        } catch (Exception e) {
            logger.error("Login error (firestore)", e);
        }
        return false;
    }

    public void logout() {
        if (Session.getInstance().isLoggedIn()) {
            logger.info("User {} logged out", Session.getInstance().getCurrentUser().getUsername());
            Session.getInstance().logout();
        }
    }
}

