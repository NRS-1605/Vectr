const { db } = require("./economy");

db.exec(`CREATE TABLE IF NOT EXISTS clipboard_history (
  id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, source TEXT NOT NULL CHECK(source IN ('laptop','phone')), timestamp TEXT NOT NULL
)`);

function saveEntry(text, source) {
  db.prepare("INSERT INTO clipboard_history (text, source, timestamp) VALUES (?, ?, ?)").run(text, source, new Date().toISOString());
  const count = Number(db.prepare("SELECT COUNT(*) AS count FROM clipboard_history").get().count);
  if (count > 200) {
    db.prepare("DELETE FROM clipboard_history WHERE id <= (SELECT id FROM clipboard_history ORDER BY id DESC LIMIT 1 OFFSET 150)").run();
  }
}

function getHistory(limit = 50) {
  return db.prepare("SELECT id, text, source, timestamp FROM clipboard_history ORDER BY id DESC LIMIT ?").all(limit);
}

function clearHistory() {
  db.exec("DELETE FROM clipboard_history");
}

module.exports = { saveEntry, getHistory, clearHistory };
