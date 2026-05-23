const { execFileSync } = require("node:child_process");
const path = require("node:path");
const os = require("node:os");
const bcrypt = require("bcryptjs");
const { Pool } = require("pg");

const sqliteDb = process.env.SQLITE_DB_PATH
  || path.join(os.homedir(), ".baletpos", "baletpos.db");
const databaseUrl = process.env.DATABASE_URL;

const tables = [
  { name: "users", columns: ["id", "username", "password_hash", "full_name", "role", "is_active", "created_at", "updated_at"] },
  { name: "categories", columns: ["id", "code", "name", "description", "is_active", "created_at"] },
  { name: "brands", columns: ["id", "code", "name", "is_active", "created_at"] },
  { name: "suppliers", columns: ["id", "code", "name", "contact", "address", "phone", "email", "is_active", "created_at"] },
  { name: "customers", columns: ["id", "name", "phone", "address", "email", "notes", "is_active", "created_at"] },
  { name: "products", columns: ["id", "sku", "name", "product_type_code", "category_id", "brand_id", "hpp", "margin_percent", "selling_price", "stock", "description", "image_path", "is_active", "created_at", "updated_at"] },
  { name: "purchases", columns: ["id", "purchase_number", "supplier_id", "purchase_date", "total_amount", "notes", "status", "created_by", "created_at"] },
  { name: "purchase_items", columns: ["id", "purchase_id", "product_id", "quantity", "hpp_per_unit", "subtotal"] },
  { name: "sales", columns: ["id", "invoice_number", "customer_id", "sale_date", "subtotal", "discount_percent", "discount_amount", "total_amount", "total_hpp", "payment_method", "payment_type", "payment_amount", "paid_amount", "change_amount", "status", "void_reason", "voided_by", "voided_at", "technician_id", "notes", "created_by", "created_at"] },
  { name: "sale_payments", columns: ["id", "sale_id", "method", "amount", "ref_no", "created_at"] },
  { name: "sale_items", columns: ["id", "sale_id", "product_id", "quantity", "unit_price", "hpp_per_unit", "discount_percent", "discount_amount", "subtotal", "serial_number", "buyer_name", "buyer_nik"] },
  { name: "stock_movements", columns: ["id", "product_id", "movement_type", "reference_type", "reference_id", "quantity_change", "stock_before", "stock_after", "notes", "created_by", "created_at"] },
  { name: "opening_stocks", columns: ["id", "product_id", "period_yyyymm", "opening_qty", "created_by", "created_at"] },
  { name: "expense_codes", columns: ["id", "code", "name", "description", "is_active", "created_at"] },
  { name: "expenses", columns: ["id", "expense_number", "expense_code_id", "expense_date", "amount", "description", "created_by", "created_at"] },
  { name: "sales_returns", columns: ["id", "return_number", "sale_id", "return_date", "total_amount", "reason", "status", "created_by", "created_at"] },
  { name: "sales_return_items", columns: ["id", "sales_return_id", "sale_item_id", "product_id", "quantity", "unit_price", "hpp_per_unit", "subtotal"] },
  { name: "purchase_returns", columns: ["id", "return_number", "purchase_id", "return_date", "total_amount", "reason", "status", "created_by", "created_at"] },
  { name: "purchase_return_items", columns: ["id", "purchase_return_id", "purchase_item_id", "product_id", "quantity", "hpp_per_unit", "subtotal"] },
  { name: "stock_adjustments", columns: ["id", "adjustment_number", "product_id", "adjustment_date", "quantity_change", "reason", "notes", "created_by", "created_at"] },
  { name: "audit_logs", columns: ["id", "user_id", "action", "table_name", "record_id", "old_values", "new_values", "ip_address", "created_at"] },
  { name: "settings", columns: ["id", "setting_key", "setting_value", "description", "updated_at"] },
  { name: "invoice_sequences", columns: ["id", "prefix", "date_part", "last_number"] },
];

function requireEnv() {
  if (!databaseUrl) {
    throw new Error("DATABASE_URL belum diset.");
  }
  if (process.env.CONFIRM_MIGRATE !== "YES") {
    throw new Error("Set CONFIRM_MIGRATE=YES untuk migrasi remote Supabase.");
  }
}

function sqliteJson(sql) {
  const output = execFileSync("sqlite3", ["-json", sqliteDb, sql], {
    encoding: "utf8",
    maxBuffer: 1024 * 1024 * 100,
  }).trim();
  return output ? JSON.parse(output) : [];
}

function sqliteTables() {
  const rows = sqliteJson("SELECT name FROM sqlite_master WHERE type='table'");
  return new Set(rows.map((row) => row.name));
}

function normalizeValue(table, column, value) {
  if (value === undefined) return null;
  if (table === "users" && column === "role" && value === "ADMIN") return "ADMIN_TOKO";
  if (table === "users" && column === "password_hash" && typeof value === "string" && !value.startsWith("$2")) {
    return bcrypt.hashSync(value, 12);
  }
  return value;
}

async function truncateAll(client) {
  const names = tables.map((table) => `"${table.name}"`).join(", ");
  await client.query(`TRUNCATE ${names} RESTART IDENTITY CASCADE`);
}

async function insertRows(client, table, rows) {
  if (rows.length === 0) return;

  const columns = table.columns;
  const columnSql = columns.map((column) => `"${column}"`).join(", ");
  const batchSize = 200;

  for (let offset = 0; offset < rows.length; offset += batchSize) {
    const batch = rows.slice(offset, offset + batchSize);
    const values = [];
    const placeholders = batch.map((row, rowIndex) => {
      const cells = columns.map((column, columnIndex) => {
        values.push(normalizeValue(table.name, column, row[column]));
        return `$${rowIndex * columns.length + columnIndex + 1}`;
      });
      return `(${cells.join(", ")})`;
    });

    await client.query(
      `INSERT INTO "${table.name}" (${columnSql}) VALUES ${placeholders.join(", ")}`,
      values
    );
  }
}

async function resetSequence(client, table) {
  if (!table.columns.includes("id")) return;
  await client.query(
    `SELECT setval(pg_get_serial_sequence($1, 'id'), COALESCE((SELECT MAX(id) FROM "${table.name}"), 1), true)`,
    [table.name]
  );
}

async function main() {
  requireEnv();
  const existingSqliteTables = sqliteTables();
  const pool = new Pool({
    connectionString: databaseUrl,
    ssl: databaseUrl.includes("localhost") ? false : { rejectUnauthorized: false },
  });
  const client = await pool.connect();

  try {
    console.log(`Migrating ${sqliteDb}`);
    await client.query("BEGIN");
    await truncateAll(client);

    for (const table of tables) {
      if (!existingSqliteTables.has(table.name)) {
        console.log(`skip ${table.name}: not in SQLite`);
        continue;
      }
      
      const pragma = sqliteJson(`PRAGMA table_info("${table.name}")`);
      const existingCols = new Set(pragma.map(c => c.name));
      const validColumns = table.columns.filter(c => existingCols.has(c));
      
      const rows = sqliteJson(`SELECT ${validColumns.map((column) => `"${column}"`).join(", ")} FROM "${table.name}"`);
      await insertRows(client, table, rows);
      console.log(`${table.name}: ${rows.length} rows`);
    }

    for (const table of tables) {
      await resetSequence(client, table);
    }

    await client.query("COMMIT");
    console.log("Migration completed.");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
    await pool.end();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
