const fs = require('fs');
const { Pool } = require('pg');

const connectionString = process.env.DATABASE_URL || process.env.POSTGRES_URL;
if (!connectionString) {
  throw new Error('Set DATABASE_URL before running init-schema.js');
}

const pool = new Pool({
  connectionString,
  ssl: connectionString.includes('localhost') ? false : { rejectUnauthorized: false },
});

async function run() {
  const schema = fs.readFileSync('../src/main/resources/sql/schema_postgres.sql', 'utf8');
  try {
    await pool.query(schema);
    console.log('Schema created successfully!');
  } catch (err) {
    console.error('Error creating schema:', err.message);
  } finally {
    pool.end();
  }
}
run();
