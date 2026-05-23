package com.baletpos.util;

import com.baletpos.dao.UserDAO;
import com.baletpos.model.User;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import com.baletpos.util.Session;
import com.baletpos.util.ModalUtil;
import com.baletpos.util.PasswordUtil;

import java.util.List;
import java.util.Optional;

public class SecurityUtil {

    public static boolean requestAdminConfirmation(String actionName) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Konfirmasi Admin");
        dialog.setHeaderText("Otorisasi Diperlukan: " + actionName);

        ButtonType confirmType = new ButtonType("Verifikasi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 20;");

        Label label = new Label("Masukkan Password Admin:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("********");

        content.getChildren().addAll(label, passwordField);
        dialog.getDialogPane().setContent(content);

        Platform.runLater(passwordField::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == confirmType) {
                return passwordField.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            return validatePassword(result.get());
        }
        return false;
    }

    private static boolean validatePassword(String inputPassword) {
        if (inputPassword == null || inputPassword.isBlank())
            return false;

        // 1. Cek user saat ini jika punya hak admin toko/keuangan
        User currentUser = Session.getInstance().getCurrentUser();
        if (currentUser != null && Session.getInstance().isAdmin()) {
            if (PasswordUtil.checkPassword(inputPassword, currentUser.getPasswordHash())) {
                return true;
            }
        }

        // 2. Jika user saat ini bukan admin, cek akun admin aktif pertama di database.
        UserDAO userDAO = new UserDAO();
        Optional<User> admin = userDAO.findFirstActiveByRoles(
                List.of(User.Role.ADMIN_TOKO, User.Role.ADMIN_KEUANGAN));
        if (admin.isPresent()) {
            if (PasswordUtil.checkPassword(inputPassword, admin.get().getPasswordHash())) {
                return true;
            }
        }

        ModalUtil.showError("Otorisasi Gagal", "Password salah atau tidak memiliki hak akses!");

        return false;
    }
}
