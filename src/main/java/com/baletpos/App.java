package com.baletpos;

import atlantafx.base.theme.NordLight;
import com.baletpos.config.DatabaseConfig;
import com.baletpos.util.ImageUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumnBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class App extends Application {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static Scene scene;
    private static Stage primaryStage;
    private static final Set<String> TEXT_KEYS = Set.of(
            "nama", "name", "kode", "code", "kategori", "category", "kasir", "cashier",
            "deskripsi", "description", "supplier", "pelanggan", "customer", "sku",
            "metode", "method", "payment", "invoice", "nota", "alamat", "contact", "email",
            "telepon", "phone", "produk", "product", "service");
    private static final Set<String> NUMBER_KEYS = Set.of(
            "qty", "jumlah", "harga", "total", "hpp", "laba", "profit", "omzet", "omset",
            "nilai", "margin", "diskon", "stok", "stock", "subtotal", "revenue", "cogs",
            "amount", "pembayaran");
    private static final Set<String> STATUS_KEYS = Set.of(
            "status", "badge", "pembayaran", "payment", "metode", "method");
    private static final Set<String> ACTION_KEYS = Set.of(
            "aksi", "action", "actions", "edit", "hapus", "delete", "cetak", "print", "lihat", "view", "detail");
    private static final Set<String> SMALL_KEYS = Set.of(
            "no", "no.", "qty", "status", "aksi", "action", "select", "id", "stok", "stock");
    private static final Set<String> MAIN_KEYS = Set.of(
            "nama", "name", "deskripsi", "description", "produk", "product");

    @Override
    public void start(Stage stage) throws IOException {
        logger.info("Starting BaletPOS Application...");
        primaryStage = stage;

        // Apply light-only modern theme (NordLight - clean & minimal)
        Application.setUserAgentStylesheet(new NordLight().getUserAgentStylesheet());

        // Initialize Database (Firebase)
        try {
            com.baletpos.config.FirebaseConfig.initialize();

            // Copy dummy images if first run
            ImageUtil.copyDummyImages();

        } catch (Exception e) {
            logger.error("Critical Error: Database initialization failed", e);
            com.baletpos.util.ModalUtil.showError("Gagal terhubung ke database. Cek file serviceAccountKey.json Anda.", e);
            javafx.application.Platform.exit();
            System.exit(1);
            return;
        }

        scene = new Scene(loadFXML("login"), 1366, 768);

        // Apply custom minimal styles
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());

        // Setup global keyboard shortcuts
        setupGlobalShortcuts(scene);

        // App Icon
        try {
            stage.getIcons().add(new javafx.scene.image.Image(App.class.getResourceAsStream("/assets/icon/app.png")));
        } catch (Exception e) {
            logger.warn("App icon not found", e);
        }

        stage.setScene(scene);
        stage.setTitle("BaletPOS - Sistem Point of Sale");
        stage.setMinWidth(1200);
        stage.setMinHeight(700);
        stage.setMaximized(true);
        stage.setFullScreen(true);
        stage.show();

        applyTableAutoFit(scene.getRoot());
    }

    private void setupGlobalShortcuts(Scene scene) {
        // Fullscreen only
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F11),
                () -> primaryStage.setFullScreen(!primaryStage.isFullScreen()));
    }

    public static void setRoot(String fxml) throws IOException {
        Parent root = loadFXML(fxml);
        scene.setRoot(root);
        applyTableAutoFit(root);

        // Restore stylesheet
        if (!scene.getStylesheets().contains(App.class.getResource("/css/app.css").toExternalForm())) {
            scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        }
    }

    public static void setRootWithSize(String fxml, double width, double height) throws IOException {
        Parent root = loadFXML(fxml);
        scene.setRoot(root);
        applyTableAutoFit(root);
        primaryStage.setWidth(width);
        primaryStage.setHeight(height);
        primaryStage.centerOnScreen();
    }

    private static void applyTableAutoFit(Parent root) {
        if (root == null) {
            return;
        }
        for (Node node : root.lookupAll(".table-view")) {
            if (node instanceof TableView<?> table) {
                try {
                    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
                } catch (Exception e) {
                    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
                }
                table.setMinWidth(0);
                table.setMaxWidth(Double.MAX_VALUE);
                table.setPrefWidth(Region.USE_COMPUTED_SIZE);
                forceStretchColumns(table);
            }
        }
    }

    private static void forceStretchColumns(TableView<?> table) {
        for (TableColumnBase<?, ?> col : table.getColumns()) {
            applyGlobalColumnRules(col);
            if (!col.getColumns().isEmpty()) {
                for (TableColumnBase<?, ?> child : col.getColumns()) {
                    applyGlobalColumnRules(child);
                }
            }
        }
    }

    private static void applyGlobalColumnRules(TableColumnBase<?, ?> col) {
        String raw = (col.getText() != null ? col.getText() : "").trim();
        String id = (col.getId() != null ? col.getId() : "").trim();
        String key = (raw + " " + id).toLowerCase();
        boolean headerEmpty = raw.isBlank();

        boolean isAction = headerEmpty || containsAny(key, ACTION_KEYS) || id.toLowerCase().contains("action");
        boolean isStatus = containsAny(key, STATUS_KEYS);
        boolean isNumeric = containsAny(key, NUMBER_KEYS);
        boolean isText = containsAny(key, TEXT_KEYS);
        boolean isMain = containsAny(key, MAIN_KEYS);
        boolean isSmall = containsAny(key, SMALL_KEYS) || isAction || isStatus;

        addStyleClass(col, "col-text");
        if (isNumeric) {
            addStyleClass(col, "col-number");
            removeStyleClass(col, "col-text");
        } else if (isStatus) {
            addStyleClass(col, "col-status");
            removeStyleClass(col, "col-text");
        } else if (isAction) {
            addStyleClass(col, "col-action");
            removeStyleClass(col, "col-text");
        } else if (isText) {
            addStyleClass(col, "col-text");
        }

        if (isAction) {
            col.setMinWidth(70);
            col.setPrefWidth(90);
            col.setMaxWidth(120);
            col.setResizable(false);
        } else if (isSmall) {
            col.setMinWidth(60);
            col.setPrefWidth(90);
            col.setMaxWidth(140);
        } else if (isMain) {
            col.setMinWidth(180);
            col.setPrefWidth(220);
            col.setMaxWidth(1f * Integer.MAX_VALUE);
        } else {
            col.setMinWidth(80);
            col.setMaxWidth(1f * Integer.MAX_VALUE);
        }
    }

    private static boolean containsAny(String text, Set<String> keys) {
        for (String key : keys) {
            if (text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static void addStyleClass(TableColumnBase<?, ?> col, String cls) {
        if (!col.getStyleClass().contains(cls)) {
            col.getStyleClass().add(cls);
        }
    }

    private static void removeStyleClass(TableColumnBase<?, ?> col, String cls) {
        col.getStyleClass().remove(cls);
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/fxml/" + fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static Scene getScene() {
        return scene;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch();
    }
}


