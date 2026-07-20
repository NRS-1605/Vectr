const express = require("express");
const path = require("path");
const { DatabaseSync } = require("node:sqlite");
const { createMessage } = require("../../shared/message-contract");
const { broadcastMessage } = require("../ws");

const db = new DatabaseSync(path.join(__dirname, "..", "axon-core.sqlite"));
db.exec("CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, checked INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
function listTodos() { return db.prepare("SELECT id, text, checked, created_at AS createdAt FROM todos ORDER BY id DESC").all().map((item) => ({ ...item, checked: Boolean(item.checked) })); }
function broadcastTodos(wss) { const items = listTodos(); broadcastMessage(wss, createMessage("todos.update", { items }, "axon-core")); return items; }

function createTodoRoutes(wss) {
  const router = express.Router();
  router.get("/todos", (req, res) => res.json(listTodos()));
  router.post("/todos", (req, res) => {
    const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
    if (!text) return res.status(400).json({ error: "text is required." });
    db.prepare("INSERT INTO todos (text) VALUES (?)").run(text);
    return res.status(201).json(broadcastTodos(wss));
  });
  router.patch("/todos/:id", (req, res) => {
    const id = Number(req.params.id); const input = req.body || {}; const fields = []; const values = [];
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "Invalid todo id." });
    if (Object.hasOwn(input, "text")) { if (typeof input.text !== "string" || !input.text.trim()) return res.status(400).json({ error: "text must be non-empty." }); fields.push("text = ?"); values.push(input.text.trim()); }
    if (Object.hasOwn(input, "checked")) { if (typeof input.checked !== "boolean") return res.status(400).json({ error: "checked must be a boolean." }); fields.push("checked = ?"); values.push(input.checked ? 1 : 0); }
    if (!fields.length) return res.status(400).json({ error: "text and/or checked is required." });
    if (!db.prepare(`UPDATE todos SET ${fields.join(", ")} WHERE id = ?`).run(...values, id).changes) return res.status(404).json({ error: "Todo not found." });
    return res.json(broadcastTodos(wss));
  });
  router.delete("/todos/:id", (req, res) => {
    if (!db.prepare("DELETE FROM todos WHERE id = ?").run(Number(req.params.id)).changes) return res.status(404).json({ error: "Todo not found." });
    return res.json(broadcastTodos(wss));
  });
  router.post("/todos/clear", (req, res) => { db.exec("DELETE FROM todos"); return res.json(broadcastTodos(wss)); });
  return router;
}
module.exports = { createTodoRoutes };
