const express = require("express");
const { DatabaseSync } = require("node:sqlite");
const { createMessage } = require("../../shared/message-contract");
const { broadcastMessage } = require("../ws");
const { databasePath } = require("../runtime-paths");

const db = new DatabaseSync(databasePath);
db.exec("CREATE TABLE IF NOT EXISTS todo_lists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
db.exec("CREATE TABLE IF NOT EXISTS todos (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, checked INTEGER NOT NULL DEFAULT 0, list_id INTEGER, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
const columns = db.prepare("PRAGMA table_info(todos)").all().map((c) => c.name);
if (!columns.includes("list_id")) db.exec("ALTER TABLE todos ADD COLUMN list_id INTEGER");

function listLists() { return db.prepare("SELECT id, name FROM todo_lists ORDER BY id").all(); }
function listTodos() {
  return db.prepare("SELECT id, text, checked, list_id AS listId, created_at AS createdAt FROM todos ORDER BY checked ASC, id DESC").all()
    .map((item) => ({ ...item, checked: Boolean(item.checked) }));
}
function fullState() { return { lists: listLists(), items: listTodos() }; }
function broadcastTodos(wss) { const state = fullState(); broadcastMessage(wss, createMessage("todos.update", state, "axon-core")); return state; }

function createTodoRoutes(wss) {
  const router = express.Router();
  router.get("/todos", (req, res) => res.json(fullState()));

  router.get("/todos/lists", (req, res) => res.json(listLists()));
  router.post("/todos/lists", (req, res) => {
    const name = typeof req.body?.name === "string" ? req.body.name.trim() : "";
    if (!name) return res.status(400).json({ error: "name is required." });
    db.prepare("INSERT INTO todo_lists (name) VALUES (?)").run(name);
    return res.status(201).json(broadcastTodos(wss));
  });
  router.delete("/todos/lists/:id", (req, res) => {
    const id = Number(req.params.id);
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "Invalid list id." });
    if (!db.prepare("DELETE FROM todo_lists WHERE id = ?").run(id).changes) return res.status(404).json({ error: "List not found." });
    db.prepare("DELETE FROM todos WHERE list_id = ?").run(id);
    return res.json(broadcastTodos(wss));
  });

  router.post("/todos", (req, res) => {
    const text = typeof req.body?.text === "string" ? req.body.text.trim() : "";
    if (!text) return res.status(400).json({ error: "text is required." });
    const listId = Number.isInteger(req.body?.listId) ? req.body.listId : null;
    db.prepare("INSERT INTO todos (text, list_id) VALUES (?, ?)").run(text, listId);
    return res.status(201).json(broadcastTodos(wss));
  });
  router.patch("/todos/:id", (req, res) => {
    const id = Number(req.params.id); const input = req.body || {}; const fields = []; const values = [];
    if (!Number.isInteger(id) || id < 1) return res.status(400).json({ error: "Invalid todo id." });
    if (Object.hasOwn(input, "text")) { if (typeof input.text !== "string" || !input.text.trim()) return res.status(400).json({ error: "text must be non-empty." }); fields.push("text = ?"); values.push(input.text.trim()); }
    if (Object.hasOwn(input, "checked")) { if (typeof input.checked !== "boolean") return res.status(400).json({ error: "checked must be a boolean." }); fields.push("checked = ?"); values.push(input.checked ? 1 : 0); }
    if (Object.hasOwn(input, "listId")) { fields.push("list_id = ?"); values.push(input.listId == null ? null : Number(input.listId)); }
    if (!fields.length) return res.status(400).json({ error: "text, checked and/or listId is required." });
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