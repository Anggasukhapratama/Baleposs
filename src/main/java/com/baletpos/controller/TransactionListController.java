package com.baletpos.controller;

import com.baletpos.dao.SaleDAO;
import com.baletpos.model.Sale;
import com.baletpos.model.SaleItem;
import com.baletpos.service.PdfService;
import com.baletpos.util.ModalUtil;
import com.baletpos.util.NotificationUtil;
import com.baletpos.util.PaginationControl;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class TransactionListController {

    @FXML
    private TextField searchField;
    @FXML
    private TableView<Sale> transactionTable;
    @FXML
    private TableColumn<Sale, Boolean> selectCol;
    @FXML
    private TableColumn<Sale, String> invoiceCol;
    @FXML
    private TableColumn<Sale, String> dateCol;
    @FXML
    private TableColumn<Sale, String> cashierCol;
    @FXML
    private TableColumn<Sale, String> totalCol;
    @FXML
    private TableColumn<Sale, String> paymentCol;
    @FXML
    private TableColumn<Sale, String> statusCol;
    @FXML
    private TableColumn<Sale, Void> actionCol;

    @FXML
    private VBox tableContainer;

    private final SaleDAO saleDAO = new SaleDAO();
    private final ObservableList<Sale> saleList = FXCollections.observableArrayList();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm",
            new Locale("id", "ID"));

    // Pagination
    private PaginationControl pagination;
    private String currentSearchQuery = "";

    @FXML
    public void initialize() {
        try {
            currencyFormat.setMaximumFractionDigits(0);
            setupTable();
            setupPagination();
            loadData();

            // Search listener
            searchField.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    handleSearch();
                }
            });
        } catch (Exception e) {
            System.err.println("ERROR in TransactionListController.initialize: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupTable() {
        System.out.println("Setting up transaction table columns...");

        // 1. Checkbox Column
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setCellValueFactory(cellData -> {
            Sale sale = cellData.getValue();
            SimpleBooleanProperty property = new SimpleBooleanProperty(false);
            return property;
        });
        selectCol.setEditable(true);
        transactionTable.setEditable(true);

        // 2. Invoice - Manual Lambda
        invoiceCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getInvoiceNumber()));

        // 3. Date
        dateCol.setCellValueFactory(cell -> {
            if (cell.getValue().getSaleDate() != null) {
                return new SimpleStringProperty(cell.getValue().getSaleDate().format(dateTimeFormatter));
            }
            return new SimpleStringProperty("-");
        });

        // 4. Cashier
        cashierCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getCreatedByName() != null ? cell.getValue().getCreatedByName() : "System"));

        // 5. Total (Right Align)
        totalCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getTotalAmount())));
        totalCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        // 6. Payment (Badge)
        paymentCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");

                    // Style based on text
                    String lower = item.toLowerCase();
                    if (lower.contains("cash") || lower.contains("tunai")) {
                        badge.getStyleClass().add("badge-payment-cash");
                    } else if (lower.contains("transfer")) {
                        badge.getStyleClass().add("badge-payment-transfer");
                    } else if (lower.contains("qris")) {
                        badge.getStyleClass().add("badge-payment-qris");
                    } else {
                        badge.getStyleClass().add("pill"); // Default
                    }
                    setGraphic(badge);
                    setAlignment(Pos.CENTER);
                    setText(null);
                }
            }
        });
        paymentCol.setCellValueFactory(cell -> {
            if (cell.getValue().getPaymentType() == Sale.PaymentType.SPLIT)
                return new SimpleStringProperty("Split");
            if (cell.getValue().getPaymentMethod() != null)
                return new SimpleStringProperty(cell.getValue().getPaymentMethod().getDisplayName());
            return new SimpleStringProperty("-");
        });

        // 7. Status (Badge)
        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");

                    if ("COMPLETED".equalsIgnoreCase(item)) {
                        badge.setText("Completed");
                        badge.getStyleClass().add("badge-status-completed");
                    } else if ("VOIDED".equalsIgnoreCase(item)) {
                        badge.setText("Void");
                        badge.getStyleClass().add("badge-status-voided");
                    } else if ("RETURNED".equalsIgnoreCase(item)) {
                        badge.setText("Returned");
                        badge.getStyleClass().add("badge-status-canceled");
                    } else {
                        badge.setText(item);
                        badge.getStyleClass().add("pill");
                    }
                    setGraphic(badge);
                    setAlignment(Pos.CENTER);
                    setText(null);
                }
            }
        });
        statusCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getStatus() != null ? cell.getValue().getStatus().name() : ""));

        // 8. Action (Print Icon)
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button printBtn = new Button("Print");
            {
                printBtn.getStyleClass().addAll("btn-table-action", "btn-table-print");
                printBtn.setTooltip(new Tooltip("Cetak Ulang"));
                printBtn.setOnAction(e -> {
                    Sale s = getTableView().getItems().get(getIndex());
                    handlePrint(s);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox box = new HBox(printBtn);
                    box.setAlignment(Pos.CENTER);
                    setGraphic(box);
                }
            }
        });

        transactionTable.setItems(saleList);
        transactionTable.setPlaceholder(new Label("Tidak ada transaksi"));
        System.out.println("Table setup complete.");
    }

    private void loadData() {
        saleList.clear();
        String query = currentSearchQuery != null ? currentSearchQuery : "";

        long totalItems = saleDAO.countSales(null, null, query);
        pagination.update((int) totalItems);

        int limit = pagination.getPageSize();
        int offset = pagination.getOffset();

        List<Sale> data = saleDAO.searchSales(null, null, query, limit, offset);
        saleList.addAll(data);
    }

    private void setupPagination() {
        pagination = new PaginationControl();
        pagination.setOnPageChange(this::loadData);
        if (tableContainer != null) {
            tableContainer.getChildren().add(pagination);
        }
    }

    @FXML
    private void handleSearch() {
        currentSearchQuery = searchField.getText().trim();
        pagination.resetToFirstPage();
        loadData();
    }

    
    private void handlePrint(Sale s) {
        boolean confirm = ModalUtil.showConfirm("Cetak Ulang",
                "Cetak struk untuk invoice " + s.getInvoiceNumber() + "?");
        if (confirm) {
            try {
                // Should fetch full detail first? searchSales might load limited info.
                // SaleDAO.searchSales usually loads Sales without Items for performance in
                // lists.
                // But PdfService needs Items.
                Sale fullSale = saleDAO.findByInvoiceNumber(s.getInvoiceNumber());
                PdfService.printInvoice(fullSale);
                NotificationUtil.successShort("Struk berhasil dicetak.");
            } catch (Exception e) {
                e.printStackTrace();
                ModalUtil.showError("Error", "Gagal mencetak struk: " + e.getMessage());
            }
        }
    }
}


