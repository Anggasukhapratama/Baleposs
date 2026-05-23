const fs = require("fs");
const path = require("path");
const bcrypt = require("bcryptjs");
const { query } = require("../api/_db");

const envPath = path.join(__dirname, "..", ".env");
if (fs.existsSync(envPath)) {
  const lines = fs.readFileSync(envPath, "utf8").split(/\r?\n/);
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const index = trimmed.indexOf("=");
    if (index < 1) continue;
    const key = trimmed.slice(0, index).trim();
    const value = trimmed.slice(index + 1).trim();
    if (!process.env[key]) process.env[key] = value;
  }
}

async function main() {
  const rows = await query(
    `SELECT id, username, password_hash, role, is_active
     FROM users
     WHERE username = $1 AND is_active = 1
     LIMIT 1`,
    ["adminkeuangan"]
  );

  const user = rows[0];
  if (!user) {
    throw new Error("User adminkeuangan tidak ditemukan atau tidak aktif.");
  }

  const passwordOk = await bcrypt.compare("keuangan123", user.password_hash);
  console.log(JSON.stringify({
    username: user.username,
    role: user.role,
    active: user.is_active,
    passwordOk,
  }));
}

main().catch((error) => {
  console.error(error.stack || error.message);
  process.exit(1);
});
