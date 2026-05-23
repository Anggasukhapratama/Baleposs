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

/**
 * Data Access Object untuk Pembelian (Purchase)
 */
public class PurchaseDAO {
    private static final Logger logger = LoggerFactory.getLogger(PurchaseDAO.class);
    private static final DateTimeFormatter PO_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Membuat transaksi pembelian secara atomic
     * 1. Generate nomor PO
     * 2. Insert Purchase Header
     * 3. Insert Purchase Items
     * 4. Update stock produk (tambah)
     * 5. Insert stock movements
     * 6. Update HPP produk jika diminta
     */
    public void createPurchase(Purchase purchase, boolean updateProductHpp) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            // 1. Generate PO Number
            String poNumber = generatePONumber(conn);
            purchase.setPurchaseNumber(poNumber);
            purchase.setPurchaseDate(LocalDateTime.now());

            // 2. Insert Purchase Header
            long purchaseId = insertPurchaseHeader(conn, purchase);
            purchase.setId(purchaseId);

            // 3. Process Items
            for (PurchaseItem item : purchase.getItems()) {
                item.setPurchaseId(purchaseId);

                // Get current product state
                int currentStock = getProductStock(conn, item.getProductId());

                // Insert Item
                insertPurchaseItem(conn, item);

                // Update Stock (Add)
                updateProductStock(conn, item.getProductId(), item.getQuantity());

                // Update HPP if requested
                if (updateProductHpp) {
                    updateProductHpp(conn, item.getProductId(), item.getHppPerUnit());
                }

                // Create Stock Movement
                StockMovement movement = new StockMovement();
                movement.setProductId(item.getProductId());
                movement.setMovementType(StockMovement.MovementType.PURCHASE_IN);
                movement.setReferenceType("PURCHASE");
                movement.setReferenceId(purchaseId);
                movement.setQuantityChange(item.getQuantity());
                movement.setStockBefore(currentStock);
                movement.setStockAfter(currentStock + item.getQuantity());
                movement.setCreatedBy(purchase.getCreatedBy());
                movement.setNotes("Pembelian " + poNumber);

                insertStockMovement(conn, movement);
            }

            conn.commit();
            logger.info("Purchase transaction completed successfully: {}", poNumber);

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

    public List<Purchase> findAll() {
        List<Purchase> purchases = new ArrayList<>();
        String sql = "SELECT p.*, s.code as supplier_code, s.name as supplier_name, u.full_name as created_by_name " +
                "FROM purchases p " +
                "LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "LEFT JOIN users u ON p.created_by = u.id " +
                "ORDER BY p.purchase_date DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                purchases.add(mapResultSetToPurchase(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all purchases", e);
        }
        return purchases;
    }

    public List<Purchase> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Purchase> purchases = new ArrayList<>();
        String sql = "SELECT p.*, s.code as supplier_code, s.name as supplier_name, u.full_name as created_by_name " +
                "FROM purchases p " +
                "LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "LEFT JOIN users u ON p.created_by = u.id " +
                "WHERE " + SqlDialect.dateExpression("p.purchase_date") + " BETWEEN ? AND ? " +
                "ORDER BY p.purchase_date DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapResultSetToPurchase(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding purchases by date range", e);
        }
        return purchases;
    }

    public Purchase findByIdWithItems(Long id) {
        String sql = "SELECT p.*, s.code as supplier_code, s.name as supplier_name, u.full_name as created_by_name " +
                "FROM purchases p " +
                "LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "LEFT JOIN users u ON p.created_by = u.id " +
                "WHERE p.id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Purchase purchase = mapResultSetToPurchase(rs);
                    purchase.setItems(findPurchaseItems(conn, id));
                    return purchase;
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding purchase by id: {}", id, e);
        }
        return null;
    }

    public Purchase findByNumber(String poNumber) {
        String sql = "SELECT p.*, s.code as supplier_code, s.name as supplier_name, u.full_name as created_by_name " +
                "FROM purchases p " +
                "LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                "LEFT JOIN users u ON p.created_by = u.id " +
                "WHERE p.purchase_number = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, poNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Purchase purchase = mapResultSetToPurchase(rs);
                    purchase.setItems(findPurchaseItems(conn, purchase.getId()));
                    return purchase;
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding purchase by number: {}", poNumber, e);
        }
        return null;
    }

    private List<PurchaseItem> findPurchaseItems(Connection conn, Long purchaseId) throws SQLException {
        List<PurchaseItem> items = new ArrayList<>();
        String sql = "SELECT pi.*, p.sku as product_sku, p.name as product_name " +
                "FROM purchase_items pi " +
                "LEFT JOIN products p ON pi.product_id = p.id " +
                "WHERE pi.purchase_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, purchaseId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    PurchaseItem item = new PurchaseItem();
                    item.setId(rs.getLong("id"));
                    item.setPurchaseId(rs.getLong("purchase_id"));
                    item.setProductId(rs.getLong("product_id"));
                    item.setProductSku(rs.getString("product_sku"));
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setHppPerUnit(BigDecimal.valueOf(rs.getLong("hpp_per_unit")));
                    item.setSubtotal(BigDecimal.valueOf(rs.getLong("subtotal")));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private String generatePONumber(Connection conn) throws SQLException {
        String datePart = LocalDate.now().format(PO_DATE_FMT);
        String prefix = "PO";

        String sqlSelect = "SELECT last_number FROM invoice_sequences WHERE prefix = ? AND date_part = ?";
        String sqlUpdate = "UPDATE invoice_sequences SET last_number = last_number + 1 WHERE prefix = ? AND date_part = ?";
        String sqlInsert = "INSERT INTO invoice_sequences (prefix, date_part, last_number) VALUES (?, ?, 1)";

        int nextNum = 1;

        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
            pstmt.setString(1, prefix);
            pstmt.setString(2, datePart);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    nextNum = rs.getInt("last_number") + 1;
                    try (PreparedStatement uPstmt = conn.prepareStatement(sqlUpdate)) {
                        uPstmt.setString(1, prefix);
                        uPstmt.setString(2, datePart);
                        uPstmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement iPstmt = conn.prepareStatement(sqlInsert)) {
                        iPstmt.setString(1, prefix);
                        iPstmt.setString(2, datePart);
                        iPstmt.executeUpdate();
                    }
                }
            }
        }

        return String.format("%s-%s-%04d", prefix, datePart, nextNum);
    }

    private long insertPurchaseHeader(Connection conn, Purchase purchase) throws SQLException {
        String sql = "INSERT INTO purchases (purchase_number, supplier_id, purchase_date, total_amount, notes, status, created_by) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, purchase.getPurchaseNumber());
            pstmt.setLong(2, purchase.getSupplierId());
            if (DatabaseConfig.getDialect() == DatabaseDialect.POSTGRES) {
                pstmt.setTimestamp(3, java.sql.Timestamp.valueOf(purchase.getPurchaseDate()));
            } else {
                pstmt.setString(3, purchase.getPurchaseDate().toString());
            }
            pstmt.setInt(4, purchase.getTotalAmount().intValue());
            pstmt.setString(5, purchase.getNotes());
            pstmt.setString(6, Purchase.Status.COMPLETED.name());
            pstmt.setLong(7, purchase.getCreatedBy());

            pstmt.executeUpdate();

            // SQLite style: get last inserted ID
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT " + SqlDialect.lastInsertIdExpression() + "")) {
                if (rs.next()) {
                    return rs.getLong(1);
                } else {
                    throw new SQLException("Gagal membuat pembelian, ID tidak ditemukan.");
                }
            }
        }
    }

    private void insertPurchaseItem(Connection conn, PurchaseItem item) throws SQLException {
        String sql = "INSERT INTO purchase_items (purchase_id, product_id, quantity, hpp_per_unit, subtotal) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, item.getPurchaseId());
            pstmt.setLong(2, item.getProductId());
            pstmt.setInt(3, item.getQuantity());
            pstmt.setInt(4, item.getHppPerUnit().intValue());
            pstmt.setInt(5, item.getSubtotal().intValue());

            pstmt.executeUpdate();
        }
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

    private void updateProductHpp(Connection conn, Long productId, BigDecimal newHpp) throws SQLException {
        // Get current HPP and margin to recalculate selling price
        String sqlSelect = "SELECT margin_percent FROM products WHERE id = ?";
        double marginPercent = 10.0;

        try (PreparedStatement pstmt = conn.prepareStatement(sqlSelect)) {
            pstmt.setLong(1, productId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    marginPercent = rs.getDouble("margin_percent");
                }
            }
        }

        // Calculate new selling price
        BigDecimal marginAmount = newHpp.multiply(BigDecimal.valueOf(marginPercent))
                .divide(BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
        BigDecimal sellingPrice = newHpp.add(marginAmount).setScale(0, java.math.RoundingMode.HALF_UP);

        String sqlUpdate = "UPDATE products SET hpp = ?, selling_price = ?, updated_at = " + SqlDialect.nowExpression() + " WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
            pstmt.setInt(1, newHpp.intValue());
            pstmt.setInt(2, sellingPrice.intValue());
            pstmt.setLong(3, productId);
            pstmt.executeUpdate();
        }
    }

    private void insertStockMovement(Connection conn, StockMovement movement) throws SQLException {
        String sql = "INSERT INTO stock_movements (product_id, movement_type, reference_type, reference_id, quantity_change, stock_before, stock_after, notes, created_by) "
                +
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

    private Purchase mapResultSetToPurchase(ResultSet rs) throws SQLException {
        Purchase p = new Purchase();
        p.setId(rs.getLong("id"));
        p.setPurchaseNumber(rs.getString("purchase_number"));
        p.setSupplierId(rs.getLong("supplier_id"));
        p.setSupplierCode(rs.getString("supplier_code"));
        p.setSupplierName(rs.getString("supplier_name"));

        String dateStr = rs.getString("purchase_date");
        if (dateStr != null) {
            p.setPurchaseDate(LocalDateTime.parse(dateStr.replace(" ", "T")));
        }

        p.setTotalAmount(BigDecimal.valueOf(rs.getLong("total_amount")));
        p.setNotes(rs.getString("notes"));
        p.setStatus(Purchase.Status.valueOf(rs.getString("status")));
        p.setCreatedBy(rs.getLong("created_by"));
        p.setCreatedByName(rs.getString("created_by_name"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            p.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        return p;
    }
}


