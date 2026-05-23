package com.baletpos.controller;

import com.baletpos.dao.ProductDAO;
import com.baletpos.config.DatabaseConfig;
import com.baletpos.model.Product;
import com.baletpos.model.User;
import com.baletpos.util.ImageUtil;
import com.baletpos.util.NotificationUtil;
import com.baletpos.util.PaginationControl;
import com.baletpos.util.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller modern untuk manajemen Produk
 */
public class ProductController {

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> categoryFilter;

    @FXML
    private TableView<Product> productTable;

    @FXML
    private TableColumn<Product, String> skuCol;

    @FXML
    private TableColumn<Product, String> nameCol;


    @FXML
    private TableColumn<Product, String> typeCol;

    @FXML
    private TableColumn<Product, String> stockCol;

    @FXML
    private TableColumn<Product, String> hppCol;

    @FXML
    private TableColumn<Product, String> priceCol;

    @FXML
    private TableColumn<Product, String> marginCol;

    @FXML
    private TableColumn<Product, Void> actionsCol;

    @FXML
    private Button addButton;

    // Summary Cards Containers
    @FXML
    private VBox cardTotalProduk;
    @FXML
    private VBox cardStokHabis;
    @FXML
    private VBox cardStokMenipis;
    @FXML
    private VBox cardStokAman;

    // Summary Cards Labels
    @FXML
    private Label lblTotalProduk;
    @FXML
    private Label lblStokHabis;
    @FXML
    private Label lblStokMenipis;
    @FXML
    private Label lblStokAman;

    @FXML
    private VBox tableContainer;

    private final ProductDAO productDAO = new ProductDAO();
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
    private boolean canManageProducts;

    // Pagination
    private PaginationControl pagination;
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "SEMUA";

    @FXML
    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);

        setupCategoryFilter();
        setupTable();
        setupActionsColumn();
        setupPagination();
        setupSearch();
        setupKeyboardShortcuts();

        try {
            loadProductsPage();
        } catch (Exception e) {
            System.err.println("Failed to load products page: " + e.getMessage());
            e.printStackTrace();
        }

        loadSummaryStats();
        setupPermissions();
        setupSelectionListener();

        // Layout Config
        if (cardTotalProduk != null)
            HBox.setHgrow(cardTotalProduk, javafx.scene.layout.Priority.ALWAYS);
        if (cardStokHabis != null)
            HBox.setHgrow(cardStokHabis, javafx.scene.layout.Priority.ALWAYS);
        if (cardStokMenipis != null)
            HBox.setHgrow(cardStokMenipis, javafx.scene.layout.Priority.ALWAYS);
        if (cardStokAman != null)
            HBox.setHgrow(cardStokAman, javafx.scene.layout.Priority.ALWAYS);
    }

    private void setupKeyboardShortcuts() {
        // Ctrl+F to focus search
        if (searchField != null && searchField.getScene() != null) {
            searchField.getScene().getAccelerators().put(
                    new javafx.scene.input.KeyCodeCombination(
                            javafx.scene.input.KeyCode.F,
                            javafx.scene.input.KeyCombination.CONTROL_DOWN),
                    () -> searchField.requestFocus());
        }
        // Fallback: Add listener after scene is set
        searchField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new javafx.scene.input.KeyCodeCombination(
                                javafx.scene.input.KeyCode.F,
                                javafx.scene.input.KeyCombination.CONTROL_DOWN),
                        () -> searchField.requestFocus());
            }
        });
    }

    private void loadSummaryStats() {
        try {
            // Count all products for summary cards
            int total = productDAO.countFiltered(null, "SEMUA");
            int habis = countProductsByStockStatus("HABIS");
            int menipis = countProductsByStockStatus("MENIPIS");
            int aman = countProductsByStockStatus("AMAN");

            if (lblTotalProduk != null)
                lblTotalProduk.setText(String.valueOf(total));
            if (lblStokHabis != null)
                lblStokHabis.setText(String.valueOf(habis));
            if (lblStokMenipis != null) {
                lblStokMenipis.setVisible(true);
                lblStokMenipis.setText(String.valueOf(menipis));
            }
            if (lblStokAman != null) {
                lblStokAman.setVisible(true);
                lblStokAman.setText(String.valueOf(aman));
            }
        } catch (Exception e) {
            System.err.println("Failed to load summary stats: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int countProductsByStockStatus(String status) {
        String sql = "SELECT COUNT(*) FROM products WHERE is_active = 1";
        if ("HABIS".equals(status)) {
            sql += " AND stock <= 0";
        } else if ("MENIPIS".equals(status)) {
            sql += " AND stock > 0 AND stock < 5";
        } else if ("AMAN".equals(status)) {
            sql += " AND stock >= 5";
        } else {
            return 0;
        }

        try (java.sql.Connection conn = com.baletpos.config.DatabaseConfig.getConnection();
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next())
                return rs.getInt(1);
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    private void setupActionsColumn() {
        if (actionsCol == null)
            return;

        actionsCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button();
            private final Button deleteBtn = new Button();
            private final HBox box = new HBox(8, editBtn, deleteBtn);

            {
                box.setAlignment(Pos.CENTER);
                box.setPadding(new Insets(0, 4, 0, 4));
                // Initially hidden logic handled in updateItem
                box.setOpacity(0);

                // --- Edit Button (Pencil) ---
                javafx.scene.shape.SVGPath editIcon = new javafx.scene.shape.SVGPath();
                editIcon.setContent(
                        "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z");
                editIcon.setFill(javafx.scene.paint.Color.web("#94a3b8")); // Default Soft Grey
                editIcon.setScaleX(0.8);
                editIcon.setScaleY(0.8);

                editBtn.setGraphic(editIcon);
                editBtn.setTooltip(new Tooltip("Edit Produk"));
                editBtn.setPrefSize(32, 32);
                editBtn.setMinSize(32, 32);
                editBtn.setMaxSize(32, 32);
                editBtn.setStyle(
                        "-fx-background-color: transparent; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: transparent;");

                // Hover Edit
                editBtn.setOnMouseEntered(e -> {
                    editBtn.setStyle(
                            "-fx-background-color: #dbeafe; -fx-background-radius: 8; -fx-cursor: hand;"); // Blue Soft
                    editIcon.setFill(javafx.scene.paint.Color.web("#1E40AF")); // Blue Active
                });
                editBtn.setOnMouseExited(e -> {
                    editBtn.setStyle(
                            "-fx-background-color: transparent; -fx-background-radius: 8; -fx-cursor: hand;");
                    editIcon.setFill(javafx.scene.paint.Color.web("#94a3b8"));
                });

                editBtn.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    showProductDialog(p);
                });

                // --- Delete Button (Trash) ---
                javafx.scene.shape.SVGPath trashIcon = new javafx.scene.shape.SVGPath();
                trashIcon.setContent(
                        "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z");
                trashIcon.setFill(javafx.scene.paint.Color.web("#94a3b8")); // Default Soft Grey
                trashIcon.setScaleX(0.8);
                trashIcon.setScaleY(0.8);

                deleteBtn.setGraphic(trashIcon);
                deleteBtn.setTooltip(new Tooltip("Hapus Produk"));
                deleteBtn.setPrefSize(32, 32);
                deleteBtn.setMinSize(32, 32);
                deleteBtn.setMaxSize(32, 32);
                deleteBtn.setStyle(
                        "-fx-background-color: transparent; -fx-background-radius: 8; -fx-cursor: hand;");

                // Hover Delete
                deleteBtn.setOnMouseEntered(e -> {
                    deleteBtn.setStyle(
                            "-fx-background-color: #fee2e2; -fx-background-radius: 8; -fx-cursor: hand;"); // Red Soft
                    trashIcon.setFill(javafx.scene.paint.Color.web("#ef4444")); // Red Danger
                });
                deleteBtn.setOnMouseExited(e -> {
                    deleteBtn.setStyle(
                            "-fx-background-color: transparent; -fx-background-radius: 8; -fx-cursor: hand;");
                    trashIcon.setFill(javafx.scene.paint.Color.web("#94a3b8"));
                });

                deleteBtn.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    confirmAndDelete(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    if (!canManageProducts) {
                        setGraphic(null);
                        return;
                    }
                    setGraphic(box);
                    // Smart Visibility: Show on Row Hover or Row Selection
                    TableRow<Product> row = getTableRow();
                    if (row != null) {
                        box.opacityProperty().unbind(); // Clean previous bind
                        box.opacityProperty().bind(
                                javafx.beans.binding.Bindings.when(
                                        row.hoverProperty().or(row.selectedProperty())).then(1.0).otherwise(0.0));
                    }
                }
            }
        });
    }

    private void setupCategoryFilter() {
        categoryFilter.setItems(FXCollections.observableArrayList(
                "SEMUA", "LAPTOP_NEW", "LAPTOP_SECOND", "SPAREPARTS", "PERIPHERAL", "SERVICE"));
        categoryFilter.setValue("SEMUA");
        categoryFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupTable() {
        if (productTable != null) {
            try {
                productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            } catch (Exception e) {
                productTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            }
            productTable.setMinWidth(0);
            productTable.setMaxWidth(Double.MAX_VALUE);
        }

        // Weight-based widths for constrained resize
        skuCol.setPrefWidth(1.0);
        skuCol.setMinWidth(110);
        skuCol.setMaxWidth(1f * Integer.MAX_VALUE);

        nameCol.setPrefWidth(3.0);
        nameCol.setMinWidth(200);
        nameCol.setMaxWidth(1f * Integer.MAX_VALUE);

        typeCol.setPrefWidth(1.2);
        typeCol.setMinWidth(110);
        typeCol.setMaxWidth(1f * Integer.MAX_VALUE);

        stockCol.setPrefWidth(0.7);
        stockCol.setMinWidth(70);
        stockCol.setMaxWidth(1f * Integer.MAX_VALUE);

        hppCol.setPrefWidth(1.2);
        hppCol.setMinWidth(110);
        hppCol.setMaxWidth(1f * Integer.MAX_VALUE);

        marginCol.setPrefWidth(0.8);
        marginCol.setMinWidth(80);
        marginCol.setMaxWidth(1f * Integer.MAX_VALUE);

        priceCol.setPrefWidth(1.2);
        priceCol.setMinWidth(120);
        priceCol.setMaxWidth(1f * Integer.MAX_VALUE);

        actionsCol.setPrefWidth(0.6);
        actionsCol.setMinWidth(80);
        actionsCol.setMaxWidth(110);
        actionsCol.setResizable(false);

        // === SKU Column ===
        skuCol.setCellValueFactory(new PropertyValueFactory<>("sku"));
        skuCol.setStyle("-fx-alignment: CENTER-LEFT;");

        // === Name Column with Tooltip ===
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    // Add tooltip for potentially truncated text
                    Tooltip tooltip = new Tooltip(item);
                    tooltip.setStyle("-fx-font-size: 12px;");
                    setTooltip(tooltip);
                }
            }
        });

        // === Category Badge Column (Pastel & Cheerful) ===
        typeCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getProductType().name()));
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(mapTypeToFriendlyName(item));

                    String style = "-fx-background-radius: 20; -fx-padding: 4 12; -fx-font-weight: 700; -fx-font-size: 10px;";
                    if (item.contains("LAPTOP"))
                        style += "-fx-background-color: #dbeafe; -fx-text-fill: #1E40AF;";
                    else if (item.contains("SPARE"))
                        style += "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF;";
                    else if (item.contains("SERVICE"))
                        style += "-fx-background-color: #DBEAFE; -fx-text-fill: #1E40AF;";
                    else if (item.contains("PERIPHERAL"))
                        style += "-fx-background-color: #DBEAFE; -fx-text-fill: #1F3A8A;";
                    else
                        style += "-fx-background-color: #f1f5f9; -fx-text-fill: #64748b;";

                    badge.setStyle(style);
                    setGraphic(badge);
                    setText(null);
                    setAlignment(Pos.CENTER);
                }
            }

            private String mapTypeToFriendlyName(String type) {
                if (type.equals("LAPTOP_NEW"))
                    return "Laptop Baru";
                if (type.equals("LAPTOP_SECOND"))
                    return "Laptop Bekas";
                if (type.equals("SPAREPARTS"))
                    return "Sparepart";
                if (type.equals("PERIPHERAL"))
                    return "Aksesoris";
                return type;
            }
        });

        // === Stock Column with Warning Colors ===
        stockCol.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getStock())));
        stockCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    Product product = getTableView().getItems().get(getIndex());
                    if (product.getStock() <= 0) {
                        setStyle("-fx-text-fill: #1E40AF; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #1E40AF;");
                    }
                }
            }
        });

        // === HPP Column - Right Aligned Money ===
        hppCol.setCellValueFactory(cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getHpp())));
        hppCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    setStyle("-fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        // === Margin Column - Right Aligned ===
        marginCol.setCellValueFactory(
                cell -> new SimpleStringProperty(String.format("%.1f%%", cell.getValue().getMarginPercent())));
        marginCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        // === Selling Price Column (Bold & Blue) ===
        priceCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getSellingPrice())));
        priceCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    setStyle(
                            "-fx-font-family: 'Consolas', monospace; -fx-text-fill: #1E40AF; -fx-font-weight: 800; -fx-font-size: 13px;");
                }
            }
        });

        // === Actions Column ===
        if (actionsCol != null) {
            actionsCol.setPrefWidth(100);
            actionsCol.setMinWidth(90);
            actionsCol.setStyle("-fx-alignment: CENTER;");
        }

        // === Empty State Placeholder ===
        Label emptyLabel = new Label("Belum ada produk");
        emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");

        Button addEmptyBtn = new Button("+ Tambah Produk");
        addEmptyBtn.setStyle(
                "-fx-background-color: #1E40AF; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 8; -fx-cursor: hand;");
        addEmptyBtn.setOnAction(e -> handleAdd());

        VBox emptyBox = new VBox(12, emptyLabel, addEmptyBtn);
        emptyBox.setAlignment(Pos.CENTER);
        productTable.setPlaceholder(emptyBox);

        productTable.setItems(products);
    }

    private void setupPagination() {
        pagination = new PaginationControl();
        pagination.setOnPageChange(this::loadProductsPage);

        // Add pagination to container
        if (tableContainer != null) {
            tableContainer.getChildren().add(pagination);
        }
    }

    private void setupSearch() {
        // Debounced search - reset to page 1 on search change
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            currentSearchQuery = newVal != null ? newVal.trim() : "";
            pagination.resetToFirstPage();
            loadProductsPage();
        });

        productTable.setItems(products);
    }

    private void loadProductsPage() {
        int limit = pagination.getPageSize();
        int offset = pagination.getOffset();

        // Get total count for pagination
        int totalCount = productDAO.countFiltered(currentSearchQuery, currentCategoryFilter);
        pagination.update(totalCount);

        // Load only current page data
        List<Product> pageData = productDAO.findAllPaged(limit, offset, currentSearchQuery, currentCategoryFilter);

        products.clear();
        products.addAll(pageData);
    }

    private void applyFilters() {
        currentCategoryFilter = categoryFilter.getValue();
        pagination.resetToFirstPage();
        loadProductsPage();
    }

    private void setupPermissions() {
        canManageProducts = Session.getInstance().canManageStore();
        addButton.setDisable(!canManageProducts);
    }

    private void setupSelectionListener() {
        // Selection listener for potential future use
    }

    private void loadProducts() {
        loadProductsPage();
    }

    @FXML
    private void handleAdd() {
        if (!canManageProducts) {
            NotificationUtil.warning("Akses Ditolak", "Hanya Admin Toko yang dapat menambah produk.");
            return;
        }
        try {
            showProductDialog(null);
        } catch (Exception e) {
            com.baletpos.util.ModalUtil.showFriendlyError("Waduh, ada kendala teknis!", e);
        }
    }

    @FXML
    private void handleEdit() {
        if (!canManageProducts) {
            NotificationUtil.warning("Akses Ditolak", "Hanya Admin Toko yang dapat mengubah produk.");
            return;
        }
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showProductDialog(selected);
        }
    }

    /**
     * Confirm and delete a product (called from row actions)
     */
    private void confirmAndDelete(Product product) {
        if (product == null)
            return;

        if (!canManageProducts) {
            NotificationUtil.warning("Akses Ditolak", "Hanya Admin Toko yang dapat menghapus produk.");
            return;
        }

        try {
            // Security Check
            boolean authorized = com.baletpos.util.SecurityUtil
                    .requestAdminConfirmation("Hapus Produk " + product.getSku());
            if (!authorized)
                return;

            boolean confirm = com.baletpos.util.ModalUtil.showConfirmDanger(
                    "Hapus Produk Permanen-",
                    "Anda akan menghapus \"" + product.getName() + "\".\n\nTindakan ini tidak dapat dibatalkan.");

            if (confirm) {
                product.setActive(false);
                productDAO.save(product);
                loadProducts();
                loadSummaryStats();
                NotificationUtil.success("Berhasil", "Produk berhasil dihapus");
            }
        } catch (Exception e) {
            com.baletpos.util.ModalUtil.showFriendlyError("Waduh, ada kendala teknis!", e);
        }
    }

    @FXML
    private void handleDelete() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        confirmAndDelete(selected);
    }

    @FXML
    private void handleClearFilter() {
        searchField.clear();
        categoryFilter.setValue("SEMUA");
        applyFilters();
    }

    @FXML
    private void handleImport() {
        if (!Session.getInstance().canManageStore()) {
            NotificationUtil.warning("Akses Ditolak", "Hanya Admin Toko yang dapat import data.");
            return;
        }

        // Show custom import dialog using ModalUtil pattern
        showImportOptionsDialog();
    }

    private void showImportOptionsDialog() {
        javafx.stage.Stage modal = new javafx.stage.Stage();
        com.baletpos.util.ModalUtil.applyModalDefaults(modal);
        modal.initStyle(javafx.stage.StageStyle.UNDECORATED);
        modal.setResizable(false);

        VBox content = new VBox(16);
        content.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        content.setPadding(new Insets(28, 32, 24, 32));
        content.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 16; " +
                        "-fx-border-radius: 16; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 8);");
        content.setMinWidth(380);

        Label titleLabel = new Label(" Import Produk & Stok");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        Label msgLabel = new Label("SKU baru akan dibuat sebagai produk baru. SKU yang sudah ada akan update stok, HPP, margin, harga, nama, dan tipe produk.");
        msgLabel.setWrapText(true);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #475569;");

        Button btnTemplate = new Button(" Download Template Excel");
        btnTemplate.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 12 20; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: #f1f5f9; -fx-text-fill: #1e293b; " +
                        "-fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        btnTemplate.setMaxWidth(Double.MAX_VALUE);
        btnTemplate.setOnAction(e -> {
            modal.close();
            downloadExcelTemplate();
        });

        Button btnImport = new Button(" Import File Excel");
        btnImport.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 12 20; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: #1E40AF; -fx-text-fill: white;");
        btnImport.setMaxWidth(Double.MAX_VALUE);
        btnImport.setOnAction(e -> {
            modal.close();
            importExcelFile();
        });

        Button btnCancel = new Button("Batal");
        btnCancel.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 10 24; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: transparent; -fx-text-fill: #64748b; " +
                        "-fx-border-color: #cbd5e1; -fx-border-radius: 8;");
        btnCancel.setOnAction(e -> modal.close());

        HBox btnBox = new HBox(btnCancel);
        btnBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnBox.setPadding(new Insets(8, 0, 0, 0));

        content.getChildren().addAll(titleLabel, msgLabel, btnTemplate, btnImport, btnBox);

        modal.setScene(new javafx.scene.Scene(content));
        modal.showAndWait();
    }

    private void downloadExcelTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Simpan Template Excel");
        fileChooser.setInitialFileName("template_import_produk.xlsx");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                com.baletpos.service.ExcelImportService service = new com.baletpos.service.ExcelImportService();
                service.generateTemplate(file);
                NotificationUtil.success("Berhasil", "Template Excel berhasil disimpan di:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                NotificationUtil.error("Gagal", "Tidak dapat membuat template: " + e.getMessage());
            }
        }
    }

    private void importExcelFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Produk Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                com.baletpos.service.ExcelImportService service = new com.baletpos.service.ExcelImportService();
                com.baletpos.service.ExcelImportService.ImportResult result = service.importProducts(file);

                StringBuilder msg = new StringBuilder();
                msg.append(result.getSummary());

                if (result.getUpdatedCount() > 0) {
                    msg.append("\n\nDiupdate:\n");
                    for (String s : result.getUpdatedList()) {
                        msg.append(" ").append(s).append("\n");
                    }
                }
                if (result.getSkippedCount() > 0) {
                    msg.append("\n\nDilewati:\n");
                    for (String s : result.getSkippedList()) {
                        msg.append(" ").append(s).append("\n");
                    }
                }
                if (result.getFailedCount() > 0) {
                    msg.append("\n\nGagal:\n");
                    for (String s : result.getFailedList()) {
                        msg.append(" ").append(s).append("\n");
                    }
                }

                if (result.getSuccessCount() > 0 || result.getUpdatedCount() > 0) {
                    NotificationUtil.success("Import Selesai", msg.toString());
                    applyFilters();
                    loadSummaryStats();
                } else {
                    NotificationUtil.warning("Import Selesai", msg.toString());
                }
            } catch (Exception e) {
                NotificationUtil.error("Gagal Import", e.getMessage());
            }
        }
    }

    @FXML
    private void handleRefresh() {
        loadProducts();
        searchField.clear();
        categoryFilter.setValue("SEMUA");
        NotificationUtil.infoShort("Data produk diperbarui");
    }

    private void showProductDialog(Product product) {
        javafx.stage.Stage modal = new javafx.stage.Stage();
        com.baletpos.util.ModalUtil.applyModalDefaults(modal);
        modal.initStyle(javafx.stage.StageStyle.TRANSPARENT);

        // --- Container ---
        VBox root = new VBox();
        root.setStyle(
                "-fx-background-color: white; -fx-background-radius: 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 20, 0, 0, 0);");
        root.setPadding(new Insets(0));
        root.setPrefWidth(600);

        // --- Header ---
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(24, 32, 24, 32));
        header.setStyle("-fx-border-color: #f1f5f9; -fx-border-width: 0 0 1 0;");

        VBox titleBox = new VBox(4);
        Label title = new Label(product == null ? "Tambah Produk" : "Edit Produk");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label(
                product == null ? "Masukkan informasi produk baru di bawah ini." : "Perbarui informasi produk.");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(title, subtitle);

        javafx.scene.shape.SVGPath closeIcon = new javafx.scene.shape.SVGPath();
        closeIcon.setContent("M6 18L18 6M6 6l12 12");
        closeIcon.setStroke(javafx.scene.paint.Color.web("#94a3b8"));
        closeIcon.setStrokeWidth(2);

        Button closeBtn = new Button();
        closeBtn.setGraphic(closeIcon);
        closeBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> modal.close());

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        header.getChildren().addAll(titleBox, spacer, closeBtn);

        // --- Content (Grid) ---
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(32));
        grid.setHgap(24);
        grid.setVgap(20);

        // Column Constraints
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setPercentWidth(50);
        javafx.scene.layout.ColumnConstraints col2 = new javafx.scene.layout.ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        // Fields
        TextField skuField = createStyledTextField(product == null ? "" : product.getSku(), "SKU Produk");
        skuField.setEditable(product == null);

        TextField nameField = createStyledTextField(product == null ? "" : product.getName(), "Nama Produk Lengkap");

        ComboBox<Product.ProductType> typeCombo = new ComboBox<>(
                FXCollections.observableArrayList(Product.ProductType.values()));
        typeCombo.setPromptText("Pilih Tipe");
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setStyle(
                "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 12; -fx-font-size: 14px;");
        if (product != null)
            typeCombo.setValue(product.getProductType());

        TextField hppField = createStyledTextField(product == null ? "" : String.valueOf(product.getHpp().intValue()),
                "0");

        TextField marginField = createStyledTextField(
                product == null ? "15" : String.valueOf(product.getMarginPercent()), "15");

        TextField pricePreview = createStyledTextField("", "Auto Calculate");
        pricePreview.setEditable(false);
        pricePreview.setStyle(pricePreview.getStyle()
                + "-fx-background-color: #f1f5f9; -fx-text-fill: #1E40AF; -fx-font-weight: bold;");

        TextField stockField = createStyledTextField(product == null ? "0" : String.valueOf(product.getStock()), "0");

        AtomicReference<String> imagePathRef = new AtomicReference<>(
                product != null ? product.getImagePath() : null);
        ImageView imagePreview = new ImageView(ImageUtil.loadProductThumbnail(imagePathRef.get()));
        imagePreview.setFitWidth(80);
        imagePreview.setFitHeight(80);
        imagePreview.setPreserveRatio(true);

        Button uploadImageBtn = new Button("Upload Foto");
        uploadImageBtn.setStyle(
                "-fx-background-color: #1E40AF; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 14; -fx-font-weight: 600; -fx-cursor: hand;");
        Button clearImageBtn = new Button("Hapus Foto");
        clearImageBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #1E40AF; -fx-border-color: #1E40AF; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 14; -fx-font-weight: 600; -fx-cursor: hand;");

        Label imageHint = new Label("Maks 2MB (Diubah ke 300x300)");
        imageHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");

        uploadImageBtn.setOnAction(e -> {
            String skuValue = skuField.getText().trim();
            if (skuValue.isEmpty()) {
                NotificationUtil.warning("Validasi", "SKU wajib diisi sebelum upload foto.");
                return;
            }
            FileChooser fc = new FileChooser();
            fc.setTitle("Pilih Foto Produk");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.jpg", "*.jpeg", "*.png", "*.webp"));
            File file = fc.showOpenDialog(modal);
            if (file == null) {
                return;
            }
            if (!ImageUtil.isValidExtension(file)) {
                NotificationUtil.warning("Validasi", "Format gambar harus JPG/PNG/WEBP.");
                return;
            }
            String savedPath = ImageUtil.saveProductImage(file, skuValue);
            if (savedPath == null) {
                NotificationUtil.error("Error", "Gagal menyimpan foto.");
                return;
            }
            imagePathRef.set(savedPath);
            imagePreview.setImage(ImageUtil.loadProductThumbnail(savedPath));
        });

        clearImageBtn.setOnAction(e -> {
            imagePathRef.set(null);
            imagePreview.setImage(ImageUtil.loadProductThumbnail(null));
        });

        VBox imageBtnBox = new VBox(8, uploadImageBtn, clearImageBtn, imageHint);
        imageBtnBox.setAlignment(Pos.CENTER_LEFT);
        HBox imageBox = new HBox(12, imagePreview, imageBtnBox);
        imageBox.setAlignment(Pos.CENTER_LEFT);

        TextArea descArea = new TextArea(product == null ? "" : product.getDescription());
        descArea.setPromptText("Deskripsi produk (opsional)");
        descArea.setPrefRowCount(3);
        descArea.setWrapText(true);
        descArea.setStyle(
                "-fx-control-inner-background: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 4; -fx-font-size: 14px; -fx-faint-focus-color: transparent;");

        // Layout Place
        // Left Col
        grid.add(createInputGroup("SKU", skuField), 0, 0);
        grid.add(createInputGroup("Nama Produk", nameField), 0, 1);
        grid.add(createInputGroup("Tipe / Kategori", typeCombo), 0, 2);
        grid.add(createInputGroup("Deskripsi", descArea), 0, 4, 2, 1); // Span 2 cols

        // Right Col
        grid.add(createInputGroup("HPP (Harga Modal)", hppField), 1, 0);

        HBox marginPriceBox = new HBox(12); // Split HPP row
        VBox marginBox = createInputGroup("Margin (%)", marginField);
        VBox stockBox = createInputGroup("Stok Awal", stockField);
        HBox.setHgrow(marginBox, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(stockBox, javafx.scene.layout.Priority.ALWAYS);
        marginPriceBox.getChildren().addAll(marginBox, stockBox);

        grid.add(marginPriceBox, 1, 1);
        grid.add(createInputGroup("Estimasi Harga Jual", pricePreview), 1, 2);
        grid.add(createInputGroup("Foto Produk", imageBox), 1, 3);

        // Logic
        Runnable calcPrice = () -> {
            try {
                String hppStr = hppField.getText().replaceAll("[^0-9]", "");
                if (hppStr.isEmpty())
                    return;
                double hpp = Double.parseDouble(hppStr);
                double margin = Double.parseDouble(marginField.getText().isEmpty() ? "0" : marginField.getText());
                double sell = hpp + (hpp * margin / 100.0);
                pricePreview.setText(currencyFormat.format(sell));
            } catch (Exception e) {
            }
        };
        hppField.textProperty().addListener((o, old, newVal) -> calcPrice.run());
        marginField.textProperty().addListener((o, old, newVal) -> calcPrice.run());
        if (product != null)
            calcPrice.run();

        // --- Footer ---
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(24, 32, 32, 32));
        footer.setStyle(
                "-fx-background-color: #f8fafc; -fx-background-radius: 0 0 16 16; -fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        Button btnCancel = new Button("Batal");
        btnCancel.setStyle(
                "-fx-background-color: white; -fx-text-fill: #475569; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 24; -fx-font-weight: 600; -fx-cursor: hand;");
        btnCancel.setOnAction(e -> modal.close());

        Button btnSave = new Button("Simpan Produk");
        btnSave.setStyle(
                "-fx-background-color: #1E40AF; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 10 24; -fx-font-weight: 600; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(37,99,235,0.3), 10, 0, 0, 4);");
        btnSave.setOnAction(e -> {
            // Validation
            if (skuField.getText().isBlank() || nameField.getText().isBlank() || typeCombo.getValue() == null) {
                NotificationUtil.warning("Validasi", "SKU, Nama, dan Tipe wajib diisi");
                return;
            }
            try {
                String skuValue = skuField.getText().trim().toUpperCase();
                if (product == null && productDAO.findBySku(skuValue).isPresent()) {
                    NotificationUtil.warning("Validasi", "SKU sudah digunakan. Gunakan SKU lain.");
                    return;
                }

                Product result = product != null ? product : new Product();
                if (product == null)
                    result.setSku(skuValue);
                result.setName(nameField.getText().trim());
                result.setProductType(typeCombo.getValue());
                result.setHpp(new BigDecimal(hppField.getText().replaceAll("[^0-9]", "")));
                result.setMarginPercent(Double.parseDouble(marginField.getText()));
                int stockVal = Integer.parseInt(stockField.getText());
                if (stockVal < 0) {
                    NotificationUtil.warning("Validasi", "Stok tidak boleh negatif");
                    return;
                }
                if (result.getId() == null)
                    result.setStock(stockVal);
                result.setDescription(descArea.getText().trim());
                result.setActive(true);

                Long categoryId = resolveCategoryId(result.getProductType());
                if (categoryId == null) {
                    NotificationUtil.warning("Validasi", "Kategori belum tersedia di database");
                    return;
                }
                result.setCategoryId(categoryId);

                result.setImagePath(imagePathRef.get());

                productDAO.save(result);

                if (product == null && stockVal > 0 && result.getProductType() != Product.ProductType.SERVICE) {
                    try (Connection conn = DatabaseConfig.getConnection();
                            PreparedStatement ps = conn.prepareStatement(
                                    "INSERT INTO stock_movements (product_id, movement_type, reference_type, reference_id, quantity_change, stock_before, stock_after, notes, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setLong(1, result.getId());
                        ps.setString(2, "INITIAL");
                        ps.setString(3, "PRODUCT");
                        ps.setLong(4, result.getId());
                        ps.setInt(5, stockVal);
                        ps.setInt(6, 0);
                        ps.setInt(7, stockVal);
                        ps.setString(8, "Initial stock saat produk dibuat");
                        User current = Session.getInstance().getCurrentUser();
                        ps.setLong(9, current != null ? current.getId() : 1L);
                        ps.executeUpdate();
                    } catch (Exception ex) {
                        NotificationUtil.warning("Info", "Stok awal tersimpan, namun gagal mencatat mutasi awal.");
                    }
                }
                loadProducts();
                NotificationUtil.success("Berhasil", "Produk berhasil disimpan");
                modal.close();
            } catch (Exception ex) {
                NotificationUtil.error("Error", "Gagal menyimpan: " + ex.getMessage());
            }
        });

        footer.getChildren().addAll(btnCancel, btnSave);

        root.getChildren().addAll(header, grid, footer);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        modal.setScene(scene);
        modal.showAndWait();
    }

    private TextField createStyledTextField(String text, String prompt) {
        TextField tf = new TextField(text);
        tf.setPromptText(prompt);
        tf.setStyle(
                "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
        tf.focusedProperty().addListener((obs, old, isFocused) -> {
            if (isFocused) {
                tf.setStyle(
                        "-fx-background-color: white; -fx-border-color: #1E40AF; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 14px; -fx-text-fill: #1e293b; -fx-effect: dropshadow(three-pass-box, rgba(59,130,246,0.1), 4, 0, 0, 0);");
            } else {
                tf.setStyle(
                        "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
            }
        });
        return tf;
    }

    private VBox createInputGroup(String labelText, javafx.scene.Node input) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        VBox box = new VBox(8, label, input);
        return box;
    }

    private Long resolveCategoryId(Product.ProductType type) {
        if (type == null)
            return null;
        String code = switch (type) {
            case LAPTOP_NEW, LAPTOP_SECOND -> "LAPTOP";
            case SPAREPARTS -> "SPAREPART";
            case PERIPHERAL -> "PERIPHERAL";
            case SERVICE -> "SERVICE";
        };

        try (java.sql.Connection conn = com.baletpos.config.DatabaseConfig.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT id FROM categories WHERE code = ?")) {
            ps.setString(1, code);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return rs.getLong(1);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}


