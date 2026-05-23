const { query } = require("../api/_db");
const fs = require("fs");
const path = require("path");

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
  const salesDefaults = await query(
    `SELECT column_name, column_default, data_type
     FROM information_schema.columns
     WHERE table_name = 'sales'
     ORDER BY ordinal_position`
  );
  console.log("SALES DEFAULTS:", JSON.stringify(salesDefaults, null, 2));
}

main().catch(console.error);
