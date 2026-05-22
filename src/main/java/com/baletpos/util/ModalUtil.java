package com.baletpos.util;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import com.baletpos.App;
import javafx.util.Duration;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Revitalized Modal Utility - "Say No to Boring"
 * Cheerful, Modern, Glassmorphism, and Debug-friendly.
 */
public class ModalUtil {

    public enum ModalType {
        INFO, SUCCESS, WARNING, ERROR, CONFIRM
    }

    // =============================================
    // PUBLIC API (Friendly Language Wrapper)
    // =============================================

    public static void showInfo(String title, String message) {
        showModal(ModalType.INFO, title, message, null);
    }

    public static void showSuccess(String title, String message) {
        showModal(ModalType.SUCCESS, title, message, null);
    }

    public static void showWarning(String title, String message) {
        showModal(ModalType.WARNING, title, message, null);
    }

    public static void showError(String title, String message) {
        showModal(ModalType.ERROR, title, message, null);
    }

    public static void showError(String message, Throwable ex) {
        showFriendlyError(message, ex);
    }

    public static boolean showConfirm(String title, String message) {
        return showConfirmModal(title, message, "Ya, Lanjutkan", "Batal Dulu", false);
    }

    public static boolean showConfirmDanger(String title, String message) {
        return showConfirmModal(title, message, "Hapus Aja", "Jangan Dulu", true);
    }

    // =============================================
    // INTERNAL BUILDER
    // =============================================

    private static void showModal(ModalType type, String title, String message, String debugInfo) {
        Platform.runLater(() -> {
            Stage modal = createGlassStage();
            VBox content = createContent(type, title, message, debugInfo, modal);

            // Add OK Button
            Button okBtn = createButton("Siap, Mengerti!", type == ModalType.ERROR ? "btn-danger" : "btn-primary");
            okBtn.setOnAction(e -> modal.close());

            HBox actions = new HBox(okBtn);
            actions.setAlignment(Pos.CENTER);
            actions.setPadding(new Insets(20, 0, 0, 0));
            content.getChildren().add(actions);

            showAndWait(modal, wrapWithOverlay(content));
        });
    }

    private static boolean showConfirmModal(String title, String message, String confirmText, String cancelText,
            boolean isDanger) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);

        // Run on FX Thread logic handled by caller usually, but let's ensure safety
        if (!Platform.isFxApplicationThread()) {
            // Simplify for this utility: blocking call not easily supported cross-thread
            // without Future
            // Assuming called from FX thread for UI interactions.
            Platform.runLater(
                    () -> showConfirmModalInternal(title, message, confirmText, cancelText, isDanger, result));
            // Note: This returns false immediately on non-FX thread which is issue,
            // but standard JavaFX dialogs block. We will use a workaround or assume FX
            // thread.
            return false;
        }

        return showConfirmModalInternal(title, message, confirmText, cancelText, isDanger, result);
    }

    private static boolean showConfirmModalInternal(String title, String message, String confirmText, String cancelText,
            boolean isDanger, AtomicReference<Boolean> result) {
        Stage modal = createGlassStage();
        VBox content = createContent(ModalType.CONFIRM, title, message, null, modal);

        Button confirmBtn = createButton(confirmText, isDanger ? "btn-danger" : "btn-primary");
        confirmBtn.setOnAction(e -> {
            result.set(true);
            modal.close();
        });

        Button cancelBtn = createButton(cancelText, "btn-secondary");
        cancelBtn.setOnAction(e -> {
            result.set(false);
            modal.close();
        });

        HBox actions = new HBox(16, cancelBtn, confirmBtn);
        actions.setAlignment(Pos.CENTER);
        actions.setPadding(new Insets(24, 0, 0, 0));
        content.getChildren().add(actions);

        showAndWait(modal, wrapWithOverlay(content));
        return result.get();
    }

    // =============================================
    // UI COMPOSITION
    // =============================================

    private static Stage createGlassStage() {
        Stage modal = new Stage();
        applyModalDefaults(modal);
        modal.initStyle(StageStyle.TRANSPARENT);
        return modal;
    }

    public static void applyModalDefaults(Stage modal) {
        if (modal == null) {
            return;
        }
        Window owner = App.getPrimaryStage();
        if (owner == null || !owner.isShowing()) {
            owner = Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .findFirst()
                    .orElse(null);
        }
        if (owner != null) {
            modal.initOwner(owner);
            modal.initModality(Modality.WINDOW_MODAL);
        } else {
            modal.initModality(Modality.APPLICATION_MODAL);
        }
    }

    private static VBox createContent(ModalType type, String title, String message, String debugInfo, Stage modal) {
        VBox box = new VBox(12);
        box.getStyleClass().add("glass-modal");
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(40));
        box.setMinWidth(450);
        box.setMaxWidth(550);

        // 1. Emoji Header
        Label emoji = new Label(getEmoji(type));
        emoji.setStyle("-fx-font-size: 64px;");

        // 2. Title
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: 800; -fx-text-fill: #1e293b;");

        // 3. Message
        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setAlignment(Pos.CENTER);
        msgLabel.setStyle(
                "-fx-font-size: 15px; -fx-text-fill: #64748b; -fx-line-spacing: 4; -fx-text-alignment: center;");

        box.getChildren().addAll(emoji, titleLabel, msgLabel);

        // 4. Debug Section (Collapsible)
        if (debugInfo != null && !debugInfo.isEmpty()) {
            VBox debugBox = new VBox(8);
            debugBox.setAlignment(Pos.CENTER_LEFT);
            debugBox.setPadding(new Insets(20, 0, 0, 0));

            Button techBtn = createButton("Detail Debug", "btn-secondary");

            TextArea console = new TextArea(debugInfo);
            console.getStyleClass().add("debug-terminal");
            console.setPrefRowCount(8);
            console.setEditable(false);
            console.setWrapText(false);
            console.setVisible(false);
            console.setManaged(false);

            // Toggle logic
            techBtn.setOnAction(e -> {
                boolean isVisible = !console.isVisible();
                console.setVisible(isVisible);
                console.setManaged(isVisible);
                techBtn.setText(isVisible ? "Sembunyikan Debug" : "Detail Debug");
                modal.sizeToScene(); // Resize window
            });

            debugBox.getChildren().addAll(techBtn, console);
            box.getChildren().add(debugBox);
        }

        return box;
    }

    private static Button createButton(String text, String styleClass) {
        Button btn = new Button(text);
        btn.getStyleClass().addAll("btn", styleClass);

        // Add Bounce Animation on Click (Micro-interaction)
        btn.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(0.95);
            st.setToY(0.95);
            st.play();
        });

        btn.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), btn);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });

        return btn;
    }

    private static void showAndWait(Stage modal, StackPane content) {
        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT); // Important for glassmorphism/rounded corners
        scene.getStylesheets().add(ModalUtil.class.getResource("/css/app.css").toExternalForm());

        modal.setScene(scene);
        modal.centerOnScreen();
        modal.showAndWait();
    }

    private static StackPane wrapWithOverlay(Node content) {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: rgba(148, 163, 184, 0.18); -fx-padding: 24;");
        root.getChildren().add(content);
        return root;
    }

    public static void showFriendlyError(String userMessage, Throwable ex) {
        String debugInfo = "";
        if (ex != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            debugInfo = sw.toString();
        }
        showModal(ModalType.ERROR, "Waduh, ada kendala teknis!", userMessage, debugInfo);
    }

    private static String getEmoji(ModalType type) {
        return switch (type) {
            case INFO -> "ðŸ’¡";
            case SUCCESS -> "ðŸŽ‰";
            case WARNING -> "ðŸ¤”";
            case ERROR -> "ðŸ¤•"; // Bandage face for error -> "Sakit" / Error
            case CONFIRM -> "ðŸ§";
        };
    }
}


