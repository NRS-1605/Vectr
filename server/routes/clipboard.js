const express = require("express");
const { getHistory, clearHistory } = require("../clipboard-history");

function createClipboardRoutes() {
  const router = express.Router();
  router.get("/clipboard/history", async (_req, res, next) => {
    try { res.json(getHistory(50)); } catch (error) { next(error); }
  });
  router.post("/clipboard/clear", async (_req, res, next) => {
    try { clearHistory(); res.json({ ok: true }); } catch (error) { next(error); }
  });
  return router;
}

module.exports = { createClipboardRoutes };
