package com.baletpos.controller;

import com.baletpos.dao.SaleDAO;
import com.baletpos.model.Sale;
import com.baletpos.util.ModalUtil;
import com.baletpos.util.NotificationUtil;
import com.baletpos.util.PaginationControl;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class SalesReportController {

    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private TextField searchField;

    @FXML
    private TableView<Sale> salesTable;
    @FXML
    private TableColumn<Sale, String> invoiceCol;
    @FXML
    private TableColumn<Sale, String> dateCol;
    @FXML
    private TableColumn<Sale, String> customerCol;
    @FXML
    private TableColumn<Sale, String> paymentCol;
    @FXML
    private TableColumn<Sale, String> statusCol;
    @FXML
    private TableColumn<Sale, String> userCol;
    @FXML
    private TableColumn<Sale, String> amountCol;
    @FXML
    private TableColumn<Sale, Void> actionCol;

    @FXML
    private Label totalRevenueLabel;
    @FXML
    private VBox tableContainer;

    @FXML
    private Button btnToday;
    @FXML
    private Button btnYesterday;
    @FXML
    private Button btnWeek;
    @FXML
    private Button btnMonth;
    @FXML
    private Button btnGenerate;
    @FXML
    private Button btnExport;

    private final SaleDAO saleDAO = new SaleDAO();
    private final ObservableList<Sale> salesList = FXCollections.observableArrayList();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private boolean isProgrammaticUpdate = false;
    private PaginationControl pagination;
    private String currentSearchQuery = "";

    @FXML
    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);
        setupTable();
        setupPagination();
        setupListeners();

        // Default: This Month (Start of Month to Now)
        handlePresetMonth(null);
    }

    private void setupListeners() {
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isProgrammaticUpdate) {
                clearPresetStyles();
                validateDates();
            }
        });

        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!isProgrammaticUpdate) {
                clearPresetStyles();
                validateDates();
            }
            // Logic to auto-reload if both dates valid? Wait for button
        });

        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                handleFilter();
            }
        });

        // Detail View on Double Click
        salesTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && salesTable.getSelectionModel().getSelectedItem() != null) {
                showSaleDetail(salesTable.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void validateDates() {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        boolean invalid = (start == null || end == null || start.isAfter(end));
        btnGenerate.setDisable(invalid);
    }

    private void setupTable() {
        invoiceCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));

        dateCol.setCellValueFactory(cell -> {
            if (cell.getValue().getSaleDate() != null) {
                return new SimpleStringProperty(cell.getValue().getSaleDate().format(dateTimeFormatter));
            }
            return new SimpleStringProperty("-");
        });

        customerCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getCustomerName() != null ? cell.getValue().getCustomerName() : "Umum"));

        paymentCol.setCellValueFactory(cell -> {
            if (cell.getValue().getPaymentType() == Sale.PaymentType.SPLIT)
                return new SimpleStringProperty("SPLIT");
            return new SimpleStringProperty(cell.getValue().getPaymentMethod().name());
        });

        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        userCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getCreatedByName() != null ? cell.getValue().getCreatedByName() : "-"));

        amountCol.setCellValueFactory(
                cell -> new SimpleStringProperty(currencyFormat.format(cell.getValue().getTotalAmount())));

        // Action Column with Print Button
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button printBtn = new Button(" Cetak");
            {
                printBtn.getStyleClass().addAll("btn-table-text", "btn-table-text-primary");
                printBtn.setOnAction(e -> {
                    Sale sale = getTableView().getItems().get(getIndex());
                    handlePrintInvoice(sale);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(printBtn);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        salesTable.setItems(salesList);
    }

    // === Preset Logic ===

    private void applyPreset(Button activeBtn, LocalDate start, LocalDate end) {
        isProgrammaticUpdate = true;
        try {
            startDatePicker.setValue(start);
            endDatePicker.setValue(end);
        } finally {
            isProgrammaticUpdate = false;
        }

        updatePresetStyles(activeBtn);
        validateDates();
        handleFilter(); // Auto load
    }

    private void updatePresetStyles(Button active) {
        resetPill(btnToday);
        resetPill(btnYesterday);
        resetPill(btnWeek);
        resetPill(btnMonth);
        if (active != null)
            active.getStyleClass().add("pill-active");
    }

    private void resetPill(Button b) {
        if (b != null) {
            b.getStyleClass().remove("pill-active");
            if (!b.getStyleClass().contains("pill"))
                b.getStyleClass().add("pill");
        }
    }

    private void clearPresetStyles() {
        updatePresetStyles(null);
    }

    @FXML
    private void handlePresetToday(ActionEvent e) {
        applyPreset(btnToday, LocalDate.now(), LocalDate.now());
    }

    @FXML
    private void handlePresetYesterday(ActionEvent e) {
        applyPreset(btnYesterday, LocalDate.now().minusDays(1), LocalDate.now().minusDays(1));
    }

    @FXML
    private void handlePresetWeek(ActionEvent e) {
        applyPreset(btnWeek, LocalDate.now().minusDays(6), LocalDate.now());
    }

    @FXML
    private void handlePresetMonth(ActionEvent e) {
        applyPreset(btnMonth, LocalDate.now().withDayOfMonth(1), LocalDate.now());
    }

    @FXML
    private void handleFilter() {
        currentSearchQuery = searchField.getText().trim();
        if (pagination != null) {
            pagination.resetToFirstPage();
        }
        loadPage();
    }

    private void setupPagination() {
        pagination = new PaginationControl();
        pagination.setOnPageChange(this::loadPage);
        if (tableContainer != null) {
            tableContainer.getChildren().add(pagination);
        }
    }

    @FXML
    private void handleExport() {
        NotificationUtil.infoShort("Fitur Export akan segera hadir.");
    }

    private void loadPage() {
        if (startDatePicker.getValue() == null || endDatePicker.getValue() == null)
            return;

        salesList.clear();
        String query = currentSearchQuery != null ? currentSearchQuery : "";

        long totalItems = saleDAO.countSales(startDatePicker.getValue(), endDatePicker.getValue(), query);
        if (pagination != null) {
            pagination.update((int) totalItems);
        }

        int limit = pagination != null ? pagination.getPageSize() : 10;
        int offset = pagination != null ? pagination.getOffset() : 0;

        List<Sale> data = saleDAO.searchSales(
                startDatePicker.getValue(),
                endDatePicker.getValue(),
                query,
                limit,
                offset);

        salesList.addAll(data);

        BigDecimal totalRevenue = saleDAO.sumSales(startDatePicker.getValue(), endDatePicker.getValue(), query);
        totalRevenueLabel.setText(currencyFormat.format(totalRevenue));
    }

    private void showSaleDetail(Sale s) {
        handlePrintInvoice(s);
    }

    private void handlePrintInvoice(Sale s) {
        boolean confirm = ModalUtil.showConfirm(
                "Cetak Invoice",
                "Cetak ulang struk untuk invoice " + s.getInvoiceNumber() + "?");

        if (confirm) {
            Sale fullSale = saleDAO.findByInvoiceNumber(s.getInvoiceNumber());
            if (fullSale != null) {
                try {
                    new com.baletpos.service.ReceiptPrinterService().printReceipt(fullSale);
                    NotificationUtil.successShort("Nota berhasil dikirim ke printer.");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ModalUtil.showError("Gagal Cetak", "Terjadi kesalahan saat mencetak: " + ex.getMessage());
                }
            }
        }
    }
}


