package com.baletpos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    private static final String DB_NAME = "baletpos.db";
    private static final String DB_PATH = System.getProperty("user.home") + "/.baletpos/" + DB_NAME;
    private static final String SQLITE_DB_URL = "jdbc:sqlite:" + DB_PATH;
    private static final String DB_URL = getConfiguredDbUrl();
    private static final String DB_USER = getConfigValue("baletpos.db.user", "BALETPOS_DB_USER");
    private static final String DB_PASSWORD = getConfigValue("baletpos.db.password", "BALETPOS_DB_PASSWORD");
    private static final DatabaseDialect DIALECT = DB_URL.startsWith("jdbc:postgresql:")
            ? DatabaseDialect.POSTGRES
            : DatabaseDialect.SQLITE;

    public static void initialize() {
        try {
            if (DIALECT == DatabaseDialect.SQLITE) {
                // Ensure directory exists
                Path dbDir = Paths.get(System.getProperty("user.home"), ".baletpos");
                if (!Files.exists(dbDir)) {
                    Files.createDirectories(dbDir);
                    logger.info("Created database directory: {}", dbDir);
                }
            }

            loadJdbcDriver();

            // Initialize Tables if users table missing
            boolean dbInitialized = false;
            try (Connection conn = getConnection()) {

                if (!tableExists(conn, "users")) {
                    logger.info("Database tables missing. Initializing schema and seed data...");
                    executeScript(conn, DIALECT == DatabaseDialect.POSTGRES
                            ? "/sql/schema_postgres.sql"
                            : "/sql/schema.sql");
                    executeScript(conn, "/sql/seed.sql");

                    ensureTrialUsers(conn);

                    dbInitialized = true;
                } else {
                    logger.info("Database integrity check passed.");
                    if (DIALECT == DatabaseDialect.SQLITE) {
                        // Run migrations for existing SQLite database
                        runMigrations(conn);
                    } else if (DIALECT == DatabaseDialect.POSTGRES) {
                        migratePostgresUserRoles(conn);
                        migratePostgresSaleItemFields(conn);
                    }
                }
            }

            if (dbInitialized) {
                logger.info("Database initialized successfully.");
            }

            // Ensure default trial users exist with correct roles/passwords
            try (Connection conn = getConnection()) {
                ensureTrialUsers(conn);
            } catch (Exception e) {
                logger.error("Seed default users failed", e);
            }

            if (DIALECT == DatabaseDialect.SQLITE && isDemoDataEnabled()) {
                // Check if we need to seed DEMO DATA (if no sales exist)
                try (Connection conn = getConnection()) {
                    boolean hasSales = false;
                    try (ResultSet rs = conn.getMetaData().getTables(null, null, "sales", null)) {
                        if (rs.next()) {
                            try (Statement st = conn.createStatement();
                                    ResultSet r = st.executeQuery("SELECT count(*) FROM sales")) {
                                if (r.next() && r.getInt(1) > 0)
                                    hasSales = true;
                            }
                        }
                    }

                    if (!hasSales) {
                        logger.info("No sales found. Running DEMO DATA SEEDER...");
                        com.baletpos.util.DemoDataSeeder.seed(conn);
                    }
                } catch (Exception e) {
                    logger.error("Demo Data Seeding failed", e);
                }
            } else if (DIALECT == DatabaseDialect.SQLITE) {
                logger.info("Demo data seeding disabled.");
            }

        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Run database migrations for existing databases
     */
    private static void runMigrations(Connection conn) {
        migrateUserRoles(conn);

        // Migration 1: Add image_path column to products
        try {
            boolean hasImagePath = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "products", "image_path")) {
                hasImagePath = rs.next();
            }

            if (!hasImagePath) {
                logger.info("Running migration: Add image_path column to products");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE products ADD COLUMN image_path TEXT");
                }
                logger.info("Migration completed: image_path column added");
            }
        } catch (SQLException e) {
            logger.warn("Migration check failed: {}", e.getMessage());
        }

        // Migration 2: Add technician_id column to sales
        try {
            boolean hasTechId = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "sales", "technician_id")) {
                hasTechId = rs.next();
            }

            if (!hasTechId) {
                logger.info("Running migration: Add technician_id column to sales");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE sales ADD COLUMN technician_id INTEGER");
                }
                logger.info("Migration completed: technician_id column added");
            }
        } catch (SQLException e) {
            logger.warn("Migration 2 failed: {}", e.getMessage());
        }

        // Migration 3: Split Payments
        try {
            boolean hasPaymentType = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "sales", "payment_type")) {
                hasPaymentType = rs.next();
            }
            if (!hasPaymentType) {
                logger.info("Running migration 3: Split Payments Support");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE sales ADD COLUMN payment_type TEXT DEFAULT 'SINGLE'");
                    // Ensure sale_payments table
                    stmt.execute("CREATE TABLE IF NOT EXISTS sale_payments (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "sale_id INTEGER NOT NULL, " +
                            "method TEXT NOT NULL, " +
                            "amount INTEGER NOT NULL, " +
                            "ref_no TEXT, " +
                            "created_at TEXT DEFAULT (datetime('now', 'localtime')), " +
                            "FOREIGN KEY(sale_id) REFERENCES sales(id))");

                    // Add other columns if missing
                    try {
                        stmt.execute("ALTER TABLE sales ADD COLUMN paid_amount INTEGER DEFAULT 0");
                    } catch (Exception ignored) {
                    }
                    try {
                        stmt.execute("ALTER TABLE sales ADD COLUMN change_amount INTEGER DEFAULT 0");
                    } catch (Exception ignored) {
                    }
                }
                logger.info("Migration 3 completed");
            } else {
                // Check if sale_payments exists even if payment_type exists (partial migration
                // fix)
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE TABLE IF NOT EXISTS sale_payments (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "sale_id INTEGER NOT NULL, " +
                            "method TEXT NOT NULL, " +
                            "amount INTEGER NOT NULL, " +
                            "ref_no TEXT, " +
                            "created_at TEXT DEFAULT (datetime('now', 'localtime')), " +
                            "FOREIGN KEY(sale_id) REFERENCES sales(id))");
                }
            }
        } catch (SQLException e) {
            logger.warn("Migration 3 failed: {}", e.getMessage());
        }

        // Migration 4: Fix Expenses Schema (Add expense_number if missing)
        try {
            boolean hasExpenseNumber = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "expenses", "expense_number")) {
                hasExpenseNumber = rs.next();
            }

            if (!hasExpenseNumber) {
                logger.info("Running migration 4: Add expense_number to expenses");
                try (Statement stmt = conn.createStatement()) {
                    // 1. Add column (nullable first)
                    stmt.execute("ALTER TABLE expenses ADD COLUMN expense_number TEXT");

                    // 2. Populate existing rows
                    stmt.execute(
                            "UPDATE expenses SET expense_number = 'EXP-' || date(expense_date) || '-' || substr('000' || id, -4, 4) WHERE expense_number IS NULL");

                    // Note: We cannot easily add UNIQUE constraint via ALTER TABLE in SQLite
                    // without recreation.
                    // But strictly speaking for existing data it's fine as long as code handles it.
                    // New inserts will likely respect application logic.
                }
                logger.info("Migration 4 completed");
            }
        } catch (SQLException e) {
            logger.warn("Migration 4 failed: {}", e.getMessage());
        }

        // Migration 5: Add SN and Buyer Info to sale_items
        try {
            boolean hasSN = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "sale_items", "serial_number")) {
                hasSN = rs.next();
            }

            if (!hasSN) {
                logger.info("Running migration 5: Add Laptop Buyer column to sale_items");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE sale_items ADD COLUMN serial_number TEXT");
                    stmt.execute("ALTER TABLE sale_items ADD COLUMN buyer_name TEXT");
                    stmt.execute("ALTER TABLE sale_items ADD COLUMN buyer_nik TEXT");
                    stmt.execute("ALTER TABLE sale_items ADD COLUMN bonus_product_id INTEGER");
                    stmt.execute("ALTER TABLE sale_items ADD COLUMN bonus_product_name TEXT");
                    stmt.execute("ALTER TABLE sale_items ADD COLUMN warranty_label TEXT");
                }
                logger.info("Migration 5 completed");
            }
        } catch (SQLException e) {
            logger.warn("Migration 5 failed: {}", e.getMessage());
        }

        // Migration 7: Add laptop bonus and warranty fields to existing sale_items.
        addColumnIfMissing(conn, "sale_items", "bonus_product_id", "INTEGER");
        addColumnIfMissing(conn, "sale_items", "bonus_product_name", "TEXT");
        addColumnIfMissing(conn, "sale_items", "warranty_label", "TEXT");

        // Migration 6: Fix sales table constraint (Remove restrictive CHECK)
        try {
            boolean needFix = false;
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt
                            .executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='sales'")) {
                if (rs.next()) {
                    String sql = rs.getString(1);
                    if (sql != null && sql.contains("CHECK (payment_method IN")) {
                        needFix = true;
                    }
                }
            }

            if (needFix) {
                logger.info("Running migration 6: Fix sales table payment_method constraint");
                try (Statement stmt = conn.createStatement()) {
                    conn.setAutoCommit(false);

                    // 1. Rename existing table
                    stmt.execute("ALTER TABLE sales RENAME TO sales_old_mig");

                    // 2. Create new table (Updated Schema)
                    String createNew = "CREATE TABLE sales (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            "invoice_number TEXT NOT NULL UNIQUE, " +
                            "customer_id INTEGER, " +
                            "sale_date TEXT NOT NULL, " +
                            "subtotal INTEGER NOT NULL DEFAULT 0, " +
                            "discount_percent REAL NOT NULL DEFAULT 0, " +
                            "discount_amount INTEGER NOT NULL DEFAULT 0, " +
                            "total_amount INTEGER NOT NULL DEFAULT 0, " +
                            "total_hpp INTEGER NOT NULL DEFAULT 0, " +
                            "payment_method TEXT NOT NULL, " + // Constraint removed
                            "payment_amount INTEGER NOT NULL DEFAULT 0, " +
                            "change_amount INTEGER NOT NULL DEFAULT 0, " +
                            "status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'VOIDED', 'RETURNED')), "
                            +
                            "void_reason TEXT, " +
                            "voided_by INTEGER, " +
                            "voided_at TEXT, " +
                            "technician_id INTEGER, " +
                            "notes TEXT, " +
                            "created_by INTEGER NOT NULL, " +
                            "created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')), " +
                            "payment_type TEXT DEFAULT 'SINGLE', " +
                            "paid_amount INTEGER DEFAULT 0, " +
                            "FOREIGN KEY (customer_id) REFERENCES customers(id), " +
                            "FOREIGN KEY (created_by) REFERENCES users(id), " +
                            "FOREIGN KEY (voided_by) REFERENCES users(id), " +
                            "FOREIGN KEY (technician_id) REFERENCES users(id) " +
                            ")";
                    stmt.execute(createNew);

                    // 3. Restore Data
                    // Note: We copy explicitly to ensure mapping is correct even if column order
                    // differed
                    String insertSql = "INSERT INTO sales (" +
                            "id, invoice_number, customer_id, sale_date, subtotal, discount_percent, " +
                            "discount_amount, total_amount, total_hpp, payment_method, payment_amount, " +
                            "change_amount, status, void_reason, voided_by, voided_at, technician_id, " +
                            "notes, created_by, created_at, payment_type, paid_amount) " +
                            "SELECT " +
                            "id, invoice_number, customer_id, sale_date, subtotal, discount_percent, " +
                            "discount_amount, total_amount, total_hpp, payment_method, payment_amount, " +
                            "change_amount, status, void_reason, voided_by, voided_at, technician_id, " +
                            "notes, created_by, created_at, payment_type, paid_amount " +
                            "FROM sales_old_mig";
                    stmt.execute(insertSql);

                    // 4. Drop old table
                    stmt.execute("DROP TABLE sales_old_mig");

                    // 5. Re-create Indexes
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_invoice ON sales(invoice_number)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(sale_date)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status)");

                    conn.commit();
                    logger.info("Migration 6 completed successfully");
                } catch (Exception e) {
                    conn.rollback();
                    logger.error("Migration 6 transaction failed", e);
                    throw e; // Propagate to log failure
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            logger.warn("Migration 6 failed: {}", e.getMessage());
        }

        // Migration 7: Ensure product_type_code + product_type compatibility
        try {
            boolean hasTypeCode = false;
            boolean hasType = false;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "products", "product_type_code")) {
                hasTypeCode = rs.next();
            }
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "products", "product_type")) {
                hasType = rs.next();
            }

            if (!hasTypeCode) {
                logger.info("Running migration 7: Add product_type_code to products");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE products ADD COLUMN product_type_code TEXT");
                    if (hasType) {
                        stmt.execute("UPDATE products SET product_type_code = product_type");
                    } else {
                        stmt.execute("UPDATE products SET product_type_code = 'PERIPHERAL'");
                    }
                }
            }

            if (!hasType) {
                logger.info("Running migration 7b: Add product_type to products (compat)");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE products ADD COLUMN product_type TEXT");
                    stmt.execute("UPDATE products SET product_type = product_type_code");
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_products_type ON products(product_type_code)");
            }
        } catch (SQLException e) {
            logger.warn("Migration 7 failed: {}", e.getMessage());
        }

        // Migration 8: Ensure sales return tables exist and columns match schema
        try {
            boolean hasSalesReturns = false;
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "sales_returns", null)) {
                hasSalesReturns = rs.next();
            }
            if (!hasSalesReturns) {
                logger.info("Running migration 8: Create sales_returns tables");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS sales_returns (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                return_number TEXT NOT NULL UNIQUE,
                                sale_id INTEGER NOT NULL,
                                return_date TEXT NOT NULL,
                                total_amount INTEGER NOT NULL DEFAULT 0,
                                reason TEXT,
                                status TEXT NOT NULL DEFAULT 'COMPLETED',
                                created_by INTEGER NOT NULL,
                                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                                FOREIGN KEY (sale_id) REFERENCES sales(id),
                                FOREIGN KEY (created_by) REFERENCES users(id)
                            )
                            """);
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS sales_return_items (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                sales_return_id INTEGER NOT NULL,
                                sale_item_id INTEGER NOT NULL,
                                product_id INTEGER NOT NULL,
                                quantity INTEGER NOT NULL,
                                unit_price INTEGER NOT NULL,
                                hpp_per_unit INTEGER NOT NULL,
                                subtotal INTEGER NOT NULL,
                                FOREIGN KEY (sales_return_id) REFERENCES sales_returns(id),
                                FOREIGN KEY (sale_item_id) REFERENCES sale_items(id),
                                FOREIGN KEY (product_id) REFERENCES products(id)
                            )
                            """);
                }
            } else {
                try (ResultSet rs = conn.getMetaData().getColumns(null, null, "sales_return_items", "sale_item_id")) {
                    if (!rs.next()) {
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute("ALTER TABLE sales_return_items ADD COLUMN sale_item_id INTEGER");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Migration 8 failed: {}", e.getMessage());
        }

        // Migration 9: Ensure stock_adjustments table matches schema
        try {
            boolean hasStockAdjustments = false;
            try (ResultSet rs = conn.getMetaData().getTables(null, null, "stock_adjustments", null)) {
                hasStockAdjustments = rs.next();
            }
            if (!hasStockAdjustments) {
                logger.info("Running migration 9: Create stock_adjustments table");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS stock_adjustments (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                adjustment_number TEXT NOT NULL UNIQUE,
                                product_id INTEGER NOT NULL,
                                adjustment_date TEXT NOT NULL,
                                quantity_change INTEGER NOT NULL,
                                reason TEXT NOT NULL CHECK (reason IN ('RUSAK', 'HILANG', 'OPNAME', 'LAINNYA')),
                                notes TEXT,
                                created_by INTEGER NOT NULL,
                                created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                                FOREIGN KEY (product_id) REFERENCES products(id),
                                FOREIGN KEY (created_by) REFERENCES users(id)
                            )
                            """);
                }
            } else {
                // Add missing columns if needed
                try (Statement stmt = conn.createStatement()) {
                    try {
                        stmt.execute("ALTER TABLE stock_adjustments ADD COLUMN adjustment_number TEXT");
                    } catch (Exception ignored) {
                    }
                    try {
                        stmt.execute("ALTER TABLE stock_adjustments ADD COLUMN product_id INTEGER");
                    } catch (Exception ignored) {
                    }
                    try {
                        stmt.execute("ALTER TABLE stock_adjustments ADD COLUMN adjustment_date TEXT");
                    } catch (Exception ignored) {
                    }
                    try {
                        stmt.execute("ALTER TABLE stock_adjustments ADD COLUMN quantity_change INTEGER");
                    } catch (Exception ignored) {
                    }
                    try {
                        stmt.execute("ALTER TABLE stock_adjustments ADD COLUMN notes TEXT");
                    } catch (Exception ignored) {
                    }
                    try {
                        stmt.execute("ALTER TABLE stock_adjustments ADD COLUMN created_by INTEGER");
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Migration 9 failed: {}", e.getMessage());
        }
    }

    private static void migrateUserRoles(Connection conn) {
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='users'")) {
            if (!rs.next()) {
                return;
            }

            String sql = rs.getString(1);
            boolean hasOldConstraint = sql != null && sql.contains("role IN ('ADMIN', 'KASIR')");
            if (!hasOldConstraint) {
                try (Statement update = conn.createStatement()) {
                    update.execute("UPDATE users SET role = 'ADMIN_TOKO' WHERE role = 'ADMIN'");
                }
                return;
            }

            logger.info("Running migration: Replace legacy user role constraint");
            conn.setAutoCommit(false);
            try (Statement migrate = conn.createStatement()) {
                migrate.execute("PRAGMA foreign_keys=OFF");
                migrate.execute("PRAGMA legacy_alter_table=ON");
                migrate.execute("ALTER TABLE users RENAME TO users_old_role_mig");
                migrate.execute("""
                        CREATE TABLE users (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            username TEXT NOT NULL UNIQUE,
                            password_hash TEXT NOT NULL,
                            full_name TEXT NOT NULL,
                            role TEXT NOT NULL CHECK (role IN ('KASIR', 'ADMIN_TOKO', 'ADMIN_KEUANGAN')),
                            is_active INTEGER NOT NULL DEFAULT 1,
                            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
                            updated_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
                        )
                        """);
                migrate.execute("""
                        INSERT INTO users (id, username, password_hash, full_name, role, is_active, created_at, updated_at)
                        SELECT id, username, password_hash, full_name,
                            CASE WHEN role = 'ADMIN' THEN 'ADMIN_TOKO' ELSE role END,
                            is_active, created_at, updated_at
                        FROM users_old_role_mig
                        """);
                migrate.execute("DROP TABLE users_old_role_mig");
                migrate.execute("PRAGMA legacy_alter_table=OFF");
                migrate.execute("PRAGMA foreign_keys=ON");
                conn.commit();
                logger.info("User role constraint migration completed");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            logger.warn("User role migration failed: {}", e.getMessage());
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    private static void migratePostgresUserRoles(Connection conn) {
        try {
            try (PreparedStatement findConstraint = conn.prepareStatement("""
                    SELECT conname
                    FROM pg_constraint
                    WHERE conrelid = 'users'::regclass
                      AND contype = 'c'
                      AND pg_get_constraintdef(oid) LIKE '%ADMIN%'
                    """);
                    ResultSet rs = findConstraint.executeQuery()) {
                while (rs.next()) {
                    String constraintName = rs.getString("conname");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS " + constraintName);
                    }
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("UPDATE users SET role = 'ADMIN_TOKO' WHERE role = 'ADMIN'");
                stmt.execute("""
                        ALTER TABLE users
                        ADD CONSTRAINT users_role_check
                        CHECK (role IN ('KASIR', 'ADMIN_TOKO', 'ADMIN_KEUANGAN'))
                        """);
            }
            logger.info("PostgreSQL user role migration completed");
        } catch (SQLException e) {
            logger.warn("PostgreSQL user role migration skipped/failed: {}", e.getMessage());
        }
    }

    private static void migratePostgresSaleItemFields(Connection conn) {
        addColumnIfMissing(conn, "sale_items", "bonus_product_id", "INTEGER REFERENCES products(id)");
        addColumnIfMissing(conn, "sale_items", "bonus_product_name", "TEXT");
        addColumnIfMissing(conn, "sale_items", "warranty_label", "TEXT");
    }

    private static void addColumnIfMissing(Connection conn, String tableName, String columnName, String definition) {
        try {
            if (columnExists(conn, tableName, columnName)) {
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            }
            logger.info("Added column {}.{}", tableName, columnName);
        } catch (SQLException e) {
            logger.warn("Add column {}.{} skipped/failed: {}", tableName, columnName, e.getMessage());
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                return true;
            }
        }

        if (DIALECT == DatabaseDialect.SQLITE) {
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
                while (rs.next()) {
                    if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void ensureTrialUsers(Connection conn) throws SQLException {
        ensureTrialUser(conn, "admintoko", "toko123", "Admin Toko", "ADMIN_TOKO");
        ensureTrialUser(conn, "adminkeuangan", "keuangan123", "Admin Keuangan", "ADMIN_KEUANGAN");
        ensureTrialUser(conn, "kasir", "kasir123", "Kasir Toko", "KASIR");
        deactivateLegacyTrialUsers(conn);
    }

    private static void ensureTrialUser(Connection conn, String username, String password, String fullName, String role)
            throws SQLException {
        String hash = com.baletpos.util.PasswordUtil.hashPassword(password);
        try (PreparedStatement check = conn.prepareStatement("SELECT count(*) FROM users WHERE username = ?")) {
            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                boolean exists = rs.next() && rs.getInt(1) > 0;
                if (exists) {
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE users SET password_hash = ?, full_name = ?, role = ?, is_active = 1 WHERE username = ?")) {
                        update.setString(1, hash);
                        update.setString(2, fullName);
                        update.setString(3, role);
                        update.setString(4, username);
                        update.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT INTO users (username, password_hash, full_name, role, is_active) VALUES (?, ?, ?, ?, 1)")) {
                        insert.setString(1, username);
                        insert.setString(2, hash);
                        insert.setString(3, fullName);
                        insert.setString(4, role);
                        insert.executeUpdate();
                    }
                }
            }
        }
        logger.info("Trial user ensured: {} ({})", username, role);
    }

    private static void deactivateLegacyTrialUsers(Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET is_active = 0 WHERE username IN (?, ?, ?)")) {
            stmt.setString(1, "admin");
            stmt.setString(2, "kasir2");
            stmt.setString(3, "teknisi");
            stmt.executeUpdate();
        }
        logger.info("Legacy trial users deactivated: admin, kasir2, teknisi");
    }

    public static Connection getConnection() throws SQLException {
        if (DIALECT == DatabaseDialect.POSTGRES && DB_USER != null && !DB_USER.isBlank()) {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD == null ? "" : DB_PASSWORD);
        }
        return DriverManager.getConnection(DB_URL);
    }

    public static DatabaseDialect getDialect() {
        return DIALECT;
    }

    private static String getConfiguredDbUrl() {
        // Prefer explicit BaletPOS var
        String configured = getConfigValue("baletpos.db.url", "BALETPOS_DB_URL");

        // Backward/compat: some deployments use DATABASE_URL
        if (configured == null || configured.isBlank()) {
            configured = getConfigValue("DATABASE_URL", "DATABASE_URL");
        }

        return configured == null || configured.isBlank() ? SQLITE_DB_URL : configured.trim();
    }


    private static String getConfigValue(String propertyName, String envName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return LocalAppConfig.get(propertyName);
    }

    private static boolean isDemoDataEnabled() {
        String configured = getConfigValue("baletpos.seed.demo", "BALETPOS_SEED_DEMO");
        return configured != null && configured.equalsIgnoreCase("true");
    }

    private static void loadJdbcDriver() throws ClassNotFoundException {
        if (DIALECT == DatabaseDialect.POSTGRES) {
            Class.forName("org.postgresql.Driver");
        } else {
            Class.forName("org.sqlite.JDBC");
        }
        logger.info("Using {} database", DIALECT);
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String schema = DIALECT == DatabaseDialect.POSTGRES ? "public" : null;
        try (ResultSet rs = conn.getMetaData().getTables(null, schema, tableName, new String[] { "TABLE" })) {
            return rs.next();
        }
    }

    private static void executeScript(Connection conn, String scriptPath) throws SQLException {
        try (InputStream is = DatabaseConfig.class.getResourceAsStream(scriptPath)) {
            if (is == null) {
                throw new RuntimeException("Script file not found: " + scriptPath);
            }

            String sql;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                sql = reader.lines().collect(Collectors.joining("\n"));
            }

            String[] statements = sql.split(";");

            try (Statement stmt = conn.createStatement()) {
                for (String statement : statements) {
                    if (!statement.trim().isEmpty()) {
                        stmt.execute(statement.trim());
                    }
                }
            }
            logger.info("Executed script: {}", scriptPath);
        } catch (Exception e) {
            logger.error("Error executing script: {}", scriptPath, e);
            throw new SQLException("Error executing script: " + scriptPath, e);
        }
    }
}


