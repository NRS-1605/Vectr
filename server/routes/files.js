const express = require("express");
const fs = require("fs");
const fsp = require("fs/promises");
const path = require("path");
const multer = require("multer");
const { createMessage } = require("../../shared/message-contract");
const { broadcastMessage } = require("../ws");
const { storage } = require("../storage");

const incomingDirectory = storage.filesIncoming;
const outgoingDirectory = storage.filesOutgoing;
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 100 * 1024 * 1024, files: 1 } });

function timestampSuffix(date = new Date()) {
  const part = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}${part(date.getMonth() + 1)}${part(date.getDate())}-${part(date.getHours())}${part(date.getMinutes())}${part(date.getSeconds())}`;
}

function safeFilename(filename) {
  const name = path.basename(String(filename || "")).replace(/[\0/\\]/g, "").trim();
  return name && name !== "." && name !== ".." ? name : null;
}

async function uniqueIncomingFilename(originalFilename) {
  const extension = path.extname(originalFilename);
  const stem = path.basename(originalFilename, extension) || "upload";
  let candidate = originalFilename;
  let counter = 1;
  while (true) {
    try {
      await fsp.access(path.join(incomingDirectory, candidate));
      candidate = `${stem}-${timestampSuffix()}${counter > 1 ? `-${counter}` : ""}${extension}`;
      counter += 1;
    } catch (error) {
      if (error.code === "ENOENT") return candidate;
      throw error;
    }
  }
}

function createFileRoutes(wss) {
  const router = express.Router();

  router.post("/files/upload", upload.single("file"), async (req, res, next) => {
    const originalFilename = safeFilename(req.file?.originalname);
    if (!req.file || !originalFilename) return res.status(400).json({ error: "A file with a valid filename is required." });
    try {
      await fsp.mkdir(incomingDirectory, { recursive: true });
      const filename = await uniqueIncomingFilename(originalFilename);
      await fsp.writeFile(path.join(incomingDirectory, filename), req.file.buffer, { flag: "wx" });
      broadcastMessage(wss, createMessage("file.received", { filename }, "axon-core"));
      return res.status(201).json({ filename });
    } catch (error) {
      if (error.code === "EEXIST") return next(new Error("Could not allocate a unique filename; please retry."));
      return next(error);
    }
  });

  router.get("/files/list", async (req, res, next) => {
    try {
      let entries;
      try { entries = await fsp.readdir(outgoingDirectory, { withFileTypes: true }); } catch (error) {
        if (error.code === "ENOENT") return res.json([]);
        throw error;
      }
      const files = await Promise.all(entries.filter((entry) => entry.isFile()).map(async (entry) => {
        const stat = await fsp.stat(path.join(outgoingDirectory, entry.name));
        return { filename: entry.name, size: stat.size };
      }));
      return res.json(files.sort((a, b) => a.filename.localeCompare(b.filename)));
    } catch (error) { return next(error); }
  });

  router.get("/files/download/:filename", async (req, res, next) => {
    const filename = safeFilename(req.params.filename);
    if (!filename || filename !== req.params.filename) return res.status(400).json({ error: "Invalid filename." });
    const filePath = path.join(outgoingDirectory, filename);
    try {
      const stat = await fsp.stat(filePath);
      if (!stat.isFile()) return res.status(404).json({ error: "File not found." });
      res.status(200);
      res.setHeader("Content-Type", "application/octet-stream");
      res.setHeader("Content-Length", stat.size);
      res.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`);
      const stream = fs.createReadStream(filePath);
      stream.on("error", next);
      stream.pipe(res);
    } catch (error) {
      if (error.code === "ENOENT") return res.status(404).json({ error: "File not found." });
      return next(error);
    }
  });

  return router;
}

module.exports = { createFileRoutes };
