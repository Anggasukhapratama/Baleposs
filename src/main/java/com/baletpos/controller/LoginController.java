package com.baletpos.controller;

import com.baletpos.App;
import com.baletpos.model.User;
import com.baletpos.service.AuthService;
import com.baletpos.util.Session;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    private final AuthService authService;

    public LoginController() {
        this.authService = new AuthService();
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username dan Password harus diisi!");
            return;
        }

        setLoginBusy(true);
        hideError();

        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() {
                return authService.login(username, password);
            }
        };

        loginTask.setOnSucceeded(event -> {
            setLoginBusy(false);
            if (Boolean.TRUE.equals(loginTask.getValue())) {
                User user = Session.getInstance().getCurrentUser();
                if (user.getRole() == User.Role.KASIR
                        || user.getRole() == User.Role.ADMIN_TOKO
                        || user.getRole() == User.Role.ADMIN_KEUANGAN) {
                    try {
                        openDashboard();
                    } catch (IOException e) {
                        e.printStackTrace();
                        showError("Gagal memuat halaman selanjutnya.");
                    }
                } else {
                    showError("Role user tidak dikenali.");
                }
            } else {
                showError("Username atau Password salah!");
            }
        });

        loginTask.setOnFailed(event -> {
            setLoginBusy(false);
            showError("Login gagal. Periksa koneksi database.");
        });

        Thread thread = new Thread(loginTask, "baletpos-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void openDashboard() throws IOException {
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setWidth(1280);
        stage.setHeight(720);
        stage.centerOnScreen();
        stage.setMaximized(true); // Admin usually wants full view
        App.setRoot("dashboard");
    }

    private void openPOS() throws IOException {
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setWidth(1024); // POS might be optimized for touch or specific screen
        stage.setHeight(768);
        stage.centerOnScreen();
        stage.setMaximized(true);
        App.setRoot("pos_view");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setLoginBusy(boolean busy) {
        loginButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        loginButton.setText(busy ? "Memproses..." : "Masuk");
    }
}


