const express = require("express");
const fs = require("fs/promises");
const crypto = require("crypto");
const { broadcastMessage } = require("../ws");
const { createMessage } = require("../../shared/message-contract");
const { schedWallStatePath } = require("../runtime-paths");

const STATE_PATH = schedWallStatePath;
const DEFAULT_STATE = { quote: "Si vis pacem, para bellum", template: [], overlay: [], checked: {} };

function todayYmd() { return new Date().toISOString().slice(0, 10); }
function validDate(value) { return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value); }

async function readState() {
  try {
    const parsed = JSON.parse(await fs.readFile(STATE_PATH, "utf8"));
    return { ...DEFAULT_STATE, ...parsed, template: Array.isArray(parsed.template) ? parsed.template : [], overlay: Array.isArray(parsed.overlay) ? parsed.overlay : [], checked: parsed.checked && typeof parsed.checked === "object" ? parsed.checked : {} };
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    return { ...DEFAULT_STATE, template: [], overlay: [], checked: {} };
  }
}

async function writeState(state) { await fs.writeFile(STATE_PATH, `${JSON.stringify(state, null, 2)}\n`, "utf8"); }

function purgePast(state) {
  const today = todayYmd();
  state.overlay = state.overlay.filter((task) => task.date >= today);
  for (const key of Object.keys(state.checked)) {
    const date = key.match(/-(\d{4}-\d{2}-\d{2})$/)?.[1];
    if (date && date < today) delete state.checked[key];
  }
  return state;
}

function createSchedWallRoutes(wss) {
  const router = express.Router();
  const publish = async () => {
    const state = purgePast(await readState());
    await writeState(state);
    broadcastMessage(wss, createMessage("schedwall.state", state, "axon-core"));
    return state;
  };
  const mutate = async (handler) => {
    const state = purgePast(await readState());
    const result = await handler(state);
    await writeState(state);
    broadcastMessage(wss, createMessage("schedwall.state", state, "axon-core"));
    return result;
  };

  router.get("/state", async (_req, res, next) => { try { res.json(await publish()); } catch (error) { next(error); } });
  router.post("/quote", async (req, res, next) => {
    const quote = String(req.body?.quote || "").trim().slice(0, 300);
    if (!quote) return res.status(400).json({ error: "A quote is required." });
    try { await mutate((state) => { state.quote = quote; }); res.json({ ok: true }); } catch (error) { next(error); }
  });
  router.post("/task", async (req, res, next) => {
    const { layer, day, date, start, end, title } = req.body || {};
    const s = Number(start), e = Number(end), label = String(title || "").trim().slice(0, 120);
    if (!['template', 'overlay'].includes(layer) || !Number.isInteger(s) || !Number.isInteger(e) || s < 7 || e > 24 || e <= s || !label) return res.status(400).json({ error: "Invalid schedule item." });
    if (layer === "template" && (!Number.isInteger(Number(day)) || Number(day) < 1 || Number(day) > 7)) return res.status(400).json({ error: "Invalid weekday." });
    if (layer === "overlay" && (!validDate(date) || date < todayYmd())) return res.status(400).json({ error: "Invalid overlay date." });
    const task = { id: crypto.randomUUID(), start: s, end: e, title: label, ...(layer === "template" ? { day: Number(day) } : { date }) };
    try { await mutate((state) => { state[layer].push(task); }); res.status(201).json(task); } catch (error) { next(error); }
  });
  router.delete("/tasks/:layer", async (req, res, next) => {
    const layer = req.params.layer;
    if (!['template', 'overlay'].includes(layer)) return res.status(400).json({ error: "Invalid layer." });
    try { await mutate((state) => { const ids = new Set(state[layer].map((task) => task.id)); state[layer] = []; Object.keys(state.checked).forEach((key) => { if (ids.has(key.split("-")[0])) delete state.checked[key]; }); }); res.json({ ok: true }); } catch (error) { next(error); }
  });
  router.delete("/task/:layer/:id", async (req, res, next) => {
    const { layer, id } = req.params;
    if (!['template', 'overlay'].includes(layer)) return res.status(400).json({ error: "Invalid layer." });
    try { await mutate((state) => { state[layer] = state[layer].filter((task) => task.id !== id); Object.keys(state.checked).forEach((key) => { if (key.startsWith(`${id}-`)) delete state.checked[key]; }); }); res.json({ ok: true }); } catch (error) { next(error); }
  });
  router.post("/check", async (req, res, next) => {
    const { taskId, date, checked } = req.body || {};
    if (typeof taskId !== "string" || !validDate(date) || typeof checked !== "boolean") return res.status(400).json({ error: "Invalid check update." });
    try { await mutate((state) => { const key = `${taskId}-${date}`; if (checked) state.checked[key] = true; else delete state.checked[key]; }); res.json({ ok: true }); } catch (error) { next(error); }
  });
  router.get("/scroll-:direction", (req, res) => {
    const direction = req.params.direction;
    if (!['up', 'down'].includes(direction)) return res.status(400).json({ error: "Invalid direction." });
    broadcastMessage(wss, createMessage("schedwall.scroll", { direction }, "axon-core"));
    return res.json({ ok: true });
  });
  return router;
}

module.exports = { createSchedWallRoutes };
