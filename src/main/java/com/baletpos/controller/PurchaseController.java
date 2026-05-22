package com.baletpos.controller;

import com.baletpos.dao.*;
import com.baletpos.model.*;
import com.baletpos.util.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Controller untuk menu Pembelian (Purchase Order)
 */
public class PurchaseController {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseController.class);

    @FXML
    private ComboBox<Supplier> supplierCombo;

    @FXML
    private TextField supplierSearchField;

    @FXML
    private TextField productSearchField;

    @FXML
    private TableView<PurchaseItem> itemsTable;

    @FXML
    private TableColumn<PurchaseItem, String> skuCol;

    @FXML
    private TableColumn<PurchaseItem, String> nameCol;

    @FXML
    private TableColumn<PurchaseItem, Integer> qtyCol;

    @FXML
    private TableColumn<PurchaseItem, String> hppCol;

    @FXML
    private TableColumn<PurchaseItem, String> subtotalCol;

    @FXML
    private Label totalLabel;

    @FXML
    private TextArea notesArea;

    @FXML
    private CheckBox updateHppCheck;

    @FXML
    private Button removeButton;

    @FXML
    private Button saveButton;

    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final PurchaseDAO purchaseDAO = new PurchaseDAO();
    private final ObservableList<PurchaseItem> items = FXCollections.observableArrayList();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    private List<Supplier> allSuppliers;
    private List<Product> allProducts;

    @FXML
    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);

        setupTable();
        loadSuppliers();
        loadProducts();

        // Supplier search filter
        supplierSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterSuppliers(newVal));

        // Selection listener
        itemsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeButton.setDisable(newVal == null);
        });

        // Recalculate total when items change
        items.addListener((javafx.collections.ListChangeListener<PurchaseItem>) c -> calculateTotal());
    }

    private void setupTable() {
        skuCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getProductSku()));
        nameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getProductName()));

        qtyCol.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleIntegerProperty(cell.getValue().getQuantity()).asObject());
        qtyCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        qtyCol.setOnEditCommit(e -> {
            PurchaseItem item = e.getRowValue();
            item.setQuantity(e.getNewValue());
            item.calculateSubtotal();
            itemsTable.refresh();
            calculateTotal();
        });

        hppCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getHppPerUnit())));

        subtotalCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getSubtotal())));

        itemsTable.setItems(items);
        itemsTable.setEditable(true);
    }

    private void loadSuppliers() {
        allSuppliers = supplierDAO.findAll();
        supplierCombo.setItems(FXCollections.observableArrayList(allSuppliers));
    }

    private void loadProducts() {
        allProducts = productDAO.findAll();
        logger.info("Loaded {} products for purchase view", allProducts.size());
    }

    private void filterSuppliers(String query) {
        if (query == null || query.isBlank()) {
            supplierCombo.setItems(FXCollections.observableArrayList(allSuppliers));
        } else {
            String lowerQuery = query.toLowerCase();
            List<Supplier> filtered = allSuppliers.stream()
                    .filter(s -> s.getCode().toLowerCase().contains(lowerQuery)
                            || s.getName().toLowerCase().contains(lowerQuery))
                    .collect(Collectors.toList());
            supplierCombo.setItems(FXCollections.observableArrayList(filtered));
            if (!filtered.isEmpty()) {
                supplierCombo.show();
            }
        }
    }

    @FXML
    private void handleAddProduct() {
        String query = productSearchField.getText().trim();
        logger.info("handleAddProduct called with query: '{}', allProducts size: {}", query, allProducts.size());

        if (query.isEmpty()) {
            showError("Masukkan SKU atau nama produk");
            return;
        }

        // Find exact SKU match first
        Product found = allProducts.stream()
                .filter(p -> p.getSku().equalsIgnoreCase(query))
                .findFirst()
                .orElse(null);

        logger.info("Exact SKU match: {}", found != null ? found.getName() : "null");

        // If not found by SKU, try name match
        if (found == null) {
            List<Product> matches = allProducts.stream()
                    .filter(p -> p.getName().toLowerCase().contains(query.toLowerCase()))
                    .collect(Collectors.toList());

            logger.info("Name matches found: {}", matches.size());

            if (matches.size() == 1) {
                found = matches.get(0);
            } else if (matches.size() > 1) {
                logger.info("Showing product selection dialog for {} products", matches.size());
                showProductSelectionDialog(matches);
                return;
            } else {
                showError("Produk tidak ditemukan");
                return;
            }
        }

        addProductToList(found);
        productSearchField.clear();
    }

    private void showProductSelectionDialog(List<Product> products) {
        javafx.stage.Stage modal = new javafx.stage.Stage();
        com.baletpos.util.ModalUtil.applyModalDefaults(modal);
        modal.initStyle(javafx.stage.StageStyle.DECORATED);
        modal.setTitle("Pilih Produk");

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox();
        root.setStyle(
                "-fx-background-color: white; -fx-padding: 20;");
        root.setPrefWidth(450);

        // Header
        javafx.scene.layout.VBox header = new javafx.scene.layout.VBox(4);
        header.setStyle("-fx-padding: 0 0 16 0;");
        Label title = new Label("Pilih Produk");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label("Ditemukan " + products.size()
                + " produk yang cocok. Klik dua kali atau pilih lalu klik 'Pilih Produk'.");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");
        subtitle.setWrapText(true);
        header.getChildren().addAll(title, subtitle);

        // Product list
        ListView<Product> listView = new ListView<>(FXCollections.observableArrayList(products));
        listView.setPrefHeight(250);
        listView.setStyle("-fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName() + " (" + item.getSku() + ")");
                }
            }
        });

        // Double-click to select
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Product selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    logger.info("Double-clicked product: {}", selected.getName());
                    modal.close();
                    addProductToList(selected);
                }
            }
        });

        // Footer
        javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(12);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setStyle("-fx-padding: 16 0 0 0;");

        Button cancelBtn = new Button("Batal");
        cancelBtn.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: 600; -fx-padding: 10 20; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: transparent; -fx-text-fill: #64748b; " +
                        "-fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        cancelBtn.setOnAction(e -> modal.close());

        Button selectBtn = new Button("Pilih Produk");
        selectBtn.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 24; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: #1E40AF; -fx-text-fill: white;");
        selectBtn.setOnAction(e -> {
            Product selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                logger.info("Selected product from dialog: {}", selected.getName());
                modal.close();
                addProductToList(selected);
            } else {
                showError("Pilih produk terlebih dahulu");
            }
        });

        footer.getChildren().addAll(cancelBtn, selectBtn);
        root.getChildren().addAll(header, listView, footer);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        modal.setScene(scene);
        modal.showAndWait();
    }

    private void addProductToList(Product product) {
        logger.info("addProductToList called for: {}", product.getName());

        // Check if already in list
        for (PurchaseItem item : items) {
            if (item.getProductId().equals(product.getId())) {
                item.setQuantity(item.getQuantity() + 1);
                itemsTable.refresh();
                calculateTotal();
                logger.info("Product already in list, qty increased to: {}", item.getQuantity());
                return;
            }
        }

        // Show HPP input dialog
        javafx.stage.Stage modal = new javafx.stage.Stage();
        com.baletpos.util.ModalUtil.applyModalDefaults(modal);
        modal.initStyle(javafx.stage.StageStyle.DECORATED);
        modal.setTitle("HPP Pembelian - " + product.getName());

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox();
        root.setStyle("-fx-background-color: white; -fx-padding: 20;");
        root.setPrefWidth(400);

        // Header
        javafx.scene.layout.VBox header = new javafx.scene.layout.VBox(4);
        header.setStyle("-fx-padding: 0 0 16 0;");
        Label title = new Label("HPP Pembelian");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitle = new Label(product.getName() + " (" + product.getSku() + ")");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");
        header.getChildren().addAll(title, subtitle);

        // Content
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12);
        content.setStyle("-fx-padding: 16 0;");

        Label lblHpp = new Label("HPP per unit (Rp)");
        lblHpp.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #374151;");

        TextField hppField = new TextField(String.valueOf(product.getHpp().intValue()));
        hppField.setStyle(
                "-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; " +
                        "-fx-background-radius: 8; -fx-padding: 12 14; -fx-font-size: 16px;");

        content.getChildren().addAll(lblHpp, hppField);

        // Footer
        javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(12);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setStyle("-fx-padding: 16 0 0 0;");

        Button cancelBtn = new Button("Batal");
        cancelBtn.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: 600; -fx-padding: 10 20; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: transparent; -fx-text-fill: #64748b; " +
                        "-fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        cancelBtn.setOnAction(e -> modal.close());

        Button saveBtn = new Button("Tambahkan");
        saveBtn.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 24; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: #1E40AF; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> {
            try {
                int hpp = Integer.parseInt(hppField.getText().replaceAll("[^0-9]", ""));
                PurchaseItem item = new PurchaseItem();
                item.setProductId(product.getId());
                item.setProductSku(product.getSku());
                item.setProductName(product.getName());
                item.setQuantity(1);
                item.setHppPerUnit(BigDecimal.valueOf(hpp));
                item.calculateSubtotal();
                items.add(item);
                logger.info("Product added to purchase list: {} with HPP {}", product.getName(), hpp);
                modal.close();
            } catch (NumberFormatException ex) {
                showError("HPP tidak valid");
            }
        });

        footer.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().addAll(header, content, footer);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        modal.setScene(scene);
        modal.showAndWait();
    }

    @FXML
    private void handleRemove() {
        PurchaseItem selected = itemsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            items.remove(selected);
        }
    }

    @FXML
    private void handleClear() {
        items.clear();
        supplierCombo.getSelectionModel().clearSelection();
        notesArea.clear();
        productSearchField.clear();
        supplierSearchField.clear();
    }

    @FXML
    private void handleSave() {
        if (supplierCombo.getValue() == null) {
            showError("Pilih supplier terlebih dahulu");
            return;
        }

        if (items.isEmpty()) {
            showError("Tambahkan minimal satu item");
            return;
        }

        try {
            Purchase purchase = new Purchase();
            purchase.setSupplierId(supplierCombo.getValue().getId());
            purchase.setNotes(notesArea.getText().trim());
            purchase.setCreatedBy(Session.getInstance().getCurrentUser().getId());
            purchase.setItems(items.stream().collect(Collectors.toList()));
            purchase.calculateTotal();

            purchaseDAO.createPurchase(purchase, updateHppCheck.isSelected());

            showInfo("Pembelian berhasil disimpan!\nNomor PO: " + purchase.getPurchaseNumber());
            handleClear();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Gagal menyimpan pembelian: " + e.getMessage());
        }
    }

    private void calculateTotal() {
        BigDecimal total = items.stream()
                .map(PurchaseItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalLabel.setText(currencyFormat.format(total));
    }

    private void showError(String message) {
        com.baletpos.util.ModalUtil.showError("Error", message);
    }

    private void showInfo(String message) {
        com.baletpos.util.ModalUtil.showSuccess("Berhasil", message);
    }
}


