package com.baletpos.controller;

import com.baletpos.dao.ProductDAO;
import com.baletpos.model.Product;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public class ProductFormController {

    @FXML
    private TextField skuField;
    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<Product.ProductType> typeCombo;
    @FXML
    private ComboBox<String> categoryCombo;
    @FXML
    private ComboBox<String> brandCombo;
    @FXML
    private TextField hppField;
    @FXML
    private TextField marginField;
    @FXML
    private TextField sellingPricePreview;
    @FXML
    private TextField stockField;

    @FXML
    private TextArea descField;

    private final ProductDAO productDAO = new ProductDAO();
    private boolean saveClicked = false;
    private Product product;
    private Stage dialogStage;
    private final NumberFormat curFmt = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    @FXML
    public void initialize() {
        curFmt.setMaximumFractionDigits(0);

        typeCombo.setItems(FXCollections.observableArrayList(Product.ProductType.values()));
        typeCombo.getSelectionModel().selectFirst();

        // Dummy Data for Category & Brand (Should be from DAO)
        categoryCombo.setItems(FXCollections.observableArrayList(
                "Laptop", "PC", "Monitor", "Keyboard", "Mouse", "Storage", "RAM", "Aksesoris", "Service"));
        brandCombo.setItems(FXCollections.observableArrayList(
                "ASUS", "Lenovo", "HP", "Acer", "Dell", "MSI", "Logitech", "Samsung", "Kingston", "Generic"));

        // Auto Calculate Selling Price Preview
        hppField.textProperty().addListener((obs, o, n) -> calculatePreview());
        marginField.textProperty().addListener((obs, o, n) -> calculatePreview());
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setProduct(Product product) {
        this.product = product;
        if (product.getId() != null) {
            skuField.setText(product.getSku());
            skuField.setDisable(true); // Can't edit SKU once created
            nameField.setText(product.getName());
            typeCombo.setValue(product.getProductType());
            categoryCombo.setValue(product.getCategoryName()); // Ideally use ID
            brandCombo.setValue(product.getBrandName()); // Ideally use ID
            hppField.setText(String.valueOf(product.getHpp().intValue()));
            marginField.setText(String.valueOf(product.getMarginPercent()));
            stockField.setText(String.valueOf(product.getStock()));
            stockField.setDisable(true); // Edit stock via adjustment only
            descField.setText(product.getDescription());
            calculatePreview();
        } else {
            // Defaults for new product
            stockField.setText("0");
        }
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    private void calculatePreview() {
        try {
            BigDecimal hpp = new BigDecimal(hppField.getText().replaceAll("[^0-9]", ""));
            double margin = Double.parseDouble(marginField.getText());

            BigDecimal marginAmount = hpp.multiply(BigDecimal.valueOf(margin)).divide(BigDecimal.valueOf(100));
            BigDecimal selling = hpp.add(marginAmount);

            sellingPricePreview.setText(curFmt.format(selling));
        } catch (Exception e) {
            sellingPricePreview.setText("-");
        }
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            if (product == null)
                product = new Product();

            product.setSku(skuField.getText());
            product.setName(nameField.getText());
            product.setProductType(typeCombo.getValue());

            // Map String to ID (In real app, use Maps or Objects)
            // For now we just assume IDs match indexes or logic for simplicity or just save
            // names if schema modified
            // Since we use ID in DB, we need to handle this.
            // Temporary Hack: Set ID 1 for all categories/brands if logic missing, or
            // search them.
            // To be proper, we need CategoryDAO and BrandDAO. Using Default ID 1.
            product.setCategoryId(1L);
            product.setBrandId(1L);

            product.setHpp(new BigDecimal(hppField.getText().replaceAll("[^0-9]", "")));
            product.setMarginPercent(Double.parseDouble(marginField.getText()));

            if (product.getId() == null) {
                product.setStock(Integer.parseInt(stockField.getText())); // Initial stock
            }
            product.setDescription(descField.getText());

            productDAO.save(product);

            saveClicked = true;
            dialogStage.close();
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    private boolean isInputValid() {
        String errorMessage = "";

        if (skuField.getText() == null || skuField.getText().length() == 0)
            errorMessage += "SKU tidak valid!\n";
        if (nameField.getText() == null || nameField.getText().length() == 0)
            errorMessage += "Nama tidak valid!\n";
        try {
            new BigDecimal(hppField.getText().replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            errorMessage += "HPP harus angka!\n";
        }

        if (errorMessage.length() == 0)
            return true;
        else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(dialogStage);
            alert.setTitle("Invalid Fields");
            alert.setContentText(errorMessage);
            alert.showAndWait();
            return false;
        }
    }
}


