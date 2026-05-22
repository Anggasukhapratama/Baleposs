package com.baletpos.controller;

import com.baletpos.model.Sale;
import com.baletpos.model.SalePayment;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class PaymentController {

    @FXML
    private Label grandTotalLabel;
    @FXML
    private VBox paymentRowsBox;
    @FXML
    private Label totalPaidLabel;
    @FXML
    private Label remainingLabel;
    @FXML
    private Label changeLabel;
    @FXML
    private Button payButton;

    private BigDecimal grandTotal = BigDecimal.ZERO;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));
    private Runnable onCancel;
    private Consumer<List<SalePayment>> onPay;

    public void initialize() {
        currencyFormat.setMaximumFractionDigits(0);
    }

    public void setGrandTotal(BigDecimal total) {
        this.grandTotal = total;
        grandTotalLabel.setText(currencyFormat.format(total));

        // Add default row (CASH full amount)
        addPaymentRow(Sale.PaymentMethod.CASH, total);
        calculateState();
    }

    public void setOnPay(Consumer<List<SalePayment>> onPay) {
        this.onPay = onPay;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @FXML
    private void handleAddMethod() {
        addPaymentRow(Sale.PaymentMethod.CASH, BigDecimal.ZERO);
        calculateState();
    }

    @FXML
    private void handleCancel() {
        if (onCancel != null)
            onCancel.run();
        closeStage();
    }

    @FXML
    private void handlePay() {
        if (payButton.isDisabled())
            return;

        List<SalePayment> payments = new ArrayList<>();

        for (javafx.scene.Node node : paymentRowsBox.getChildren()) {
            if (node instanceof PaymentRowUI) {
                PaymentRowUI row = (PaymentRowUI) node;
                SalePayment p = new SalePayment();
                p.setMethod(row.getMethod().name());
                p.setAmount(row.getAmount());
                p.setRefNo(row.getRefNo());
                payments.add(p);
            }
        }

        if (onPay != null)
            onPay.accept(payments);
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) payButton.getScene().getWindow();
        if (stage != null)
            stage.close();
    }

    private void addPaymentRow(Sale.PaymentMethod defaultMethod, BigDecimal defaultAmount) {
        PaymentRowUI row = new PaymentRowUI(defaultMethod, defaultAmount);
        row.setRemoveHandler(() -> {
            paymentRowsBox.getChildren().remove(row);
            calculateState();
        });
        row.setChangeListener(this::calculateState);
        paymentRowsBox.getChildren().add(row);
    }

    private void calculateState() {
        BigDecimal totalInput = BigDecimal.ZERO;
        boolean hasCash = false;
        boolean allValid = true;
        boolean hasRow = false;

        for (javafx.scene.Node node : paymentRowsBox.getChildren()) {
            if (node instanceof PaymentRowUI) {
                hasRow = true;
                PaymentRowUI row = (PaymentRowUI) node;
                BigDecimal amt = row.getAmount();
                totalInput = totalInput.add(amt);
                if (row.getMethod() == Sale.PaymentMethod.CASH)
                    hasCash = true;

                if (!row.isValid())
                    allValid = false;
            }
        }

        totalPaidLabel.setText(currencyFormat.format(totalInput));

        BigDecimal diff = totalInput.subtract(grandTotal);

        if (diff.compareTo(BigDecimal.ZERO) >= 0) {
            // Paid excess or exact
            remainingLabel.setText(currencyFormat.format(BigDecimal.ZERO));
            changeLabel.setText(currencyFormat.format(diff));

            // Validation:
            // 1. If NO CASH, total must be exact (change == 0).
            if (!hasCash && diff.compareTo(BigDecimal.ZERO) > 0) {
                remainingLabel.setText("Non-Tunai harus pas!"); // Error msg override
                payButton.setDisable(true);
            } else {
                payButton.setDisable(!allValid || !hasRow);
            }
        } else {
            // Underpaid
            remainingLabel.setText(currencyFormat.format(diff.abs()));
            changeLabel.setText(currencyFormat.format(BigDecimal.ZERO));
            payButton.setDisable(true);
        }
    }

    // Internal Class for Row Logic
    private class PaymentRowUI extends HBox {
        private ComboBox<Sale.PaymentMethod> methodCombo;
        private TextField amountField;
        private TextField refField;
        private Button removeBtn;
        private Runnable onRemove;
        private Runnable onChange;

        public PaymentRowUI(Sale.PaymentMethod method, BigDecimal amount) {
            setSpacing(12);
            setAlignment(Pos.CENTER_LEFT);
            setPadding(new javafx.geometry.Insets(8, 0, 8, 0));
            setStyle("-fx-border-color: #f1f5f9; -fx-border-width: 0 0 1 0;");

            methodCombo = new ComboBox<>();
            methodCombo.getItems().setAll(
                    Sale.PaymentMethod.CASH,
                    Sale.PaymentMethod.TRANSFER_BCA,
                    Sale.PaymentMethod.TRANSFER_MANDIRI,
                    Sale.PaymentMethod.QRIS,
                    Sale.PaymentMethod.AKULAKU,
                    Sale.PaymentMethod.KREDIVO);
            methodCombo.setValue(method);
            methodCombo.setPrefWidth(150);
            methodCombo.setStyle(
                    "-fx-font-size: 13px; -fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 4;");

            String amtStr = "";
            if (amount.compareTo(BigDecimal.ZERO) > 0)
                amtStr = String.format("%.0f", amount);
            amountField = new TextField(amtStr);
            amountField.setPromptText("Nominal");
            amountField.setPrefWidth(140);
            amountField.setStyle(
                    "-fx-font-size: 13px; -fx-background-color: #fff; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-padding: 8;");

            refField = new TextField();
            refField.setPromptText("No. Ref / Approval");
            refField.setPrefWidth(160);
            refField.setStyle(
                    "-fx-font-size: 13px; -fx-background-color: #fff; -fx-border-color: #e2e8f0; -fx-border-radius: 4; -fx-padding: 8;");
            HBox.setHgrow(refField, Priority.ALWAYS);

            removeBtn = new Button("X");
            removeBtn.setStyle(
                    "-fx-text-fill: #ef4444; -fx-background-color: #fef2f2; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12px; -fx-min-width: 36; -fx-min-height: 36;");
            removeBtn.setOnAction(e -> {
                if (onRemove != null)
                    onRemove.run();
            });

            // Initial State Logic
            updateRefVisibility();

            // Listeners
            methodCombo.setOnAction(e -> {
                updateRefVisibility();
                if (onChange != null)
                    onChange.run();
            });

            amountField.textProperty().addListener((obs, old, newVal) -> {
                if (!newVal.matches("\\d*")) {
                    amountField.setText(newVal.replaceAll("[^\\d]", ""));
                }
                if (onChange != null)
                    onChange.run();
            });

            refField.textProperty().addListener(o -> {
                if (onChange != null)
                    onChange.run();
            });

            getChildren().addAll(methodCombo, amountField, refField, removeBtn);
        }

        private void updateRefVisibility() {
            Sale.PaymentMethod m = methodCombo.getValue();
            boolean needRef = m != Sale.PaymentMethod.CASH;
            refField.setDisable(!needRef);
            if (!needRef)
                refField.clear();

            // Paylater methods need approval number
            if (m == Sale.PaymentMethod.AKULAKU || m == Sale.PaymentMethod.KREDIVO) {
                refField.setPromptText("WAJIB: No. Approval");
            } else {
                refField.setPromptText("No. Ref (Wajib)");
            }
        }

        public void setRemoveHandler(Runnable r) {
            this.onRemove = r;
        }

        public void setChangeListener(Runnable r) {
            this.onChange = r;
        }

        public Sale.PaymentMethod getMethod() {
            return methodCombo.getValue();
        }

        public BigDecimal getAmount() {
            String txt = amountField.getText();
            if (txt.isEmpty())
                return BigDecimal.ZERO;
            return new BigDecimal(txt);
        }

        public String getRefNo() {
            return refField.getText();
        }

        public boolean isValid() {
            Sale.PaymentMethod m = getMethod();
            BigDecimal amt = getAmount();
            if (amt.compareTo(BigDecimal.ZERO) <= 0)
                return false;

            // Mandatory REF for all non-cash
            if (m != Sale.PaymentMethod.CASH) {
                if (refField.getText().isBlank())
                    return false;
            }
            return true;
        }
    }
}


