package com.baletpos.dao;

import com.baletpos.config.DatabaseConfig;
import com.baletpos.config.DatabaseDialect;
import com.baletpos.config.SqlDialect;
import com.baletpos.model.Expense;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object untuk Biaya Operasional
 */
public class ExpenseDAO {
    private static final Logger logger = LoggerFactory.getLogger(ExpenseDAO.class);
    private static final DateTimeFormatter EXP_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void create(Expense expense) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);

            // Generate expense number
            String expenseNumber = generateExpenseNumber(conn);
            expense.setExpenseNumber(expenseNumber);

            String sql = "INSERT INTO expenses (expense_number, expense_code_id, expense_date, amount, description, created_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, expense.getExpenseNumber());
                pstmt.setLong(2, expense.getExpenseCodeId());
                if (DatabaseConfig.getDialect() == DatabaseDialect.POSTGRES) {
                    pstmt.setDate(3, java.sql.Date.valueOf(expense.getExpenseDate()));
                } else {
                    pstmt.setString(3, expense.getExpenseDate().toString());
                }
                pstmt.setInt(4, expense.getAmount().intValue());
                pstmt.setString(5, expense.getDescription());
                pstmt.setLong(6, expense.getCreatedBy());

                pstmt.executeUpdate();

                // SQLite style: get last inserted ID
                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT " + SqlDialect.lastInsertIdExpression())) {
                    if (rs.next()) {
                        expense.setId(rs.getLong(1));
                    }
                }
            }

            conn.commit();
            logger.info("Expense created successfully: {}", expenseNumber);

        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            throw new SQLException("Error creating expense: " + e.getMessage(), e);
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

    public List<Expense> findAll() {
        List<Expense> expenses = new ArrayList<>();
        String sql = "SELECT e.*, ec.code as expense_code, ec.name as expense_code_name, u.full_name as created_by_name "
                + "FROM expenses e "
                + "LEFT JOIN expense_codes ec ON e.expense_code_id = ec.id "
                + "LEFT JOIN users u ON e.created_by = u.id "
                + "ORDER BY e.expense_date DESC, e.id DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                expenses.add(mapResultSetToExpense(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all expenses", e);
        }
        return expenses;
    }

    public List<Expense> findByDateRange(LocalDate startDate, LocalDate endDate) {
        List<Expense> expenses = new ArrayList<>();
        String sql = "SELECT e.*, ec.code as expense_code, ec.name as expense_code_name, u.full_name as created_by_name "
                + "FROM expenses e "
                + "LEFT JOIN expense_codes ec ON e.expense_code_id = ec.id "
                + "LEFT JOIN users u ON e.created_by = u.id "
                + "WHERE e.expense_date BETWEEN ? AND ? "
                + "ORDER BY e.expense_date DESC, e.id DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    expenses.add(mapResultSetToExpense(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding expenses by date range", e);
        }
        return expenses;
    }

    public List<Expense> findByDateRangeAndCode(LocalDate startDate, LocalDate endDate, Long expenseCodeId) {
        List<Expense> expenses = new ArrayList<>();
        String sql = "SELECT e.*, ec.code as expense_code, ec.name as expense_code_name, u.full_name as created_by_name "
                + "FROM expenses e "
                + "LEFT JOIN expense_codes ec ON e.expense_code_id = ec.id "
                + "LEFT JOIN users u ON e.created_by = u.id "
                + "WHERE e.expense_date BETWEEN ? AND ? AND e.expense_code_id = ? "
                + "ORDER BY e.expense_date DESC, e.id DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());
            pstmt.setLong(3, expenseCodeId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    expenses.add(mapResultSetToExpense(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding expenses by date range and code", e);
        }
        return expenses;
    }

    public BigDecimal getTotalByDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT COALESCE(SUM(amount), 0) as total FROM expenses WHERE expense_date BETWEEN ? AND ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, startDate.toString());
            pstmt.setString(2, endDate.toString());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return BigDecimal.valueOf(rs.getLong("total"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting total expenses by date range", e);
        }
        return BigDecimal.ZERO;
    }

    public Optional<Expense> findById(Long id) {
        String sql = "SELECT e.*, ec.code as expense_code, ec.name as expense_code_name, u.full_name as created_by_name "
                + "FROM expenses e "
                + "LEFT JOIN expense_codes ec ON e.expense_code_id = ec.id "
                + "LEFT JOIN users u ON e.created_by = u.id "
                + "WHERE e.id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToExpense(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding expense by id: {}", id, e);
        }
        return Optional.empty();
    }

    public void update(Expense expense) {
        String sql = "UPDATE expenses SET expense_code_id = ?, expense_date = ?, amount = ?, description = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, expense.getExpenseCodeId());
            if (DatabaseConfig.getDialect() == DatabaseDialect.POSTGRES) {
                pstmt.setDate(2, java.sql.Date.valueOf(expense.getExpenseDate()));
            } else {
                pstmt.setString(2, expense.getExpenseDate().toString());
            }
            pstmt.setInt(3, expense.getAmount().intValue());
            pstmt.setString(4, expense.getDescription());
            pstmt.setLong(5, expense.getId());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating expense", e);
            throw new RuntimeException("Error updating expense: " + e.getMessage());
        }
    }

    public void delete(Long id) {
        String sql = "DELETE FROM expenses WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error deleting expense", e);
            throw new RuntimeException("Error deleting expense: " + e.getMessage());
        }
    }

    private String generateExpenseNumber(Connection conn) throws SQLException {
        String datePart = LocalDate.now().format(EXP_DATE_FMT);
        String prefix = "EXP";

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

    private Expense mapResultSetToExpense(ResultSet rs) throws SQLException {
        Expense e = new Expense();
        e.setId(rs.getLong("id"));
        e.setExpenseNumber(rs.getString("expense_number"));
        e.setExpenseCodeId(rs.getLong("expense_code_id"));
        e.setExpenseCode(rs.getString("expense_code"));
        e.setExpenseCodeName(rs.getString("expense_code_name"));

        String dateStr = rs.getString("expense_date");
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                String cleanDate = dateStr.length() > 10 ? dateStr.substring(0, 10) : dateStr;
                e.setExpenseDate(LocalDate.parse(cleanDate));
            } catch (Exception ex) {
                logger.warn("Failed to parse expense_date: {}", dateStr);
            }
        }

        e.setAmount(BigDecimal.valueOf(rs.getLong("amount")));
        e.setDescription(rs.getString("description"));
        e.setCreatedBy(rs.getLong("created_by"));
        e.setCreatedByName(rs.getString("created_by_name"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null && !createdAtStr.isBlank()) {
            try {
                e.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
            } catch (Exception ex) {
                logger.warn("Failed to parse created_at: {}", createdAtStr);
            }
        }

        return e;
    }
}
