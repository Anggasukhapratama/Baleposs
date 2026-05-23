package com.baletpos.dao;

import com.baletpos.config.DatabaseConfig;
import com.baletpos.config.DatabaseDialect;
import com.baletpos.config.SqlDialect;
import com.baletpos.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SaleDAO {
    private static final Logger logger = LoggerFactory.getLogger(SaleDAO.class);
    private static final DateTimeFormatter INV_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    /**
     * Executes a sale transaction atomically.
     * 1. Insert Sale Header
     * 2. Insert Sale Items
     * 3. Update Product Stock
     * 4. Insert Stock Movements
     */
    public void createSale(Sale sale) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false); // Start Transaction

            // 1. Generate Invoice Number
            String invoiceNumber = generateInvoiceNumber(conn);
            sale.setInvoiceNumber(invoiceNumber);
            sale.setSaleDate(LocalDateTime.now());

            // 2. Insert Sale Header
            long saleId = insertSaleHeader(conn, sale);
            sale.setId(saleId);

            // 3. Process Items
            for (SaleItem item : sale.getItems()) {
                item.setSaleId(saleId);

                // Get current product state for stock movement
                int currentStock = getProductStock(conn, item.getProductId());

                // Validate stock
                if (currentStock < item.getQuantity()) {
                    throw new SQLException("Stok tidak mencukupi untuk produk: " + item.getProductName());
                }

                // Insert Item
                insertSaleItem(conn, item);

                // Update Stock (Reduce)
                updateProductStock(conn, item.getProductId(), -item.getQuantity());

                // Create Stock Movement
                StockMovement movement = new StockMovement();
                movement.setProductId(item.getProductId());
                movement.setMovementType(StockMovement.MovementType.SALE_OUT);
                movement.setReferenceType("SALE");
                movement.setReferenceId(saleId);
                movement.setQuantityChange(-item.getQuantity());
                movement.setStockBefore(currentStock);
                movement.setStockAfter(currentStock - item.getQuantity());
                movement.setCreatedBy(sale.getCreatedBy());
                movement.setNotes("Sale " + invoiceNumber);

                insertStockMovement(conn, movement);

                processBonusStockOut(conn, item, saleId, sale.getCreatedBy(), invoiceNumber);
            }

            // 4. Insert Payments
            insertSalePayments(conn, sale.getPayments(), saleId);

            conn.commit();
            logger.info("Sale transaction completed successfully: {}", invoiceNumber);

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                    logger.error("Transaction rolled back due to error: {}", e.getMessage());
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            throw new SQLException("Transaction failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * VOID transaksi penjualan (hanya ADMIN)
     * 1. Set status = VOIDED
     * 2. Kembalikan stok untuk setiap item
     * 3. Insert stock movements (VOID_RESTORE)
     * 4. Log ke audit_logs
     */
    public void voidSale(Long saleId, Long voidedBy, String voidReason) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            // Check if sale exists and is not already voided
            Sale sale = findByIdWithItems(conn, saleId);
            if (sale == null) {
                throw new SQLException("Transaksi tidak ditemukan");
            }
            if (sale.getStatus() == Sale.Status.VOIDED) {
                throw new SQLException("Transaksi sudah di-VOID sebelumnya");
            }

            // Check if sale has been returned (cannot void returned sales)
            String checkReturnSql = "SELECT COUNT(*) FROM sales_returns WHERE sale_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(checkReturnSql)) {
                pstmt.setLong(1, saleId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new SQLException("Transaksi sudah memiliki retur, tidak dapat di-VOID");
                    }
                }
            }

            // 1. Update sale status
            String sqlUpdate = "UPDATE sales SET status = 'VOIDED', void_reason = ?, voided_by = ?, " +
                    "voided_at = " + SqlDialect.nowExpression() + " WHERE id = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
                pstmt.setString(1, voidReason);
                pstmt.setLong(2, voidedBy);
                pstmt.setLong(3, saleId);
                pstmt.executeUpdate();
            }

            // 2. Restore stock for each item
            for (SaleItem item : sale.getItems()) {
                int currentStock = getProductStock(conn, item.getProductId());

                // Restore stock
                updateProductStock(conn, item.getProductId(), item.getQuantity());

                // Create stock movement
                StockMovement movement = new StockMovement();
                movement.setProductId(item.getProductId());
                movement.setMovementType(StockMovement.MovementType.VOID_RESTORE);
                movement.setReferenceType("SALE_VOID");
                movement.setReferenceId(saleId);
                movement.setQuantityChange(item.getQuantity());
                movement.setStockBefore(currentStock);
                movement.setStockAfter(currentStock + item.getQuantity());
                movement.setCreatedBy(voidedBy);
                movement.setNotes("VOID " + sale.getInvoiceNumber() + " - " + voidReason);

                insertStockMovement(conn, movement);

                restoreBonusStock(conn, item, saleId, voidedBy, sale.getInvoiceNumber(), voidReason);
            }

            conn.commit();

            // 3. Audit log (outside transaction)
            auditLogDAO.log(voidedBy, "VOID_SALE", "sales", saleId,
                    "status=COMPLETED,invoice=" + sale.getInvoiceNumber(),
                    "status=VOIDED,reason=" + voidReason);

            logger.info("Sale voided successfully: {}", sale.getInvoiceNumber());

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back void transaction", ex);
                }
            }
            throw new SQLException("VOID failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    public List<Sale> findAll() {
        List<Sale> sales = new ArrayList<>();
        String sql = "SELECT s.*, c.name as customer_name, u.full_name as created_by_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "LEFT JOIN users u ON s.created_by = u.id " +
                "ORDER BY s.sale_date DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                sales.add(mapResultSetToSale(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all sales", e);
        }
        return sales;
    }

    public List<Sale> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Sale> sales = new ArrayList<>();
        String sql = "SELECT s.*, c.name as customer_name, u.full_name as created_by_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "LEFT JOIN users u ON s.created_by = u.id " +
                "WHERE " + SqlDialect.dateExpression("s.sale_date") + " BETWEEN ? AND ? " +
                "ORDER BY s.sale_date DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapResultSetToSale(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding sales by date range", e);
        }
        return sales;
    }

    public Sale findById(Long id) {
        String sql = "SELECT s.*, c.name as customer_name, u.full_name as created_by_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "LEFT JOIN users u ON s.created_by = u.id " +
                "WHERE s.id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Sale sale = mapResultSetToSale(rs);
                    sale.setItems(findSaleItems(conn, id));
                    return sale;
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding sale by id: {}", id, e);
        }
        return null;
    }

    public Sale findByInvoiceNumber(String invoiceNumber) {
        String sql = "SELECT s.*, c.name as customer_name, u.full_name as created_by_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "LEFT JOIN users u ON s.created_by = u.id " +
                "WHERE s.invoice_number = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, invoiceNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Sale sale = mapResultSetToSale(rs);
                    sale.setItems(findSaleItems(conn, sale.getId()));
                    sale.setPayments(findSalePayments(conn, sale.getId()));
                    return sale;
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding sale by invoice: {}", invoiceNumber, e);
        }
        return null;
    }

    public List<Sale> searchSales(LocalDate startDate, LocalDate endDate, String query, int limit, int offset) {
        List<Sale> sales = new ArrayList<>();
        // Handle null dates with wide default range
        LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(2100, 12, 31);
        LocalDateTime start = effectiveStart.atStartOfDay();
        LocalDateTime end = effectiveEnd.plusDays(1).atStartOfDay();

        String sql = "SELECT s.*, u.full_name as created_by_name, c.name as customer_name " +
                "FROM sales s " +
                "LEFT JOIN users u ON s.created_by = u.id " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "WHERE s.sale_date >= ? AND s.sale_date < ? " +
                "AND (? = '' OR s.invoice_number LIKE ? OR c.name LIKE ?) " +
                "ORDER BY s.sale_date DESC LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setDateTimeRange(pstmt, 1, 2, start, end);
            String q = (query == null) ? "" : query;
            String qLike = "%" + q + "%";
            pstmt.setString(3, q);
            pstmt.setString(4, qLike);
            pstmt.setString(5, qLike);
            pstmt.setInt(6, limit);
            pstmt.setInt(7, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    sales.add(mapResultSetToSale(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching sales", e);
        }
        return sales;
    }

    public long countSales(LocalDate startDate, LocalDate endDate, String query) {
        // Handle null dates with wide default range
        LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(2100, 12, 31);
        LocalDateTime start = effectiveStart.atStartOfDay();
        LocalDateTime end = effectiveEnd.plusDays(1).atStartOfDay();

        String sql = "SELECT COUNT(*) FROM sales s " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "WHERE s.sale_date >= ? AND s.sale_date < ? " +
                "AND (? = '' OR s.invoice_number LIKE ? OR c.name LIKE ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setDateTimeRange(pstmt, 1, 2, start, end);
            String q = (query == null) ? "" : query;
            String qLike = "%" + q + "%";
            pstmt.setString(3, q);
            pstmt.setString(4, qLike);
            pstmt.setString(5, qLike);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Error counting sales", e);
        }
        return 0;
    }

    public BigDecimal sumSales(LocalDate startDate, LocalDate endDate, String query) {
        LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.of(2000, 1, 1);
        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.of(2100, 12, 31);
        LocalDateTime start = effectiveStart.atStartOfDay();
        LocalDateTime end = effectiveEnd.plusDays(1).atStartOfDay();

        String sql = "SELECT COALESCE(SUM(total_amount), 0) as total FROM sales s " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "WHERE s.status = 'COMPLETED' " +
                "AND s.sale_date >= ? AND s.sale_date < ? " +
                "AND (? = '' OR s.invoice_number LIKE ? OR c.name LIKE ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setDateTimeRange(pstmt, 1, 2, start, end);
            String q = (query == null) ? "" : query;
            String qLike = "%" + q + "%";
            pstmt.setString(3, q);
            pstmt.setString(4, qLike);
            pstmt.setString(5, qLike);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return BigDecimal.valueOf(rs.getLong("total"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error summing sales", e);
        }
        return BigDecimal.ZERO;
    }

    private Sale findByIdWithItems(Connection conn, Long id) throws SQLException {
        String sql = "SELECT s.*, c.name as customer_name, u.full_name as created_by_name " +
                "FROM sales s " +
                "LEFT JOIN customers c ON s.customer_id = c.id " +
                "LEFT JOIN users u ON s.created_by = u.id " +
                "WHERE s.id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Sale sale = mapResultSetToSale(rs);
                    sale.setItems(findSaleItems(conn, id));
                    sale.setPayments(findSalePayments(conn, id));
                    return sale;
                }
            }
        }
        return null;
    }

    public List<SaleItem> findSaleItems(Connection conn, Long saleId) throws SQLException {
        List<SaleItem> items = new ArrayList<>();
        String sql = "SELECT si.*, p.sku as product_sku, p.name as product_name " +
                "FROM sale_items si " +
                "LEFT JOIN products p ON si.product_id = p.id " +
                "WHERE si.sale_id = ? ORDER BY si.id ASC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, saleId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    SaleItem item = new SaleItem();
                    item.setId(rs.getLong("id"));
                    item.setSaleId(rs.getLong("sale_id"));
                    item.setProductId(rs.getLong("product_id"));
                    item.setProductSku(rs.getString("product_sku"));
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(BigDecimal.valueOf(rs.getLong("unit_price")));
                    item.setHppPerUnit(BigDecimal.valueOf(rs.getLong("hpp_per_unit")));
                    item.setDiscountPercent(rs.getDouble("discount_percent"));
                    item.setDiscountAmount(BigDecimal.valueOf(rs.getLong("discount_amount")));
                    item.setSubtotal(BigDecimal.valueOf(rs.getLong("subtotal")));

                    item.setSerialNumber(rs.getString("serial_number"));
                    item.setBuyerName(rs.getString("buyer_name"));
                    item.setBuyerNik(rs.getString("buyer_nik"));
                    item.setBonusProductId(getNullableLong(rs, "bonus_product_id"));
                    item.setBonusProductName(rs.getString("bonus_product_name"));
                    item.setWarrantyLabel(rs.getString("warranty_label"));

                    items.add(item);
                }
            }
        }
        return items;
    }

    private String generateInvoiceNumber(Connection conn) throws SQLException {
        String datePart = LocalDate.now().format(INV_DATE_FMT); // yyyyMMdd
        String prefix = "INV-" + datePart + "-"; // INV-20260109-

        // Find highest index for today
        String sql = "SELECT invoice_number FROM sales WHERE invoice_number LIKE ? ORDER BY invoice_number DESC LIMIT 1";

        int nextNum = 1;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, prefix + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String lastInv = rs.getString("invoice_number");
                    if (lastInv != null && lastInv.length() > prefix.length()) {
                        try {
                            String numPart = lastInv.substring(prefix.length());
                            nextNum = Integer.parseInt(numPart) + 1;
                        } catch (NumberFormatException e) {
                            // ignore, fallback to 1 logic (should not happen if format strict)
                        }
                    }
                }
            }
        }

        return String.format("%s%04d", prefix, nextNum);
    }

    private long insertSaleHeader(Connection conn, Sale sale) throws SQLException {
        String sql = "INSERT INTO sales (invoice_number, customer_id, sale_date, subtotal, discount_percent, " +
                "discount_amount, total_amount, total_hpp, payment_method, payment_amount, change_amount, " +
                "status, technician_id, notes, created_by, payment_type) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // 1. Execute Insert (No Generated Keys flag to avoid driver issues)
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, sale.getInvoiceNumber());
            pstmt.setObject(2, sale.getCustomerId());
            if (DatabaseConfig.getDialect() == DatabaseDialect.POSTGRES) {
                pstmt.setTimestamp(3, java.sql.Timestamp.valueOf(sale.getSaleDate()));
            } else {
                pstmt.setString(3, sale.getSaleDate().format(DB_DATE_FMT));
            }
            pstmt.setInt(4, sale.getSubtotal().intValue());
            pstmt.setDouble(5, sale.getDiscountPercent());
            pstmt.setInt(6, sale.getDiscountAmount().intValue());
            pstmt.setInt(7, sale.getTotalAmount().intValue());
            pstmt.setInt(8, sale.getTotalHpp().intValue());
            pstmt.setString(9, sale.getPaymentMethod().name());
            pstmt.setLong(10, sale.getPaymentAmount().longValue());
            pstmt.setLong(11, sale.getChangeAmount().longValue());
            pstmt.setString(12, Sale.Status.COMPLETED.name());
            pstmt.setObject(13, sale.getTechnicianId());
            pstmt.setString(14, sale.getNotes());
            pstmt.setLong(15, sale.getCreatedBy());
            pstmt.setString(16, sale.getPaymentType().name());

            int affected = pstmt.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating sale failed, no rows affected.");
            }
        }

        // 2. Fetch ID manually
        String sqlId = "SELECT id FROM sales WHERE invoice_number = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlId)) {
            pstmt.setString(1, sale.getInvoiceNumber());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                } else {
                    throw new SQLException("Creating sale failed, ID lookup failed.");
                }
            }
        }
    }

    private void insertSaleItem(Connection conn, SaleItem item) throws SQLException {
        String sql = "INSERT INTO sale_items (sale_id, product_id, quantity, unit_price, hpp_per_unit, " +
                "discount_percent, discount_amount, subtotal, serial_number, buyer_name, buyer_nik, " +
                "bonus_product_id, bonus_product_name, warranty_label) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, item.getSaleId());
            pstmt.setLong(2, item.getProductId());
            pstmt.setInt(3, item.getQuantity());
            pstmt.setInt(4, item.getUnitPrice().intValue());
            pstmt.setInt(5, item.getHppPerUnit().intValue());
            pstmt.setDouble(6, item.getDiscountPercent());
            pstmt.setInt(7, item.getDiscountAmount().intValue());
            pstmt.setInt(8, item.getSubtotal().intValue());
            pstmt.setString(9, item.getSerialNumber());
            pstmt.setString(10, item.getBuyerName());
            pstmt.setString(11, item.getBuyerNik());
            pstmt.setObject(12, item.getBonusProductId());
            pstmt.setString(13, item.getBonusProductName());
            pstmt.setString(14, item.getWarrantyLabel());

            pstmt.executeUpdate();
        }
    }

    private void processBonusStockOut(Connection conn, SaleItem item, long saleId, Long createdBy, String invoiceNumber)
            throws SQLException {
        if (item.getBonusProductId() == null) {
            return;
        }

        int currentStock = getProductStock(conn, item.getBonusProductId());
        int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
        if (currentStock < quantity) {
            throw new SQLException("Stok bonus tidak mencukupi untuk produk: " + item.getBonusProductName());
        }

        updateProductStock(conn, item.getBonusProductId(), -quantity);

        StockMovement movement = new StockMovement();
        movement.setProductId(item.getBonusProductId());
        movement.setMovementType(StockMovement.MovementType.SALE_OUT);
        movement.setReferenceType("SALE_BONUS");
        movement.setReferenceId(saleId);
        movement.setQuantityChange(-quantity);
        movement.setStockBefore(currentStock);
        movement.setStockAfter(currentStock - quantity);
        movement.setCreatedBy(createdBy);
        movement.setNotes("Bonus " + invoiceNumber + " untuk " + item.getProductName());
        insertStockMovement(conn, movement);
    }

    private void restoreBonusStock(Connection conn, SaleItem item, long saleId, Long voidedBy, String invoiceNumber,
            String voidReason) throws SQLException {
        if (item.getBonusProductId() == null) {
            return;
        }

        int currentStock = getProductStock(conn, item.getBonusProductId());
        int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
        updateProductStock(conn, item.getBonusProductId(), quantity);

        StockMovement movement = new StockMovement();
        movement.setProductId(item.getBonusProductId());
        movement.setMovementType(StockMovement.MovementType.VOID_RESTORE);
        movement.setReferenceType("SALE_BONUS_VOID");
        movement.setReferenceId(saleId);
        movement.setQuantityChange(quantity);
        movement.setStockBefore(currentStock);
        movement.setStockAfter(currentStock + quantity);
        movement.setCreatedBy(voidedBy);
        movement.setNotes("VOID bonus " + invoiceNumber + " - " + voidReason);
        insertStockMovement(conn, movement);
    }

    private Long getNullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private void setDateTimeRange(PreparedStatement pstmt, int startIndex, int endIndex,
            LocalDateTime start, LocalDateTime end) throws SQLException {
        if (DatabaseConfig.getDialect() == DatabaseDialect.POSTGRES) {
            pstmt.setTimestamp(startIndex, Timestamp.valueOf(start));
            pstmt.setTimestamp(endIndex, Timestamp.valueOf(end));
            return;
        }

        pstmt.setString(startIndex, start.format(DB_DATE_FMT));
        pstmt.setString(endIndex, end.format(DB_DATE_FMT));
    }

    private int getProductStock(Connection conn, Long productId) throws SQLException {
        String sql = "SELECT stock FROM products WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock");
                }
            }
        }
        return 0;
    }

    private void updateProductStock(Connection conn, Long productId, int change) throws SQLException {
        String sql = "UPDATE products SET stock = stock + ?, updated_at = " + SqlDialect.nowExpression() + " WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, change);
            pstmt.setLong(2, productId);
            pstmt.executeUpdate();
        }
    }

    private void insertStockMovement(Connection conn, StockMovement movement) throws SQLException {
        String sql = "INSERT INTO stock_movements (product_id, movement_type, reference_type, reference_id, " +
                "quantity_change, stock_before, stock_after, notes, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, movement.getProductId());
            pstmt.setString(2, movement.getMovementType().name());
            pstmt.setString(3, movement.getReferenceType());
            pstmt.setLong(4, movement.getReferenceId());
            pstmt.setInt(5, movement.getQuantityChange());
            pstmt.setInt(6, movement.getStockBefore());
            pstmt.setInt(7, movement.getStockAfter());
            pstmt.setString(8, movement.getNotes());
            pstmt.setLong(9, movement.getCreatedBy());

            pstmt.executeUpdate();
        }
    }

    private void insertSalePayments(Connection conn, List<SalePayment> payments, Long saleId) throws SQLException {
        if (payments == null || payments.isEmpty())
            return;
        String sql = "INSERT INTO sale_payments (sale_id, method, amount, ref_no) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (SalePayment p : payments) {
                pstmt.setLong(1, saleId);
                pstmt.setString(2, p.getMethod());
                pstmt.setLong(3, p.getAmount().longValue());
                pstmt.setString(4, p.getRefNo());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
        }
    }

    private List<SalePayment> findSalePayments(Connection conn, Long saleId) throws SQLException {
        List<SalePayment> list = new ArrayList<>();
        String sql = "SELECT * FROM sale_payments WHERE sale_id = ? ORDER BY id ASC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, saleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    SalePayment p = new SalePayment();
                    p.setId(rs.getLong("id"));
                    p.setSaleId(rs.getLong("sale_id"));
                    p.setMethod(rs.getString("method"));
                    p.setAmount(BigDecimal.valueOf(rs.getLong("amount")));
                    p.setRefNo(rs.getString("ref_no"));
                    list.add(p);
                }
            }
        }
        return list;
    }

    private Sale mapResultSetToSale(ResultSet rs) throws SQLException {
        Sale sale = new Sale();
        sale.setId(rs.getLong("id"));
        sale.setInvoiceNumber(rs.getString("invoice_number"));
        sale.setCustomerId(rs.getLong("customer_id"));
        sale.setCustomerName(rs.getString("customer_name"));

        String dateStr = rs.getString("sale_date");
        if (dateStr != null) {
            sale.setSaleDate(LocalDateTime.parse(dateStr.replace(" ", "T")));
        }

        sale.setSubtotal(BigDecimal.valueOf(rs.getLong("subtotal")));
        sale.setDiscountPercent(rs.getDouble("discount_percent"));
        sale.setDiscountAmount(BigDecimal.valueOf(rs.getLong("discount_amount")));
        sale.setTotalAmount(BigDecimal.valueOf(rs.getLong("total_amount")));
        sale.setTotalHpp(BigDecimal.valueOf(rs.getLong("total_hpp")));
        sale.setPaymentMethod(Sale.PaymentMethod.valueOf(rs.getString("payment_method")));
        String pType = rs.getString("payment_type");
        if (pType != null)
            sale.setPaymentType(Sale.PaymentType.valueOf(pType));
        sale.setPaymentAmount(BigDecimal.valueOf(rs.getLong("payment_amount")));
        sale.setChangeAmount(BigDecimal.valueOf(rs.getLong("change_amount")));
        sale.setStatus(Sale.Status.valueOf(rs.getString("status")));
        sale.setVoidReason(rs.getString("void_reason"));

        Object voidedByObj = rs.getObject("voided_by");
        if (voidedByObj != null) {
            sale.setVoidedBy(((Number) voidedByObj).longValue());
        }

        String voidedAtStr = rs.getString("voided_at");
        if (voidedAtStr != null && !voidedAtStr.isEmpty()) {
            sale.setVoidedAt(LocalDateTime.parse(voidedAtStr.replace(" ", "T")));
        }

        Object techIdObj = rs.getObject("technician_id");
        if (techIdObj != null) {
            sale.setTechnicianId(((Number) techIdObj).longValue());
        }

        sale.setNotes(rs.getString("notes"));
        sale.setCreatedBy(rs.getLong("created_by"));
        sale.setCreatedByName(rs.getString("created_by_name"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            sale.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        return sale;
    }
}


