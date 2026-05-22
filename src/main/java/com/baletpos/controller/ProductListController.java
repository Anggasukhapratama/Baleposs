package com.baletpos.controller;

import com.baletpos.dao.ProductDAO;
import com.baletpos.model.Product;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.text.NumberFormat;
import java.util.Locale;

public class ProductListController {

    @FXML
    private TextField searchField;

    @FXML
    private TableView<Product> productTable;

    @FXML
    private TableColumn<Product, String> skuColumn;
    @FXML
    private TableColumn<Product, String> nameColumn;
    @FXML
    private TableColumn<Product, String> typeColumn;
    @FXML
    private TableColumn<Product, String> categoryColumn;
    @FXML
    private TableColumn<Product, String> brandColumn;
    @FXML
    private TableColumn<Product, String> stockColumn;
    @FXML
    private TableColumn<Product, String> priceColumn;

    private final ProductDAO productDAO;
    private ObservableList<Product> productList;
    private final NumberFormat currencyFormat;

    public ProductListController() {
        this.productDAO = new ProductDAO();
        this.currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
        this.currencyFormat.setMaximumFractionDigits(0);
    }

    @FXML
    public void initialize() {
        // Setup columns
        skuColumn.setCellValueFactory(new PropertyValueFactory<>("sku"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("productType"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        brandColumn.setCellValueFactory(new PropertyValueFactory<>("brandName"));
        stockColumn.setCellValueFactory(new PropertyValueFactory<>("stock"));

        priceColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(currencyFormat.format(cellData.getValue().getSellingPrice())));

        // Load data
        loadData();

        // Search listener
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterData(newValue);
        });
    }

    private void loadData() {
        productList = FXCollections.observableArrayList(productDAO.findAll());
        productTable.setItems(productList);
    }

    private void filterData(String query) {
        if (query == null || query.isEmpty()) {
            productTable.setItems(productList);
        } else {
            ObservableList<Product> filtered = FXCollections.observableArrayList();
            for (Product p : productList) {
                if (p.getName().toLowerCase().contains(query.toLowerCase()) ||
                        p.getSku().toLowerCase().contains(query.toLowerCase())) {
                    filtered.add(p);
                }
            }
            productTable.setItems(filtered);
        }
    }

    @FXML
    private void handleAddProduct() {
        showProductDialog(new Product());
    }

    @FXML
    private void handleEditProduct() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showProductDialog(selected);
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Pilih produk yang akan diedit!");
            alert.show();
        }
    }

    private void showProductDialog(Product product) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    com.baletpos.App.class.getResource("/fxml/product_form.fxml"));
            javafx.scene.layout.VBox page = loader.load();

            javafx.stage.Stage dialogStage = new javafx.stage.Stage();
            dialogStage.setTitle("Form Produk");
            dialogStage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            dialogStage.initOwner(productTable.getScene().getWindow());
            javafx.scene.Scene scene = new javafx.scene.Scene(page);
            dialogStage.setScene(scene);

            ProductFormController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setProduct(product);

            dialogStage.showAndWait();

            if (controller.isSaveClicked()) {
                loadData();
            }
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }
}


