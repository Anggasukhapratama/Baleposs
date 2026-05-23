package com.baletpos.dao;

import com.baletpos.config.DatabaseConfig;
import com.baletpos.config.SqlDialect;
import com.baletpos.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductDAO {
    private static final Logger logger = LoggerFactory.getLogger(ProductDAO.class);
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Product> findAll() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT p.*, c.name as category_name, b.name as brand_name " +
                "FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.id " +
                "LEFT JOIN brands b ON p.brand_id = b.id " +
                "WHERE p.is_active = 1";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                products.add(mapResultSetToProduct(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all products", e);
        }
        return products;
    }

    /**
     * Get paginated products with optional search filter
     * Uses DB-level LIMIT/OFFSET for performance
     */
    public List<Product> findAllPaged(int limit, int offset, String searchQuery, String categoryFilter) {
        List<Product> products = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.*, c.name as category_name, b.name as brand_name ");
        sql.append("FROM products p ");
        sql.append("LEFT JOIN categories c ON p.category_id = c.id ");
        sql.append("LEFT JOIN brands b ON p.brand_id = b.id ");
        sql.append("WHERE p.is_active = 1 ");

        if (searchQuery != null && !searchQuery.isBlank()) {
            sql.append("AND (p.name LIKE ? OR p.sku LIKE ?) ");
        }
        if (categoryFilter != null && !categoryFilter.equals("SEMUA")) {
            sql.append("AND p.product_type_code = ? ");
        }

        sql.append("ORDER BY p.name ");
        sql.append("LIMIT ? OFFSET ?");

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (searchQuery != null && !searchQuery.isBlank()) {
                String pattern = "%" + searchQuery + "%";
                pstmt.setString(paramIndex++, pattern);
                pstmt.setString(paramIndex++, pattern);
            }
            if (categoryFilter != null && !categoryFilter.equals("SEMUA")) {
                pstmt.setString(paramIndex++, categoryFilter);
            }
            pstmt.setInt(paramIndex++, limit);
            pstmt.setInt(paramIndex, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding paged products", e);
        }
        return products;
    }

    /**
     * Count total products matching filter (for pagination)
     */
    public int countFiltered(String searchQuery, String categoryFilter) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM products p WHERE p.is_active = 1 ");

        if (searchQuery != null && !searchQuery.isBlank()) {
            sql.append("AND (p.name LIKE ? OR p.sku LIKE ?) ");
        }
        if (categoryFilter != null && !categoryFilter.equals("SEMUA")) {
            sql.append("AND p.product_type_code = ? ");
        }

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (searchQuery != null && !searchQuery.isBlank()) {
                String pattern = "%" + searchQuery + "%";
                pstmt.setString(paramIndex++, pattern);
                pstmt.setString(paramIndex++, pattern);
            }
            if (categoryFilter != null && !categoryFilter.equals("SEMUA")) {
                pstmt.setString(paramIndex, categoryFilter);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting products", e);
        }
        return 0;
    }

    public List<Product> search(String query) {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT p.*, c.name as category_name, b.name as brand_name " +
                "FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.id " +
                "LEFT JOIN brands b ON p.brand_id = b.id " +
                "WHERE p.is_active = 1 AND (p.name LIKE ? OR p.sku LIKE ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + query + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    products.add(mapResultSetToProduct(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching products with query: {}", query, e);
        }
        return products;
    }

    public Optional<Product> findBySku(String sku) {
        String sql = "SELECT p.*, c.name as category_name, b.name as brand_name " +
                "FROM products p " +
                "LEFT JOIN categories c ON p.category_id = c.id " +
                "LEFT JOIN brands b ON p.brand_id = b.id " +
                "WHERE p.sku = ? AND p.is_active = 1";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sku);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProduct(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding product by SKU: {}", sku, e);
        }
        return Optional.empty();
    }

    public void save(Product product) {
        if (product.getId() == null) {
            insert(product);
        } else {
            update(product);
        }
    }

    private void insert(Product product) {
        ensureSellingPrice(product);

        String sql = "INSERT INTO products (sku, name, product_type_code, category_id, brand_id, hpp, margin_percent, selling_price, stock, description, image_path) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, product.getSku());
            pstmt.setString(2, product.getName());
            pstmt.setString(3, product.getProductType().name());
            pstmt.setObject(4, product.getCategoryId());
            pstmt.setObject(5, product.getBrandId());
            pstmt.setInt(6, product.getHpp().intValue());
            pstmt.setDouble(7, product.getMarginPercent());
            pstmt.setInt(8, product.getSellingPrice().intValue());
            pstmt.setInt(9, product.getStock());
            pstmt.setString(10, product.getDescription());
            pstmt.setString(11, product.getImagePath());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                // SQLite style: get last inserted ID
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT " + SqlDialect.lastInsertIdExpression())) {
                    if (rs.next()) {
                        product.setId(rs.getLong(1));
                    }
                }
            }
            logger.info("Product inserted: sku={}, id={}", product.getSku(), product.getId());
        } catch (SQLException e) {
            logger.error("Error inserting product", e);
            throw new RuntimeException("Gagal menyimpan produk baru");
        }
    }

    /**
     * Update ONLY image_path (for image upload, doesn't touch other fields)
     */
    public void updateImagePath(Long productId, String imagePath) {
        String sql = "UPDATE products SET image_path = ?, updated_at = " + SqlDialect.nowExpression() + " WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, imagePath);
            pstmt.setLong(2, productId);
            int rows = pstmt.executeUpdate();
            logger.info("Image path updated: productId={}, imagePath={}, rows={}", productId, imagePath, rows);
        } catch (SQLException e) {
            logger.error("Error updating image path", e);
            throw new RuntimeException("Gagal menyimpan foto produk");
        }
    }

    private void update(Product product) {
        ensureSellingPrice(product);

        String sql = "UPDATE products SET name = ?, category_id = ?, brand_id = ?, hpp = ?, margin_percent = ?, " +
                "selling_price = ?, stock = ?, description = ?, image_path = ?, product_type_code = ?, is_active = ?, "
                +
                "updated_at = " + SqlDialect.nowExpression() + " WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, product.getName());
            pstmt.setObject(2, product.getCategoryId());
            pstmt.setObject(3, product.getBrandId());
            pstmt.setInt(4, product.getHpp().intValue());
            pstmt.setDouble(5, product.getMarginPercent());
            pstmt.setInt(6, product.getSellingPrice().intValue());
            pstmt.setInt(7, product.getStock());
            pstmt.setString(8, product.getDescription());
            pstmt.setString(9, product.getImagePath());
            pstmt.setString(10, product.getProductType().name());
            pstmt.setBoolean(11, product.isActive());
            pstmt.setLong(12, product.getId());

            int rows = pstmt.executeUpdate();
            logger.info("[DB] Product updated: id={}, image_path={}, rows_affected={}",
                    product.getId(), product.getImagePath(), rows);
        } catch (SQLException e) {
            logger.error("Error updating product", e);
            throw new RuntimeException("Error updating product: " + e.getMessage());
        }
    }

    private void ensureSellingPrice(Product product) {
        if (product.getSellingPrice() != null && product.getSellingPrice().compareTo(BigDecimal.ZERO) > 0) {
            product.setSellingPrice(product.getSellingPrice().setScale(0, java.math.RoundingMode.HALF_UP));
            return;
        }

        BigDecimal hpp = product.getHpp();
        double margin = product.getMarginPercent();
        BigDecimal marginAmount = hpp.multiply(BigDecimal.valueOf(margin)).divide(BigDecimal.valueOf(100),
                java.math.RoundingMode.HALF_UP);
        BigDecimal sellingPrice = hpp.add(marginAmount).setScale(0, java.math.RoundingMode.HALF_UP);
        product.setSellingPrice(sellingPrice);
    }

    public void updateStock(Long productId, int quantityChange) {
        String sql = "UPDATE products SET stock = stock + ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, quantityChange);
            pstmt.setLong(2, productId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            logger.error("Error updating stock for product id: {}", productId, e);
            throw new RuntimeException("Error updating stock");
        }
    }

    private Product mapResultSetToProduct(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getLong("id"));
        p.setSku(rs.getString("sku"));
        p.setName(rs.getString("name"));
        p.setProductType(Product.ProductType.valueOf(rs.getString("product_type_code")));
        Object catIdObj = rs.getObject("category_id");
        if (catIdObj != null) {
            p.setCategoryId(((Number) catIdObj).longValue());
        }

        Object brandIdObj = rs.getObject("brand_id");
        if (brandIdObj != null) {
            p.setBrandId(((Number) brandIdObj).longValue());
        }

        p.setHpp(BigDecimal.valueOf(rs.getLong("hpp"))); // Use getLong for money/large ints just in case
        p.setMarginPercent(rs.getDouble("margin_percent"));
        p.setSellingPrice(BigDecimal.valueOf(rs.getLong("selling_price")));
        p.setStock(rs.getInt("stock"));
        p.setStock(rs.getInt("stock"));
        p.setDescription(rs.getString("description"));
        p.setImagePath(rs.getString("image_path"));
        p.setActive(rs.getInt("is_active") == 1);

        p.setCategoryName(rs.getString("category_name"));
        p.setBrandName(rs.getString("brand_name"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            p.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        String updatedAtStr = rs.getString("updated_at");
        if (updatedAtStr != null) {
            p.setUpdatedAt(LocalDateTime.parse(updatedAtStr, DB_DATE_FMT));
        }

        return p;
    }
}


