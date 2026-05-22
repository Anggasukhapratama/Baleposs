package com.baletpos.controller;

import com.baletpos.dao.SalesReturnDAO;
import com.baletpos.dao.SaleDAO;
import com.baletpos.dao.StockAdjustmentDAO;
import com.baletpos.dao.ProductDAO;
import com.baletpos.model.*;
import com.baletpos.util.ExcelExportUtil;
import java.util.Arrays;
import javafx.stage.Stage;
import com.baletpos.util.ModalUtil;
import com.baletpos.util.PaginationControl;
import com.baletpos.util.SecurityUtil;
import com.baletpos.util.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReturnMutationController {
    private static final Logger logger = LoggerFactory.getLogger(ReturnMutationController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final NumberFormat CURRENCY_FMT = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    // === Tab Retur Fields ===
    @FXML
    private TextField txtSearchInvoice;
    @FXML
    private TableView<ReturnItemRow> tableReturn;
    @FXML
    private TableColumn<ReturnItemRow, String> colSku;
    @FXML
    private TableColumn<ReturnItemRow, String> colName;
    @FXML
    private TableColumn<ReturnItemRow, Integer> colQtySold;
    @FXML
    private TableColumn<ReturnItemRow, Integer> colQtyReturned;
    @FXML
    private TableColumn<ReturnItemRow, Integer> colQtyToReturn;

    // === Tab Mutasi Fields ===
    @FXML
    private TextField txtSearchProduct;
    @FXML
    private TextField txtAdjQty;
    @FXML
    private TextArea txtAdjReason;
    @FXML
    private Label lblSelectedProduct;

    // === Tab Riwayat Retur Fields ===
    @FXML
    private TextField searchReturnField;
    @FXML
    private TableView<SalesReturn> tableReturnHistory;
    @FXML
    private TableColumn<SalesReturn, String> colReturnNo;
    @FXML
    private TableColumn<SalesReturn, String> colReturnInvoice;
    @FXML
    private TableColumn<SalesReturn, String> colReturnDate;
    @FXML
    private TableColumn<SalesReturn, String> colReturnTotal;
    @FXML
    private TableColumn<SalesReturn, String> colReturnUser;
    @FXML
    private TableColumn<SalesReturn, Void> colReturnAction;
    @FXML
    private VBox returnHistoryContainer;

    // === Tab Riwayat Mutasi Fields ===
    @FXML
    private TextField searchAdjustmentField;
    @FXML
    private TableView<StockAdjustment> tableAdjustmentHistory;
    @FXML
    private TableColumn<StockAdjustment, String> colAdjNo;
    @FXML
    private TableColumn<StockAdjustment, String> colAdjDate;
    @FXML
    private TableColumn<StockAdjustment, String> colAdjReason;
    @FXML
    private TableColumn<StockAdjustment, String> colAdjUser;
    @FXML
    private TableColumn<StockAdjustment, Void> colAdjAction;
    @FXML
    private VBox adjustmentHistoryContainer;

    // DAO
    private SaleDAO saleDAO = new SaleDAO();
    private SalesReturnDAO salesReturnDAO = new SalesReturnDAO();
    private StockAdjustmentDAO adjustmentDAO = new StockAdjustmentDAO();
    private ProductDAO productDAO = new ProductDAO();

    private Sale currentSale;
    private Product selectedProduct;
    private PaginationControl returnPagination;
    private PaginationControl adjustmentPagination;
    private final ObservableList<SalesReturn> returnHistoryData = FXCollections.observableArrayList();
    private final ObservableList<StockAdjustment> adjustmentHistoryData = FXCollections.observableArrayList();
    private List<SalesReturn> allReturns = new ArrayList<>();
    private List<StockAdjustment> allAdjustments = new ArrayList<>();
    private List<SalesReturn> filteredReturns = new ArrayList<>();
    private List<StockAdjustment> filteredAdjustments = new ArrayList<>();

    @FXML
    public void initialize() {
        initReturnTable();
        initReturnHistoryTable();
        initAdjustmentHistoryTable();
        setupPagination();
        setupSearchHandlers();

        // Load history data on init
        loadReturnHistory();
        loadAdjustmentHistory();
    }

    // =============================================
    // INIT TABLES
    // =============================================

    private void initReturnTable() {
        colSku.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSaleItem().getProductSku()));
        colName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSaleItem().getProductName()));
        colQtySold.setCellValueFactory(new PropertyValueFactory<>("qtySold"));
        colQtyReturned.setCellValueFactory(new PropertyValueFactory<>("qtyPreviouslyReturned"));

        colQtyToReturn.setCellValueFactory(new PropertyValueFactory<>("qtyToReturn"));
        colQtyToReturn.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colQtyToReturn.setOnEditCommit(e -> {
            ReturnItemRow row = e.getRowValue();
            int newVal = e.getNewValue();
            if (newVal < 0)
                newVal = 0;
            if (newVal > (row.getQtySold() - row.getQtyPreviouslyReturned())) {
                ModalUtil.showError("Error", "Jumlah retur melebihi sisa barang yang bisa diretur.");
                newVal = e.getOldValue();
            }
            row.setQtyToReturn(newVal);
            tableReturn.refresh();
        });

        tableReturn.setEditable(true);
    }

    private void initReturnHistoryTable() {
        colReturnNo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getReturnNo()));
        colReturnInvoice.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSaleInvoiceNumber()));
        colReturnDate.setCellValueFactory(cell -> {
            var date = cell.getValue().getCreatedAt();
            return new SimpleStringProperty(date != null ? date.format(DATE_FMT) : "-");
        });
        colReturnTotal.setCellValueFactory(cell -> {
            var total = cell.getValue().getTotalAmount();
            return new SimpleStringProperty(total != null ? CURRENCY_FMT.format(total) : "-");
        });
        colReturnUser.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCreatedByName()));

        tableReturnHistory.setItems(returnHistoryData);

        // Action column with Print button
        colReturnAction.setCellFactory(col -> new TableCell<>() {
            private final Button printBtn = new Button("Print");
            {
                printBtn.getStyleClass().addAll("btn-table-action", "btn-table-print");
                printBtn.setTooltip(new Tooltip("Cetak Retur"));
                printBtn.setOnAction(e -> {
                    SalesReturn ret = getTableView().getItems().get(getIndex());
                    handlePrintReturn(ret);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : printBtn);
                setAlignment(Pos.CENTER);
            }
        });
    }

    private void initAdjustmentHistoryTable() {
        colAdjNo.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAdjNo()));
        colAdjDate.setCellValueFactory(cell -> {
            var date = cell.getValue().getCreatedAt();
            return new SimpleStringProperty(date != null ? date.format(DATE_FMT) : "-");
        });
        colAdjReason.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getReason()));
        colAdjUser.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCreatedByName()));

        tableAdjustmentHistory.setItems(adjustmentHistoryData);

        // Action column with Print button
        colAdjAction.setCellFactory(col -> new TableCell<>() {
            private final Button printBtn = new Button("Print");
            {
                printBtn.getStyleClass().addAll("btn-table-action", "btn-table-print");
                printBtn.setTooltip(new Tooltip("Cetak Mutasi"));
                printBtn.setOnAction(e -> {
                    StockAdjustment adj = getTableView().getItems().get(getIndex());
                    handlePrintAdjustment(adj);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : printBtn);
                setAlignment(Pos.CENTER);
            }
        });
    }

    // =============================================
    // LOAD HISTORY DATA
    // =============================================

    @FXML
    private void handleRefreshReturns() {
        loadReturnHistory();
    }

    @FXML
    private void handleRefreshAdjustments() {
        loadAdjustmentHistory();
    }

    private void loadReturnHistory() {
        allReturns = salesReturnDAO.findAll();
        applyReturnFilter();
    }

    private void loadAdjustmentHistory() {
        allAdjustments = adjustmentDAO.findAll();
        applyAdjustmentFilter();
    }

    private void setupPagination() {
        returnPagination = new PaginationControl();
        returnPagination.setOnPageChange(this::updateReturnPage);
        if (returnHistoryContainer != null) {
            returnHistoryContainer.getChildren().add(returnPagination);
        }

        adjustmentPagination = new PaginationControl();
        adjustmentPagination.setOnPageChange(this::updateAdjustmentPage);
        if (adjustmentHistoryContainer != null) {
            adjustmentHistoryContainer.getChildren().add(adjustmentPagination);
        }
    }

    private void setupSearchHandlers() {
        if (searchReturnField != null) {
            searchReturnField.textProperty().addListener((obs, oldVal, newVal) -> applyReturnFilter());
        }
        if (searchAdjustmentField != null) {
            searchAdjustmentField.textProperty().addListener((obs, oldVal, newVal) -> applyAdjustmentFilter());
        }
    }

    private void applyReturnFilter() {
        String q = searchReturnField != null ? searchReturnField.getText().trim().toLowerCase() : "";
        filteredReturns = new ArrayList<>();
        for (SalesReturn r : allReturns) {
            if (q.isEmpty()) {
                filteredReturns.add(r);
                continue;
            }
            String no = safeLower(r.getReturnNo());
            String inv = safeLower(r.getSaleInvoiceNumber());
            String user = safeLower(r.getCreatedByName());
            if (no.contains(q) || inv.contains(q) || user.contains(q)) {
                filteredReturns.add(r);
            }
        }
        if (returnPagination != null) {
            returnPagination.resetToFirstPage();
        }
        updateReturnPage();
    }

    private void applyAdjustmentFilter() {
        String q = searchAdjustmentField != null ? searchAdjustmentField.getText().trim().toLowerCase() : "";
        filteredAdjustments = new ArrayList<>();
        for (StockAdjustment a : allAdjustments) {
            if (q.isEmpty()) {
                filteredAdjustments.add(a);
                continue;
            }
            String no = safeLower(a.getAdjNo());
            String reason = safeLower(a.getReason());
            String user = safeLower(a.getCreatedByName());
            if (no.contains(q) || reason.contains(q) || user.contains(q)) {
                filteredAdjustments.add(a);
            }
        }
        if (adjustmentPagination != null) {
            adjustmentPagination.resetToFirstPage();
        }
        updateAdjustmentPage();
    }

    private void updateReturnPage() {
        int total = filteredReturns != null ? filteredReturns.size() : 0;
        returnPagination.update(total);
        returnHistoryData.clear();
        if (total == 0) {
            return;
        }
        int offset = returnPagination.getOffset();
        int end = Math.min(offset + returnPagination.getPageSize(), total);
        returnHistoryData.addAll(filteredReturns.subList(offset, end));
    }

    private void updateAdjustmentPage() {
        int total = filteredAdjustments != null ? filteredAdjustments.size() : 0;
        adjustmentPagination.update(total);
        adjustmentHistoryData.clear();
        if (total == 0) {
            return;
        }
        int offset = adjustmentPagination.getOffset();
        int end = Math.min(offset + adjustmentPagination.getPageSize(), total);
        adjustmentHistoryData.addAll(filteredAdjustments.subList(offset, end));
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    // =============================================
    // PRINT FUNCTIONS
    // =============================================

    private void handlePrintReturn(SalesReturn ret) {
        // Fetch full return with items
        SalesReturn fullReturn = salesReturnDAO.findById(ret.getId());
        if (fullReturn == null) {
            ModalUtil.showError("Error", "Data retur tidak ditemukan.");
            return;
        }

        // Generate text-based receipt
        StringBuilder sb = new StringBuilder();
        sb.append("============================================\n");
        sb.append("           BUKTI RETUR PENJUALAN           \n");
        sb.append("============================================\n\n");
        sb.append("No. Retur    : ").append(fullReturn.getReturnNo()).append("\n");
        sb.append("Invoice Asal : ").append(fullReturn.getSaleInvoiceNumber()).append("\n");
        sb.append("Tanggal      : ")
                .append(fullReturn.getCreatedAt() != null ? fullReturn.getCreatedAt().format(DATE_FMT) : "-")
                .append("\n");
        sb.append("Diproses Oleh: ").append(fullReturn.getCreatedByName()).append("\n\n");
        sb.append("--------------------------------------------\n");
        sb.append("ITEM RETUR:\n");
        sb.append("--------------------------------------------\n");

        for (SalesReturnItem item : fullReturn.getItems()) {
            sb.append(String.format("%-6s %-25s\n", item.getProductSku(), item.getProductName()));
            sb.append(String.format("       %d x %s = %s\n",
                    item.getQtyReturn(),
                    CURRENCY_FMT.format(item.getUnitPrice()),
                    CURRENCY_FMT.format(item.getLineTotal())));
        }

        sb.append("--------------------------------------------\n");
        sb.append(String.format("TOTAL RETUR: %s\n", CURRENCY_FMT.format(fullReturn.getTotalAmount())));
        sb.append("============================================\n");
        sb.append("\nCatatan: ").append(fullReturn.getNotes() != null ? fullReturn.getNotes() : "-");

        // Show preview and offer to save
        showPrintPreview("Bukti Retur - " + fullReturn.getReturnNo(), sb.toString());
    }

    private void handlePrintAdjustment(StockAdjustment adj) {
        // Fetch full adjustment with items
        StockAdjustment fullAdj = adjustmentDAO.findById(adj.getId());
        if (fullAdj == null) {
            ModalUtil.showError("Error", "Data mutasi tidak ditemukan.");
            return;
        }

        // Generate text-based receipt
        StringBuilder sb = new StringBuilder();
        sb.append("============================================\n");
        sb.append("           BUKTI MUTASI STOK               \n");
        sb.append("============================================\n\n");
        sb.append("No. Mutasi   : ").append(fullAdj.getAdjNo()).append("\n");
        sb.append("Tanggal      : ")
                .append(fullAdj.getCreatedAt() != null ? fullAdj.getCreatedAt().format(DATE_FMT) : "-").append("\n");
        sb.append("Diproses Oleh: ").append(fullAdj.getCreatedByName()).append("\n");
        sb.append("Alasan       : ").append(fullAdj.getReason()).append("\n\n");
        sb.append("--------------------------------------------\n");
        sb.append("ITEM MUTASI:\n");
        sb.append("--------------------------------------------\n");

        for (StockAdjustmentItem item : fullAdj.getItems()) {
            String deltaSign = item.getQtyDelta() >= 0 ? "+" : "";
            sb.append(String.format("%-10s %-25s\n", item.getProductSku(), item.getProductName()));
            sb.append(String.format("           Qty: %s%d\n", deltaSign, item.getQtyDelta()));
            if (item.getNote() != null && !item.getNote().isEmpty()) {
                sb.append(String.format("           Note: %s\n", item.getNote()));
            }
        }

        sb.append("============================================\n");

        // Show preview and offer to save
        showPrintPreview("Bukti Mutasi - " + fullAdj.getAdjNo(), sb.toString());
    }

    private void showPrintPreview(String title, String content) {
        javafx.stage.Stage modal = new javafx.stage.Stage();
        com.baletpos.util.ModalUtil.applyModalDefaults(modal);
        modal.initStyle(javafx.stage.StageStyle.TRANSPARENT);

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox();
        root.setStyle(
                "-fx-background-color: white; -fx-background-radius: 16; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 20, 0, 0, 0);");
        root.setPrefWidth(550);

        // Header
        javafx.scene.layout.HBox header = new javafx.scene.layout.HBox();
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.setPadding(new javafx.geometry.Insets(24, 32, 24, 32));
        header.setStyle("-fx-border-color: #f1f5f9; -fx-border-width: 0 0 1 0;");

        javafx.scene.layout.VBox titleBox = new javafx.scene.layout.VBox(4);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subtitleLabel = new Label("Preview Cetak");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        javafx.scene.shape.SVGPath closeIcon = new javafx.scene.shape.SVGPath();
        closeIcon.setContent("M6 18L18 6M6 6l12 12");
        closeIcon.setStroke(javafx.scene.paint.Color.web("#94a3b8"));
        closeIcon.setStrokeWidth(2);

        Button closeBtn = new Button();
        closeBtn.setGraphic(closeIcon);
        closeBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> modal.close());

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        header.getChildren().addAll(titleBox, spacer, closeBtn);

        // Content - Preview TextArea
        TextArea preview = new TextArea(content);
        preview.setEditable(false);
        preview.setStyle(
                "-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px; " +
                        "-fx-control-inner-background: #f8fafc; -fx-border-color: #e2e8f0; " +
                        "-fx-border-radius: 8; -fx-background-radius: 8;");
        preview.setPrefHeight(350);
        javafx.scene.layout.VBox.setMargin(preview, new javafx.geometry.Insets(24, 32, 0, 32));

        // Footer
        javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(12);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setPadding(new javafx.geometry.Insets(16, 32, 24, 32));

        Button closeFooterBtn = new Button("Tutup");
        closeFooterBtn.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: 600; -fx-padding: 10 20; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: transparent; -fx-text-fill: #64748b; " +
                        "-fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        closeFooterBtn.setOnAction(e -> modal.close());

        Button saveBtn = new Button("Simpan TXT");
        saveBtn.setStyle(
                "-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 24; " +
                        "-fx-background-radius: 8; -fx-cursor: hand; " +
                        "-fx-background-color: #1E40AF; -fx-text-fill: white;");
        saveBtn.setOnAction(e -> {
            modal.close();
            saveToFile(title, content);
        });

        footer.getChildren().addAll(closeFooterBtn, saveBtn);
        root.getChildren().addAll(header, preview, footer);

        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        modal.setScene(scene);
        modal.showAndWait();
    }

    private void saveToFile(String suggestedName, String content) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Simpan Bukti");
        fc.setInitialFileName(suggestedName.replace(" ", "_") + ".txt");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

        File file = fc.showSaveDialog(null);
        if (file != null) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.print(content);
                ModalUtil.showSuccess("Berhasil", "File berhasil disimpan ke:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Failed to save file", e);
                ModalUtil.showError("Error", "Gagal menyimpan file: " + e.getMessage());
            }
        }
    }

    // =============================================
    // RETUR PENJUALAN LOGIC
    // =============================================

    @FXML
    private void handleSearchInvoice() {
        String inv = txtSearchInvoice.getText().trim();
        if (inv.isEmpty())
            return;

        currentSale = saleDAO.findByInvoiceNumber(inv);
        if (currentSale == null) {
            ModalUtil.showError("Tidak Ditemukan", "Invoice tidak ditemukan.");
            return;
        }

        loadReturnItems(currentSale);
    }

    private void loadReturnItems(Sale sale) {
        ObservableList<ReturnItemRow> rows = FXCollections.observableArrayList();
        for (SaleItem item : sale.getItems()) {
            int returned = salesReturnDAO.getReturnedQuantity(sale.getId(), item.getProductId());
            rows.add(new ReturnItemRow(item, item.getQuantity(), returned));
        }
        tableReturn.setItems(rows);
    }

    @FXML
    private void handleProcessReturn() {
        if (currentSale == null)
            return;

        User user = Session.getInstance().getCurrentUser();
        if (user == null || !Session.getInstance().canManageStore()) {
            ModalUtil.showError("Akses Ditolak", "Hanya Admin Toko yang bisa melakukan retur.");
            return;
        }

        List<SalesReturnItem> returnItems = new ArrayList<>();
        BigDecimal totalReturnAmount = BigDecimal.ZERO;

        for (ReturnItemRow row : tableReturn.getItems()) {
            if (row.getQtyToReturn() > 0) {
                SalesReturnItem sri = new SalesReturnItem();
                sri.setSaleItemId(row.getSaleItem().getId());
                sri.setProductId(row.getSaleItem().getProductId());
                sri.setQtyReturn(row.getQtyToReturn());
                sri.setUnitPrice(row.getSaleItem().getUnitPrice());
                sri.setSnapshotHpp(row.getSaleItem().getHppPerUnit());

                BigDecimal price = row.getSaleItem().getUnitPrice();
                BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(row.getQtyToReturn()));
                sri.setLineTotal(lineTotal);

                returnItems.add(sri);
                totalReturnAmount = totalReturnAmount.add(lineTotal);
            }
        }

        if (returnItems.isEmpty()) {
            ModalUtil.showInfo("Info", "Tidak ada item yang diretur.");
            return;
        }

        if (!SecurityUtil.requestAdminConfirmation("Proses Retur Penjualan")) {
            return;
        }

        // Confirm before processing
        boolean confirmed = ModalUtil.showConfirmDanger("Konfirmasi Retur",
                "Proses retur senilai " + CURRENCY_FMT.format(totalReturnAmount) + "?\nStok akan dikembalikan.");

        if (!confirmed)
            return;

        try {
            SalesReturn ret = new SalesReturn();
            ret.setSaleId(currentSale.getId());
            ret.setUserId(user.getId());
            ret.setTotalAmount(totalReturnAmount);
            ret.setNotes("Return for " + currentSale.getInvoiceNumber());
            ret.setItems(returnItems);

            salesReturnDAO.createReturn(ret);

            ModalUtil.showSuccess("Berhasil", "Retur berhasil diproses.\nNo. Retur: " + ret.getReturnNo());
            tableReturn.getItems().clear();
            txtSearchInvoice.clear();
            currentSale = null;

            // Refresh history
            loadReturnHistory();

        } catch (Exception e) {
            logger.error("Return failed", e);
            ModalUtil.showError("Error", "Gagal memproses retur: " + e.getMessage());
        }
    }

    // =============================================
    // MUTASI STOK LOGIC
    // =============================================

    @FXML
    private void handleSearchProduct() {
        String query = txtSearchProduct.getText();
        List<Product> products = productDAO.findAll();
        selectedProduct = products.stream()
                .filter(p -> p.getName().toLowerCase().contains(query.toLowerCase())
                        || p.getSku().equalsIgnoreCase(query))
                .findFirst().orElse(null);

        if (selectedProduct != null) {
            lblSelectedProduct.setText(selectedProduct.getSku() + " - " + selectedProduct.getName() + " (Stok: "
                    + selectedProduct.getStock() + ")");
        } else {
            lblSelectedProduct.setText("Produk tidak ditemukan");
        }
    }

    @FXML
    private void handleProcessMutation() {
        if (selectedProduct == null) {
            ModalUtil.showError("Error", "Pilih produk dulu.");
            return;
        }

        try {
            User user = Session.getInstance().getCurrentUser();
            if (user == null || !Session.getInstance().canManageStore()) {
                ModalUtil.showError("Akses Ditolak", "Hanya Admin Toko yang bisa melakukan mutasi.");
                return;
            }
            int delta = Integer.parseInt(txtAdjQty.getText());
            if (delta == 0)
                throw new NumberFormatException();

            String reason = txtAdjReason.getText();
            if (reason.isEmpty()) {
                ModalUtil.showError("Error", "Alasan wajib diisi.");
                return;
            }

            if (delta < 0 && (selectedProduct.getStock() + delta < 0)) {
                ModalUtil.showError("Error", "Stok tidak cukup untuk pengurangan.");
                return;
            }

            if (!SecurityUtil.requestAdminConfirmation("Proses Mutasi Stok")) {
                return;
            }

            // Confirm before processing
            String deltaSign = delta >= 0 ? "+" : "";
            boolean confirmed = ModalUtil.showConfirm("Konfirmasi Mutasi",
                    "Proses mutasi stok " + deltaSign + delta + " untuk produk " + selectedProduct.getName() + "?");

            if (!confirmed)
                return;

            StockAdjustment adj = new StockAdjustment();
            adj.setUserId(user.getId());
            adj.setReason(reason);

            StockAdjustmentItem item = new StockAdjustmentItem();
            item.setProductId(selectedProduct.getId());
            item.setQtyDelta(delta);
            item.setNote(reason);

            adj.setItems(List.of(item));

            adjustmentDAO.createAdjustment(adj);

            ModalUtil.showSuccess("Berhasil", "Mutasi stok berhasil.\nNo. Mutasi: " + adj.getAdjNo());
            txtAdjQty.clear();
            txtAdjReason.clear();
            lblSelectedProduct.setText("-");
            selectedProduct = null;

            // Refresh history
            loadAdjustmentHistory();

        } catch (NumberFormatException e) {
            ModalUtil.showError("Error", "Jumlah harus angka valid dan bukan 0.");
        } catch (Exception e) {
            logger.error("Adjustment failed", e);
            ModalUtil.showError("Error", "Gagal: " + e.getMessage());
        }
    }

    // =============================================
    // EXPORT HANDLERS
    // =============================================

    @FXML
    private void handleExportReturnPdf() {
        ModalUtil.showInfo("Info", "Fitur Export PDF untuk Retur akan segera hadir.");
    }

    @FXML
    private void handleExportReturnExcel() {
        ExcelExportUtil.export(
                (Stage) tableReturnHistory.getScene().getWindow(),
                "Riwayat Retur",
                Arrays.asList("No. Retur", "Invoice Asal", "Tanggal", "Total", "Oleh"),
                Arrays.asList(
                        (SalesReturn item) -> item.getReturnNo(),
                        (SalesReturn item) -> item.getSaleInvoiceNumber(),
                        (SalesReturn item) -> item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_FMT) : "",
                        (SalesReturn item) -> item.getTotalAmount() != null ? CURRENCY_FMT.format(item.getTotalAmount())
                                : "0",
                        (SalesReturn item) -> item.getCreatedByName()),
                FXCollections.observableArrayList(filteredReturns));
    }

    @FXML
    private void handleExportMutationPdf() {
        ModalUtil.showInfo("Info", "Fitur Export PDF untuk Mutasi akan segera hadir.");
    }

    @FXML
    private void handleExportMutationExcel() {
        ExcelExportUtil.export(
                (Stage) tableAdjustmentHistory.getScene().getWindow(),
                "Riwayat Mutasi",
                Arrays.asList("No. Mutasi", "Tanggal", "Alasan", "Oleh"),
                Arrays.asList(
                        (StockAdjustment item) -> item.getAdjNo(),
                        (StockAdjustment item) -> item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_FMT)
                                : "",
                        (StockAdjustment item) -> item.getReason(),
                        (StockAdjustment item) -> item.getCreatedByName()),
                FXCollections.observableArrayList(filteredAdjustments));
    }

    // =============================================
    // INNER CLASS FOR TABLE
    // =============================================

    public static class ReturnItemRow {
        private SaleItem saleItem;
        private int qtySold;
        private int qtyPreviouslyReturned;
        private int qtyToReturn;

        public ReturnItemRow(SaleItem item, int qtySold, int returned) {
            this.saleItem = item;
            this.qtySold = qtySold;
            this.qtyPreviouslyReturned = returned;
            this.qtyToReturn = 0;
        }

        public SaleItem getSaleItem() {
            return saleItem;
        }

        public int getQtySold() {
            return qtySold;
        }

        public int getQtyPreviouslyReturned() {
            return qtyPreviouslyReturned;
        }

        public int getQtyToReturn() {
            return qtyToReturn;
        }

        public void setQtyToReturn(int q) {
            this.qtyToReturn = q;
        }
    }
}


