const express = require("express");
const fs = require("fs/promises");
const path = require("path");
const crypto = require("crypto");
const multer = require("multer");
const { db } = require("../db");
const { storage } = require("../storage");

const photosDirectory = storage.inventoryPhotos;
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 10 * 1024 * 1024 } });

function validDate(value) { return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`)); }
function extension(file) {
  const supplied = path.extname(file?.originalname || "").toLowerCase();
  return [".jpg", ".jpeg", ".png", ".webp"].includes(supplied) ? supplied : file?.mimetype === "image/png" ? ".png" : ".jpg";
}
function row(item) { return { ...item, photo_url: item.photo_filename ? `/api/inventory/photos/${encodeURIComponent(item.photo_filename)}` : null }; }

function createInventoryRoutes() {
  const router = express.Router();
  router.get("/inventory", (_req, res) => res.json(db.prepare("SELECT * FROM inventory_items ORDER BY expiry_date ASC, created_at DESC").all().map(row)));
  router.post("/inventory", upload.single("photo"), async (req, res, next) => {
    const name = String(req.body?.name || "").trim(); const manufactureDate = req.body?.manufactureDate; const expiryDate = req.body?.expiryDate; const quantity = Number(req.body?.quantity);
    if (!name || name.length > 120 || !validDate(manufactureDate) || !validDate(expiryDate)) return res.status(400).json({ error: "Name, manufacture date, and expiry date are required." });
    if (expiryDate < manufactureDate) return res.status(400).json({ error: "Expiry date must be after the manufacture date." });
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99999) return res.status(400).json({ error: "Quantity must be a whole number greater than zero." });
    if (req.file && !String(req.file.mimetype || "").startsWith("image/")) return res.status(400).json({ error: "Photo must be an image." });
    try {
      const id = crypto.randomUUID(); const now = new Date().toISOString(); let photoFilename = null;
      if (req.file) { await fs.mkdir(photosDirectory, { recursive: true }); photoFilename = `${id}${extension(req.file)}`; await fs.writeFile(path.join(photosDirectory, photoFilename), req.file.buffer, { flag: "wx" }); }
      db.prepare("INSERT INTO inventory_items (id,name,manufacture_date,expiry_date,quantity,photo_filename,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)").run(id, name, manufactureDate, expiryDate, quantity, photoFilename, now, now);
      res.status(201).json(row(db.prepare("SELECT * FROM inventory_items WHERE id = ?").get(id)));
    } catch (error) { next(error); }
  });
  router.delete("/inventory/:id", async (req, res, next) => {
    try {
      const item = db.prepare("SELECT * FROM inventory_items WHERE id = ?").get(req.params.id);
      if (!item) return res.status(404).json({ error: "Inventory item not found." });
      db.prepare("DELETE FROM inventory_items WHERE id = ?").run(item.id);
      if (item.photo_filename) await fs.unlink(path.join(photosDirectory, item.photo_filename)).catch(() => {});
      res.status(204).end();
    } catch (error) { next(error); }
  });
  router.get("/inventory/photos/:filename", async (req, res, next) => {
    const filename = path.basename(req.params.filename);
    if (filename !== req.params.filename || !/^[a-f0-9-]+\.(jpg|jpeg|png|webp)$/i.test(filename)) return res.status(400).json({ error: "Invalid photo." });
    return res.sendFile(path.join(photosDirectory, filename), (error) => {
      if (!error) return;
      if (!res.headersSent) res.status(error.code === "ENOENT" ? 404 : 500).json({ error: "Photo not found." });
      else next(error);
    });
  });
  return router;
}
module.exports = { createInventoryRoutes };
