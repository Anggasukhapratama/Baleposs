package com.baletpos.controller;

import com.baletpos.dao.ProductDAO;
import com.baletpos.dao.SaleDAO;
import com.baletpos.model.Product;
import com.baletpos.model.Sale;
import com.baletpos.model.SaleItem;
import com.baletpos.model.SalePayment;
import com.baletpos.model.User;

import com.baletpos.util.Session;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class POSController {

    @FXML
    private TextField searchProductField;
    @FXML
    private HBox categoryTabsContainer;
    @FXML
    private VBox cartItemsContainer;
    @FXML
    private Pagination pagination;
    @FXML
    private Button backButton;

    // Payment Panel
    @FXML
    private Label subtotalLabel;
    @FXML
    private TextField discountPercentField;
    @FXML
    private Label grandTotalLabel;
    @FXML
    private VBox paymentMethodsContainer;
    @FXML
    private Label changeLabel;
    @FXML
    private Button payPrintButton;
    @FXML
    private Button addPaymentBtn;
    @FXML
    private Label cartCountLabel;

    // Optional header fields
    @FXML
    private Label clockLabel;
    @FXML
    private Label userLabel;
    @FXML
    private Label catalogCountLabel;

    private final ProductDAO productDAO = new ProductDAO();
    private final SaleDAO saleDAO = new SaleDAO();

    private final ObservableList<SaleItem> cartItems = FXCollections.observableArrayList();
    private final List<PaymentRow> paymentRows = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    private List<Product> allProductsHelper;
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "SEMUA";
    private BigDecimal currentGrandTotal = BigDecimal.ZERO;
    private static final int ITEMS_PER_PAGE = 12;

    private DashboardController dashboardController;

    public void setDashboardController(DashboardController controller) {
        this.dashboardController = controller;
    }

    @FXML
    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);

        setupCartList();
        setupInputs();
        loadProductCatalog();
        populateCategoryChips();
        updateHeader();

        // Clear and add initial payment row (prevent duplicates) paymentRows.clear();
        paymentMethodsContainer.getChildren().clear();
        addPaymentRow();

        Platform.runLater(() -> searchProductField.requestFocus());
    }

    private void updateHeader() {
        if (Session.getInstance().isLoggedIn() && userLabel != null) {
            userLabel.setText(Session.getInstance().getCurrentUser().getFullName());
        }
        if (clockLabel != null) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", new Locale("id", "ID"));
            clockLabel.setText(java.time.LocalDateTime.now().format(dtf));
        }
    }

    // ========== PAYMENT ROW MANAGEMENT ==========

    private class PaymentRow {
        HBox container;
        ComboBox<Sale.PaymentMethod> methodCombo;
        TextField amountField;
        Button removeBtn;

        PaymentRow() {
            container = new HBox(6);
            container.setAlignment(Pos.CENTER_LEFT);
            container.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 6 8; -fx-background-radius: 6;");

            methodCombo = new ComboBox<>();
            methodCombo.setItems(FXCollections.observableArrayList(
                    Sale.PaymentMethod.CASH,
                    Sale.PaymentMethod.TRANSFER_BCA,
                    Sale.PaymentMethod.TRANSFER_MANDIRI,
                    Sale.PaymentMethod.QRIS,
                    Sale.PaymentMethod.AKULAKU,
                    Sale.PaymentMethod.KREDIVO));

            methodCombo.getSelectionModel().selectFirst();
            methodCombo.setPrefWidth(120); // Sedikit diperlebar
            methodCombo.setStyle(
                    "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-font-size: 11px;");

            // Display Converter (Show "Transfer BCA" instead of "TRANSFER_BCA")
            methodCombo.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(Sale.PaymentMethod p) {
                    return p == null ? "" : (p.getDisplayName() != null ? p.getDisplayName() : p.name());
                }

                @Override
                public Sale.PaymentMethod fromString(String string) {
                    return null; // Not needed for ComboBox
                }
            });

            amountField = new TextField();
            amountField.setPromptText("Nominal");
            HBox.setHgrow(amountField, Priority.ALWAYS);
            amountField.setStyle(
                    "-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 4; -fx-font-size: 12px; -fx-padding: 6;");
            amountField.textProperty().addListener((obs, o, n) -> updateChange());
            HBox.setHgrow(amountField, Priority.ALWAYS);

            removeBtn = new Button("X");
            removeBtn.setStyle(
                    "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626; -fx-background-radius: 6; -fx-cursor: hand; -fx-min-width: 32; -fx-min-height: 32;");
            removeBtn.setOnAction(e -> removePaymentRow(this));

            container.getChildren().addAll(methodCombo, amountField, removeBtn);

            // Apply Currency Format
            setupCurrencyFormatter(amountField);
        }

        String getMethod() {
            return methodCombo.getValue() != null ? methodCombo.getValue().name() : "CASH";
        }

        BigDecimal getAmount() {
            try {
                String txt = amountField.getText().replaceAll("[^0-9]", "");
                return txt.isEmpty() ? BigDecimal.ZERO : new BigDecimal(txt);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
    }

    private void addPaymentRow() {
        PaymentRow row = new PaymentRow();
        paymentRows.add(row);
        paymentMethodsContainer.getChildren().add(row.container);
        updateRemoveButtonVisibility();
    }

    private void removePaymentRow(PaymentRow row) {
        if (paymentRows.size() <= 1)
            return;
        paymentRows.remove(row);
        paymentMethodsContainer.getChildren().remove(row.container);
        updateRemoveButtonVisibility();
        updateChange();
    }

    private void updateRemoveButtonVisibility() {
        boolean showRemove = paymentRows.size() > 1;
        for (PaymentRow row : paymentRows) {
            row.removeBtn.setVisible(showRemove);
            row.removeBtn.setManaged(showRemove);
        }
    }

    @FXML
    private void handleAddPaymentMethod() {
        addPaymentRow();
    }

    // ========== BACK & CLEAR ==========

    @FXML
    private void handleBack() {
        if (!cartItems.isEmpty()) {
            boolean confirm = com.baletpos.util.ModalUtil.showConfirm(
                    "Konfirmasi Keluar",
                    "Keranjang masih berisi item. Keluar dan kosongkan keranjang");
            if (confirm) {
                if (dashboardController != null)
                    dashboardController.returnToDashboard();
            }
        } else {
            if (dashboardController != null)
                dashboardController.returnToDashboard();
        }
    }

    @FXML
    private void handleClearCart() {
        try {
            if (cartItems.isEmpty()) {
                searchProductField.requestFocus();
                return;
            }
            boolean confirm = com.baletpos.util.ModalUtil.showConfirm(
                    "Reset Keranjang",
                    "Kosongkan semua item di keranjang");
            if (confirm) {
                cartItems.clear();
                discountPercentField.setText("");
                resetPaymentRows();
            }
        } catch (Exception e) {
            com.baletpos.util.ModalUtil.showFriendlyError("Waduh, ada kendala teknis!", e);
        }
    }

    private void resetPaymentRows() {
        paymentRows.clear();
        paymentMethodsContainer.getChildren().clear();
        addPaymentRow();
    }

    // ========== PRODUCT CATALOG ==========

    @FXML
    public void loadProductCatalog() {
        try {
            allProductsHelper = productDAO.findAll();
            currentSearchQuery = "";
            currentCategoryFilter = "SEMUA";
            setupPagination();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Gagal memuat produk: " + e.getMessage());
        }
    }

    private void populateCategoryChips() {
        categoryTabsContainer.getChildren().clear();

        ToggleGroup group = new ToggleGroup();
        ToggleButton allBtn = createCategoryChip("Semua", group, true);
        allBtn.setUserData(null);
        allBtn.setOnAction(e -> filterByCategory(null));
        categoryTabsContainer.getChildren().add(allBtn);

        try {
            List<String> types = productDAO.findAll().stream().map(p -> p.getProductType().name()).distinct()
                    .collect(Collectors.toList());

            for (String type : types) {
                String label = mapTypeToFriendlyName(type);
                ToggleButton btn = createCategoryChip(label, group, false);
                btn.setUserData(type);
                btn.setOnAction(e -> filterByCategory((String) btn.getUserData()));
                categoryTabsContainer.getChildren().add(btn);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ToggleButton createCategoryChip(String text, ToggleGroup group, boolean selected) {
        ToggleButton btn = new ToggleButton(text);
        btn.setToggleGroup(group);
        btn.setSelected(selected);
        String selectedStyle = "-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; -fx-font-weight: 900; -fx-border-color: #93c5fd; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 9 15; -fx-cursor: hand;";
        String normalStyle = "-fx-background-color: white; -fx-text-fill: #334155; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 9 15; -fx-cursor: hand; -fx-font-weight: 800;";
        btn.setStyle(selected ? selectedStyle : normalStyle);

        btn.selectedProperty().addListener((obs, was, is) -> {
            btn.setStyle(is ? selectedStyle : normalStyle);
        });
        return btn;
    }

    private void filterByCategory(String type) {
        if (type == null || type.isEmpty()) {
            currentCategoryFilter = "SEMUA";
        } else {
            currentCategoryFilter = type;
        }
        setupPagination();
    }

    private void setupPagination() {
        int total = productDAO.countFiltered(currentSearchQuery, currentCategoryFilter);
        if (total <= 0) {
            pagination.setPageCount(1);
            pagination.setPageFactory(this::createEmptyPage);
            return;
        }

        int pageCount = (int) Math.ceil((double) total / ITEMS_PER_PAGE);
        pagination.setPageCount(pageCount);
        pagination.setCurrentPageIndex(0);
        pagination.setPageFactory(this::createProductPage);
    }

    private Node createEmptyPage(int pageIndex) {
        VBox box = new VBox(20);
        box.setAlignment(Pos.CENTER);
        Label lbl = new Label("Tidak ada produk ditemukan");
        lbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
        box.getChildren().add(lbl);
        return box;
    }

    private Node createProductPage(int pageIndex) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(0));
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setMaxWidth(Double.MAX_VALUE);

        grid.getColumnConstraints().clear();
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setFillWidth(true);
            grid.getColumnConstraints().add(cc);
        }

        int offset = pageIndex * ITEMS_PER_PAGE;
        List<Product> pageProducts = productDAO.findAllPaged(
                ITEMS_PER_PAGE,
                offset,
                currentSearchQuery,
                currentCategoryFilter);

        for (int i = 0; i < pageProducts.size(); i++) {
            VBox card = createProductCard(pageProducts.get(i));
            int col = i % 4;
            int row = i / 4;
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setVgrow(card, Priority.NEVER);
            grid.add(card, col, row);
        }
        return grid;
    }

    private VBox createProductCard(Product p) {
        VBox card = new VBox(10);
        card.getStyleClass().add("product-card");
        String baseStyle = "-fx-background-color: white; -fx-border-color: #dce3ea; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14; -fx-pref-width: 200; -fx-min-height: 176; -fx-cursor: hand;";
        String hoverStyle = "-fx-background-color: #f8fbff; -fx-border-color: #2563eb; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14; -fx-pref-width: 200; -fx-min-height: 176; -fx-cursor: hand;";
        card.setStyle(baseStyle);
        card.setMaxWidth(Double.MAX_VALUE);

        // 1. Info Section
        Label nameLbl = new Label(p.getName());
        nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: 800; -fx-text-fill: #1e293b;");
        nameLbl.setWrapText(true);
        nameLbl.setMinHeight(40);
        nameLbl.setMaxHeight(40);
        nameLbl.setAlignment(Pos.TOP_LEFT);

        Label metaLbl = new Label(p.getSku() + " - " + mapTypeToFriendlyName(p.getProductType().name()));
        metaLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Label priceLbl = new Label(currencyFormat.format(p.getSellingPrice()));
        priceLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: #0f766e;");

        // 2. Stock Badge
        HBox badgeRow = new HBox(6);
        badgeRow.setAlignment(Pos.CENTER_LEFT);
        Label stockLbl = new Label("Stok: " + p.getStock());

        boolean disabled = false;
        if (p.getStock() <= 0) {
            stockLbl.setText("HABIS");
            stockLbl.setStyle(
                    "-fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-text-fill: white; -fx-background-color: #dc2626; -fx-font-weight: 800;");
            card.setOpacity(0.6);
            disabled = true;
        } else if (p.getStock() < 5) {
            stockLbl.setText("Sisa " + p.getStock());
            stockLbl.setStyle(
                    "-fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-text-fill: #92400e; -fx-background-color: #fef3c7; -fx-font-weight: 800;");
        } else {
            stockLbl.setStyle(
                    "-fx-font-size: 10px; -fx-padding: 4 8; -fx-background-radius: 4; -fx-text-fill: #166534; -fx-background-color: #dcfce7; -fx-font-weight: 800;");
        }
        badgeRow.getChildren().add(stockLbl);

        card.getChildren().addAll(nameLbl, metaLbl, priceLbl, badgeRow);
        Tooltip.install(card, new Tooltip(p.getName() + " | SKU: " + p.getSku()));

        if (!disabled) {
            card.setOnMouseClicked(e -> {
                addItemToCart(p);
                // Click Bounce Animation
                javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(
                        javafx.util.Duration.millis(100), card);
                st.setFromX(1.0);
                st.setFromY(1.0);
                st.setToX(0.95);
                st.setToY(0.95);
                st.setAutoReverse(true);
                st.setCycleCount(2);
                st.play();
            });

            card.setOnMouseEntered(e -> {
                card.setStyle(hoverStyle);
            });
            card.setOnMouseExited(e -> {
                card.setStyle(baseStyle);
            });
        } else {
            card.setOnMouseClicked(
                    e -> com.baletpos.util.NotificationUtil.warning("Stok Habis", "Produk ini sedang kosong."));
        }
        return card;
    }

    // ========== CART ==========

    private void setupCartList() {
        cartItems.addListener((ListChangeListener<SaleItem>) c -> {
            renderCart();
            calculateTotals();
            if (cartCountLabel != null)
                cartCountLabel.setText(cartItems.size() + " item");
        });
        renderCart();
    }

    private void renderCart() {
        cartItemsContainer.getChildren().clear();
        if (cartItems.isEmpty()) {
            VBox p = new VBox(16);
            p.setAlignment(Pos.CENTER);
            p.setPadding(new Insets(60, 0, 40, 0));
            Label icon = new Label("Keranjang");
            icon.setStyle("-fx-font-size: 18px; -fx-font-weight: 900; -fx-text-fill: #94a3b8;");
            Label l = new Label("Keranjang Kosong");
            l.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 15px; -fx-font-weight: 600;");
            Label l2 = new Label("Klik produk untuk menambahkan");
            l2.setStyle("-fx-text-fill: #cbd5e1; -fx-font-size: 12px;");
            p.getChildren().addAll(icon, l, l2);
            cartItemsContainer.getChildren().add(p);
            return;
        }

        for (SaleItem item : cartItems) {
            cartItemsContainer.getChildren().add(createCartItemRow(item));
        }
    }

    private Node createCartItemRow(SaleItem item) {
        VBox container = new VBox(8);
        container.setStyle(
                "-fx-background-color: white; -fx-padding: 14 14; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox info = new VBox(3);
        Label name = new Label(item.getProductName());
        name.setStyle("-fx-font-weight: 800; -fx-text-fill: #1e293b; -fx-font-size: 12px;");
        name.setWrapText(true);
        name.setMaxWidth(180);

        Label price = new Label("@ " + currencyFormat.format(item.getUnitPrice()));
        price.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
        info.getChildren().addAll(name, price);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button remove = new Button("X");
        remove.setStyle(
                "-fx-background-color: rgba(239,68,68,0.1); -fx-text-fill: #dc2626; -fx-cursor: hand; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; " +
                        "-fx-min-width: 26; -fx-min-height: 26; -fx-max-width: 26; -fx-max-height: 26;");
        remove.setOnMouseEntered(e -> remove.setStyle(
                "-fx-background-color: #dc2626; -fx-text-fill: white; -fx-cursor: hand; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; " +
                        "-fx-min-width: 26; -fx-min-height: 26; -fx-max-width: 26; -fx-max-height: 26;"));
        remove.setOnMouseExited(e -> remove.setStyle(
                "-fx-background-color: rgba(239,68,68,0.1); -fx-text-fill: #dc2626; -fx-cursor: hand; " +
                        "-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6; " +
                        "-fx-min-width: 26; -fx-min-height: 26; -fx-max-width: 26; -fx-max-height: 26;"));
        remove.setOnAction(e -> {
            try {
                cartItems.remove(item);
            } catch (Exception ex) {
                com.baletpos.util.ModalUtil.showFriendlyError("Waduh, ada kendala teknis!", ex);
            }
        });
        topRow.getChildren().addAll(info, remove);

        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        // Compact Stepper
        HBox stepper = new HBox(0);
        stepper.setAlignment(Pos.CENTER);
        stepper.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 6; -fx-border-color: #e2e8f0; -fx-border-radius: 6;");

        Button minus = new Button("-");
        String minusBtnNormal = "-fx-background-color: rgba(100,116,139,0.1); -fx-cursor: hand; -fx-padding: 4 10; " +
                "-fx-text-fill: #475569; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6 0 0 6;";
        String minusBtnHover = "-fx-background-color: #475569; -fx-cursor: hand; -fx-padding: 4 10; " +
                "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 6 0 0 6;";
        minus.setStyle(minusBtnNormal);
        minus.setOnMouseEntered(e -> minus.setStyle(minusBtnHover));
        minus.setOnMouseExited(e -> minus.setStyle(minusBtnNormal));
        minus.setOnAction(e -> decreaseQty(item));

        Label qty = new Label(String.valueOf(item.getQuantity()));
        qty.setStyle(
                "-fx-padding: 0 8; -fx-font-weight: bold; -fx-min-width: 28; -fx-alignment: center; " +
                        "-fx-font-size: 13px; -fx-text-fill: #1e293b;");

        Button plus = new Button("+");
        String plusBtnNormal = "-fx-background-color: rgba(37,99,235,0.12); -fx-cursor: hand; -fx-padding: 4 10; " +
                "-fx-text-fill: #1E40AF; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 0 6 6 0;";
        String plusBtnHover = "-fx-background-color: #1E40AF; -fx-cursor: hand; -fx-padding: 4 10; " +
                "-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-background-radius: 0 6 6 0;";
        plus.setStyle(plusBtnNormal);
        plus.setOnMouseEntered(e -> plus.setStyle(plusBtnHover));
        plus.setOnMouseExited(e -> plus.setStyle(plusBtnNormal));
        plus.setOnAction(e -> increaseQty(item));
        stepper.getChildren().addAll(minus, qty, plus);

        Label subtotal = new Label(currencyFormat.format(item.getSubtotal()));
        subtotal.setStyle("-fx-text-fill: #0f766e; -fx-font-weight: 900; -fx-font-size: 12px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bottomRow.getChildren().addAll(stepper, spacer, subtotal);

        container.getChildren().addAll(topRow, bottomRow);

        // Extra fields for Laptop: SN and buyer name.
        boolean isLaptop = item.getProductType() == Product.ProductType.LAPTOP_NEW
                || item.getProductType() == Product.ProductType.LAPTOP_SECOND;
        if (isLaptop) {
            VBox laptopFields = new VBox(8);
            laptopFields.setStyle(
                    "-fx-background-color: #f8fafc; -fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #dbeafe; -fx-border-radius: 8;");

            Label lblInfo = new Label("Detail Laptop");
            lblInfo.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #334155;");

            TextField snInput = new TextField(item.getSerialNumber());
            snInput.setPromptText("Serial Number");
            snInput.setStyle(
                    "-fx-font-size: 11px; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 6 8;");
            snInput.textProperty().addListener((obs, o, n) -> item.setSerialNumber(n));

            TextField namaInput = new TextField(item.getBuyerName());
            namaInput.setPromptText("Nama Pembeli");
            namaInput.setStyle(
                    "-fx-font-size: 11px; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 6 8;");
            namaInput.textProperty().addListener((obs, o, n) -> item.setBuyerName(n));

            ComboBox<Product> bonusCombo = new ComboBox<>();
            bonusCombo.setPromptText("Bonus peripheral");
            bonusCombo.setMaxWidth(Double.MAX_VALUE);
            bonusCombo.setItems(FXCollections.observableArrayList(getAvailableBonusProducts()));
            bonusCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Product product) {
                    if (product == null) {
                        return "";
                    }
                    return product.getName() + " (" + product.getStock() + " stok)";
                }

                @Override
                public Product fromString(String string) {
                    return null;
                }
            });
            bonusCombo.getSelectionModel().select(findSelectedBonus(item));
            bonusCombo.valueProperty().addListener((obs, oldProduct, selectedProduct) -> {
                if (selectedProduct == null) {
                    item.setBonusProductId(null);
                    item.setBonusProductName(null);
                } else {
                    item.setBonusProductId(selectedProduct.getId());
                    item.setBonusProductName(selectedProduct.getName());
                }
            });
            bonusCombo.setStyle(
                    "-fx-font-size: 11px; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-background-radius: 5;");

            ComboBox<String> warrantyCombo = new ComboBox<>();
            warrantyCombo.setPromptText("Garansi");
            warrantyCombo.setMaxWidth(Double.MAX_VALUE);
            warrantyCombo.setItems(FXCollections.observableArrayList("2 Minggu", "1 Tahun", "2 Tahun"));
            warrantyCombo.getSelectionModel().select(item.getWarrantyLabel());
            warrantyCombo.valueProperty().addListener((obs, oldWarranty, selectedWarranty) -> item.setWarrantyLabel(selectedWarranty));
            warrantyCombo.setStyle(
                    "-fx-font-size: 11px; -fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-background-radius: 5;");

            GridPane laptopGrid = new GridPane();
            laptopGrid.setHgap(8);
            laptopGrid.setVgap(8);
            ColumnConstraints leftColumn = new ColumnConstraints();
            leftColumn.setPercentWidth(50);
            ColumnConstraints rightColumn = new ColumnConstraints();
            rightColumn.setPercentWidth(50);
            laptopGrid.getColumnConstraints().addAll(leftColumn, rightColumn);

            laptopGrid.add(createCartField("Serial Number", snInput), 0, 0);
            laptopGrid.add(createCartField("Nama Pembeli", namaInput), 1, 0);
            laptopGrid.add(createCartField("Bonus dari Peripheral", bonusCombo), 0, 1);
            laptopGrid.add(createCartField("Garansi", warrantyCombo), 1, 1);

            laptopFields.getChildren().addAll(lblInfo, laptopGrid);
            container.getChildren().add(laptopFields);
        }
        return container;
    }

    private VBox createCartField(String labelText, Control input) {
        VBox field = new VBox(3);
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #64748b;");
        input.setMaxWidth(Double.MAX_VALUE);
        field.getChildren().addAll(label, input);
        return field;
    }

    private List<Product> getAvailableBonusProducts() {
        if (allProductsHelper == null) {
            return List.of();
        }
        return allProductsHelper.stream()
                .filter(product -> product.getProductType() == Product.ProductType.PERIPHERAL)
                .filter(product -> product.getStock() > 0)
                .collect(Collectors.toList());
    }

    private Product findSelectedBonus(SaleItem item) {
        if (item.getBonusProductId() == null || allProductsHelper == null) {
            return null;
        }
        return allProductsHelper.stream()
                .filter(product -> item.getBonusProductId().equals(product.getId()))
                .findFirst()
                .orElse(null);
    }

    private void increaseQty(SaleItem item) {
        Product p = allProductsHelper.stream().filter(prod -> prod.getId().equals(item.getProductId())).findFirst()
                .orElse(null);
        if (p != null && item.getQuantity() + 1 > p.getStock()) {
            showAlert("Maksimal stok: " + p.getStock());
            return;
        }
        item.setQuantity(item.getQuantity() + 1);
        recalculateLineItem(item);
        calculateTotals();
        renderCart();
    }

    private void decreaseQty(SaleItem item) {
        if (item.getQuantity() <= 1) {
            cartItems.remove(item);
        } else {
            item.setQuantity(item.getQuantity() - 1);
            recalculateLineItem(item);
            calculateTotals();
            renderCart();
        }
    }

    private void setupInputs() {
        searchProductField.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal.toLowerCase().trim();
            currentSearchQuery = q;
            setupPagination();
        });

        searchProductField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F4)
                handlePayAndPrint();
            else if (event.getCode() == KeyCode.ESCAPE)
                handleClearCart();
            else if (event.getCode() == KeyCode.F5)
                loadProductCatalog();
        });

        setupCurrencyFormatter(discountPercentField);
        discountPercentField.setPromptText("Rp 0");
        discountPercentField.textProperty().addListener((obs, oldVal, newVal) -> calculateTotals());
    }

    private void setupCurrencyFormatter(TextField tf) {
        tf.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty())
                return;

            // Avoid recursive loop
            if (newVal.startsWith("Rp ") && newVal.matches("Rp [0-9,.]+")) {
                return;
            }

            String clean = newVal.replaceAll("[^0-9]", "");
            if (clean.isEmpty())
                return;

            try {
                double parsed = Double.parseDouble(clean);
                // Use existing currencyFormat which handles locale
                String formatted = currencyFormat.format(parsed);

                if (!newVal.equals(formatted)) {
                    Platform.runLater(() -> {
                        tf.setText(formatted);
                        tf.positionCaret(formatted.length());
                    });
                }
            } catch (Exception e) {
            }
        });
    }

    private void addItemToCart(Product p) {
        try {
            if (p.getStock() <= 0) {
                showAlert("Stok Habis!");
                return;
            }

            Optional<SaleItem> existing = cartItems.stream()
                    .filter(i -> i.getProductId().equals(p.getId()))
                    .findFirst();

            if (existing.isPresent()) {
                SaleItem item = existing.get();
                if (item.getQuantity() + 1 > p.getStock()) {
                    showAlert("Stok tidak mencukupi!");
                    return;
                }
                item.setQuantity(item.getQuantity() + 1);
                recalculateLineItem(item);
                calculateTotals();
                renderCart();
            } else {
                SaleItem item = new SaleItem();
                item.setProductId(p.getId());
                item.setProductSku(p.getSku());
                item.setProductName(p.getName());
                item.setQuantity(1);
                item.setUnitPrice(p.getSellingPrice());
                item.setHppPerUnit(p.getHpp());
                item.setProductType(p.getProductType());
                item.setDiscountPercent(0.0);
                item.setDiscountAmount(BigDecimal.ZERO);
                recalculateLineItem(item);
                cartItems.add(item);
            }
        } catch (Exception e) {
            com.baletpos.util.ModalUtil.showFriendlyError("Waduh, ada kendala teknis!", e);
        }
    }

    private String mapTypeToFriendlyName(String type) {
        if (type == null)
            return "-";
        return switch (type) {
            case "LAPTOP_NEW" -> "Laptop Baru";
            case "LAPTOP_SECOND" -> "Laptop Bekas";
            case "SPAREPARTS" -> "Sparepart";
            case "PERIPHERAL" -> "Peripheral";
            case "SERVICE" -> "Service";
            default -> type;
        };
    }

    private void recalculateLineItem(SaleItem item) {
        BigDecimal qty = BigDecimal.valueOf(item.getQuantity());
        BigDecimal total = item.getUnitPrice().multiply(qty);
        item.setSubtotal(total);
        item.setDiscountAmount(BigDecimal.ZERO);
    }

    private void calculateTotals() {
        BigDecimal subtotal = cartItems.stream().map(SaleItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        subtotalLabel.setText(currencyFormat.format(subtotal));

        // Parse Discount Nominal
        BigDecimal discNominal = BigDecimal.ZERO;
        try {
            String txt = discountPercentField.getText().replaceAll("[^0-9]", "");
            if (!txt.isEmpty()) {
                discNominal = new BigDecimal(txt);
            }
        } catch (Exception ignored) {
        }

        // Validate Validation: Discount cannot exceed subtotal
        if (discNominal.compareTo(subtotal) > 0) {
            discNominal = subtotal; // Cap at subtotal
            // Optional: Warn user logic here if needed, but auto-cap is smoother
        }

        currentGrandTotal = subtotal.subtract(discNominal);

        grandTotalLabel.setText(currencyFormat.format(currentGrandTotal));
        payPrintButton.setDisable(cartItems.isEmpty());

        updateChange();
    }

    private void updateChange() {
        BigDecimal totalPaid = BigDecimal.ZERO;
        for (PaymentRow row : paymentRows) {
            totalPaid = totalPaid.add(row.getAmount());
        }

        boolean cashOnly = paymentRows.size() == 1
                && paymentRows.get(0).methodCombo.getValue() == Sale.PaymentMethod.CASH;

        BigDecimal change = BigDecimal.ZERO;
        if (cashOnly) {
            change = totalPaid.subtract(currentGrandTotal);
            if (change.compareTo(BigDecimal.ZERO) < 0) {
                change = BigDecimal.ZERO;
            }
        }
        changeLabel.setText(currencyFormat.format(change));
    }

    @FXML
    private void handlePayAndPrint() {
        try {
            if (cartItems.isEmpty()) {
                showAlert("Keranjang kosong!");
                return;
            }

            // Validate fields for LAPTOP
            for (SaleItem item : cartItems) {
                boolean isLaptop = item.getProductType() == Product.ProductType.LAPTOP_NEW
                        || item.getProductType() == Product.ProductType.LAPTOP_SECOND;
                if (isLaptop) {
                    if (item.getSerialNumber() == null || item.getSerialNumber().isBlank()) {
                        showAlert("Serial number wajib diisi untuk: " + item.getProductName());
                        return;
                    }
                    if (item.getBuyerName() == null || item.getBuyerName().isBlank()) {
                        showAlert("Nama pembeli wajib diisi untuk: " + item.getProductName());
                        return;
                    }
                    if (item.getWarrantyLabel() == null || item.getWarrantyLabel().isBlank()) {
                        showAlert("Garansi wajib dipilih untuk: " + item.getProductName());
                        return;
                    }
                }
            }

            // Calculate total paid
            BigDecimal totalPaid = BigDecimal.ZERO;
            List<SalePayment> payments = new ArrayList<>();

            for (PaymentRow row : paymentRows) {
                BigDecimal amt = row.getAmount();
                if (amt.compareTo(BigDecimal.ZERO) > 0) {
                    SalePayment payment = new SalePayment();
                    payment.setMethod(row.getMethod());
                    payment.setAmount(amt);
                    payments.add(payment);
                    totalPaid = totalPaid.add(amt);
                }
            }

            if (payments.isEmpty()) {
                showAlert("Masukkan nominal pembayaran!");
                return;
            }

            // Validation: split payments must be exact
            if (payments.size() > 1 && totalPaid.compareTo(currentGrandTotal) != 0) {
                showAlert("Total split payment harus sama dengan total transaksi!");
                return;
            }

            // Validation: non-cash single payments must be exact
            if (payments.size() == 1) {
                String method = payments.get(0).getMethod();
                if (!"CASH".equalsIgnoreCase(method) && totalPaid.compareTo(currentGrandTotal) != 0) {
                    showAlert("Pembayaran non-tunai harus pas dengan total transaksi!");
                    return;
                }
            }

            if (totalPaid.compareTo(currentGrandTotal) < 0) {
                showAlert("Pembayaran kurang! Total: " + currencyFormat.format(currentGrandTotal));
                return;
            }

            BigDecimal change = totalPaid.subtract(currentGrandTotal);
            processPayment(payments, totalPaid, change);
        } catch (Exception e) {
            com.baletpos.util.ModalUtil.showFriendlyError("Waduh, ada kendala teknis!", e);
        }
    }

    private void processPayment(List<SalePayment> payments, BigDecimal totalPaid, BigDecimal change) {
        try {
            Sale sale = new Sale();
            sale.setCustomerId(1L);
            sale.setSaleDate(java.time.LocalDateTime.now());

            BigDecimal subtotal = cartItems.stream().map(SaleItem::getSubtotal).reduce(BigDecimal.ZERO,
                    BigDecimal::add);
            sale.setSubtotal(subtotal);

            BigDecimal discNominal = BigDecimal.ZERO;
            try {
                String txt = discountPercentField.getText().replaceAll("[^0-9]", "");
                if (!txt.isEmpty()) {
                    discNominal = new BigDecimal(txt);
                }
            } catch (Exception ignored) {
            }

            sale.setDiscountAmount(discNominal);

            // Calculate percent for record (metadata)
            double discPercent = 0;
            if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                discPercent = discNominal.multiply(BigDecimal.valueOf(100))
                        .divide(subtotal, 2, java.math.RoundingMode.HALF_UP).doubleValue();
            }
            sale.setDiscountPercent(discPercent);
            sale.setTotalAmount(currentGrandTotal);

            BigDecimal totalHpp = cartItems.stream()
                    .map(i -> i.getHppPerUnit().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sale.setTotalHpp(totalHpp);

            sale.setPayments(payments);
            sale.setPaymentType(payments.size() > 1 ? Sale.PaymentType.SPLIT : Sale.PaymentType.SINGLE);
            sale.setPaymentAmount(totalPaid);
            sale.setChangeAmount(change);

            if (payments.size() == 1) {
                sale.setPaymentMethod(Sale.PaymentMethod.valueOf(payments.get(0).getMethod()));
            } else {
                sale.setPaymentMethod(Sale.PaymentMethod.SPLIT);
            }

            User currentUser = Session.getInstance().getCurrentUser();
            sale.setCreatedBy(currentUser != null ? currentUser.getId() : 1L);
            sale.setItems(cartItems);

            saleDAO.createSale(sale);

            Sale storedSale = saleDAO.findByInvoiceNumber(sale.getInvoiceNumber());
            if (storedSale == null) {
                throw new RuntimeException("Transaksi gagal diverifikasi!");
            }

            // Print directly to receipt printer
            try {
                new com.baletpos.service.ReceiptPrinterService().printReceipt(storedSale);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> com.baletpos.util.ModalUtil.showWarning("Perhatian",
                        "Transaksi berhasil tapi gagal cetak nota: " + e.getMessage()));
            }

            com.baletpos.util.ModalUtil.showSuccess("Transaksi Berhasil",
                    "Kembali: " + currencyFormat.format(change));
            resetPOS();
            loadProductCatalog();

        } catch (Exception e) {
            com.baletpos.util.ErrorHandler.handle("Payment", e);
        }
    }

    private void resetPOS() {
        cartItems.clear();
        searchProductField.clear();
        searchProductField.clear();
        discountPercentField.setText("");
        resetPaymentRows();
        searchProductField.requestFocus();
    }

    private void showAlert(String msg) {
        com.baletpos.util.ModalUtil.showWarning("Perhatian", msg);
    }

    private void showInfo(String msg) {
        com.baletpos.util.ModalUtil.showSuccess("Berhasil", msg);
    }

    // === LIST STYLE IMPLEMENTATION ===
    private HBox createProductCardRow(Product p) {
        HBox row = new HBox(12);
        row.getStyleClass().add("product-row");
        String baseStyle = "-fx-background-color: white; -fx-background-radius: 8; -fx-padding: 12; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.03), 2, 0, 0, 1); -fx-border-color: #f1f5f9; -fx-border-width: 0 0 1 0;";
        row.setStyle(baseStyle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(row, Priority.ALWAYS);

        // --- AVATAR START (No Photo - Initial Only) ---
        StackPane avatarPane = new StackPane();
        avatarPane.setPrefSize(40, 40);
        avatarPane.setMaxSize(40, 40);

        String name = p.getName() != null ? p.getName().toUpperCase() : "?";
        String initials = name.length() > 1 ? name.substring(0, 2) : name.substring(0, 1);
        Label initialsLabel = new Label(initials);
        initialsLabel.setStyle("-fx-text-fill: white; -fx-font-weight: 800; -fx-font-size: 14px;");

        int colorIndex = (Math.abs(name.hashCode()) % 7) + 1;
        avatarPane.getStyleClass().clear();
        avatarPane.getStyleClass().add("avatar-circle");
        avatarPane.getStyleClass().add("avatar-bg-" + colorIndex);
        avatarPane.setStyle("-fx-background-radius: 50;");

        avatarPane.getChildren().add(initialsLabel);
        // --- AVATAR END ---

        VBox infoBox = new VBox(4);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(p.getName());
        nameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #1e293b;");
        Label skuLbl = new Label(p.getSku() + " â€¢ " + p.getProductType().name());
        skuLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        infoBox.getChildren().addAll(nameLbl, skuLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox rightBox = new VBox(4);
        rightBox.setAlignment(Pos.CENTER_RIGHT);
        Label priceLbl = new Label(currencyFormat.format(p.getSellingPrice()));
        priceLbl.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #1E40AF; -fx-font-family: 'Consolas', monospace;");
        HBox badgeRow = new HBox(6);
        badgeRow.setAlignment(Pos.CENTER_RIGHT);
        Label stockLbl = new Label("Stok: " + p.getStock());

        boolean disabled = false;
        if (p.getStock() <= 0) {
            stockLbl.setText("HABIS");
            stockLbl.setStyle(
                    "-fx-font-size: 10px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-text-fill: white; -fx-background-color: #1E40AF; -fx-font-weight: bold;");
            row.setOpacity(0.6);
            disabled = true;
        } else {
            stockLbl.setStyle(
                    "-fx-font-size: 10px; -fx-padding: 2 8; -fx-background-radius: 4; -fx-text-fill: #475569; -fx-background-color: #f1f5f9; -fx-font-weight: bold;");
        }
        badgeRow.getChildren().add(stockLbl);
        rightBox.getChildren().addAll(priceLbl, badgeRow);

        row.getChildren().addAll(avatarPane, infoBox, spacer, rightBox);

        if (!disabled) {
            row.setOnMouseClicked(e -> {
                // Global Try Catch for Add to Cart
                try {
                    addItemToCart(p);
                    row.setStyle(baseStyle + "-fx-background-color: #EFF6FF;");
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            Platform.runLater(() -> row.setStyle(baseStyle));
                        }
                    }, 150);
                } catch (Exception ex) {
                    com.baletpos.util.ErrorHandler.handle("POS Error", ex);
                }
            });
            row.setOnMouseEntered(e -> row.setStyle(baseStyle + "-fx-background-color: #f8fafc;"));
            row.setOnMouseExited(e -> row.setStyle(baseStyle));
        } else {
            row.setOnMouseClicked(
                    e -> com.baletpos.util.ModalUtil.showWarning("Stok Habis", "Produk ini sedang kosong."));
        }
        return row;
    }
}


