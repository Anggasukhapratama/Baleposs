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
  await query(
    `UPDATE users
     SET is_active = CASE
       WHEN username IN ('kasir', 'admintoko', 'adminkeuangan') THEN 1
       ELSE 0
     END`
  );

  const rows = await query(
    `SELECT id, username, full_name, role, is_active
     FROM users
     ORDER BY is_active DESC, username ASC`
  );
  console.table(rows);
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
