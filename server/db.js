const { DatabaseSync } = require("node:sqlite");
const { databasePath } = require("./runtime-paths");

const db = new DatabaseSync(databasePath);

db.exec(`CREATE TABLE IF NOT EXISTS inventory_items (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, manufacture_date TEXT NOT NULL,
  expiry_date TEXT NOT NULL, quantity INTEGER NOT NULL DEFAULT 1, photo_filename TEXT, created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);`);
if (!db.prepare("PRAGMA table_info(inventory_items)").all().some((column) => column.name === "quantity")) db.exec("ALTER TABLE inventory_items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1");

module.exports = { db };
