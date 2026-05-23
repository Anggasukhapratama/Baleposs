const fs = require("fs");
const path = require("path");
const { query } = require("../api/_db");

const envPath = path.join(__dirname, "..", ".env");
if (fs.existsSync(envPath)) {
  for (const line of fs.readFileSync(envPath, "utf8").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const index = trimmed.indexOf("=");
    if (index < 1) continue;
    process.env[trimmed.slice(0, index).trim()] = trimmed.slice(index + 1).trim();
  }
}

async function main() {
  const columns = await query(
    `SELECT column_name
     FROM information_schema.columns
     WHERE table_name = 'sale_items'
       AND column_name IN ('bonus_product_id', 'bonus_product_name', 'warranty_label')
     ORDER BY column_name`
  );

  const counts = await query(
    `SELECT
       (SELECT COUNT(*) FROM products)::int AS products,
       (SELECT COUNT(*) FROM users WHERE is_active = 1)::int AS active_users,
       (SELECT COUNT(*) FROM users
        WHERE username IN ('kasir', 'admintoko', 'adminkeuangan')
          AND is_active = 1)::int AS expected_users,
       (SELECT COUNT(*) FROM sales)::int AS sales`
  );

  const users = await query(
    `SELECT username, role, is_active
     FROM users
     ORDER BY is_active DESC, username ASC`
  );

  console.log(JSON.stringify({
    saleItemColumns: columns.map((row) => row.column_name),
    counts: counts[0],
    users,
  }, null, 2));
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
