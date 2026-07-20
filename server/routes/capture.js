const express = require("express");
const fs = require("fs/promises");
const path = require("path");
const crypto = require("crypto");
const multer = require("multer");
const { execFile } = require("child_process");
const { promisify } = require("util");
const { createMessage } = require("../../shared/message-contract");
const { broadcastMessage } = require("../ws");
const { storage } = require("../storage");

const notesDirectory = storage.captureNotes;
const attachmentsDirectory = storage.captureAttachments;
const logPath = path.join(notesDirectory, "log.md");
const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 10 * 1024 * 1024 } });
const execFileAsync = promisify(execFile);
const os = require("os");
const whisperBinary = process.env.WHISPER_BINARY || path.join(os.homedir(), "whisper.cpp", "build", "bin", process.platform === "win32" ? "whisper-cli.exe" : "whisper-cli");
const whisperModel = process.env.WHISPER_MODEL || path.join(os.homedir(), "whisper.cpp", "models", "ggml-small.bin");
const voiceScratchDirectory = path.join(os.tmpdir(), "axon-core-voice");

function sanitizeFilename(heading) {
  const sanitized = heading
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9-]/g, "")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  return sanitized || "untitled";
}

function timestampSuffix(date) {
  const part = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}${part(date.getMonth() + 1)}${part(date.getDate())}-${part(date.getHours())}${part(date.getMinutes())}${part(date.getSeconds())}`;
}

function imageExtension(file) {
  const extension = path.extname(file.originalname || "").toLowerCase().replace(/[^a-z0-9.]/g, "");
  if (extension && extension.length <= 8) return extension;
  return file.mimetype === "image/png" ? ".png" : ".jpg";
}

async function transcribeVoice(buffer) {
  const id = crypto.randomUUID();
  const audioPath = path.join(voiceScratchDirectory, `${id}.wav`);
  const outputBase = path.join(voiceScratchDirectory, id);
  const outputPath = `${outputBase}.txt`;
  try {
    await fs.access(whisperBinary);
    await fs.access(whisperModel);
    await fs.mkdir(voiceScratchDirectory, { recursive: true });
    await fs.writeFile(audioPath, buffer);
    const { stdout } = await execFileAsync(whisperBinary, ["-m", whisperModel, "-f", audioPath, "-nt", "-otxt", "-of", outputBase], {
      timeout: 30_000,
      maxBuffer: 1024 * 1024,
    });
    let transcript = "";
    try { transcript = await fs.readFile(outputPath, "utf8"); } catch (_) { transcript = stdout; }
    transcript = transcript.replace(/^\s*\[[^\]]+\]\s*/gm, "").replace(/\[BLANK_AUDIO\]/g, "").trim();
    if (!transcript) throw new Error("No speech was detected in the recording.");
    return transcript;
  } catch (error) {
    if (error.code === "ENOENT") throw new Error("Local whisper.cpp binary or small model is not available.");
    if (error.killed || error.signal === "SIGTERM") throw new Error("Voice transcription timed out after 30 seconds.");
    throw error;
  } finally {
    await Promise.all([audioPath, outputPath, `${outputBase}.vtt`, `${outputBase}.srt`, `${outputBase}.csv`].map((file) => fs.unlink(file).catch(() => {})));
  }
}

function frontmatterValue(contents, key) {
  const match = contents.match(new RegExp(`^${key}: (.+)$`, "m"));
  if (!match) return "";
  try { return JSON.parse(match[1]); } catch { return match[1]; }
}

function parseSpaceNote(filename, contents, detail = false) {
  const match = contents.match(/^---\r?\n[\s\S]*?\r?\n---\r?\n?([\s\S]*)$/);
  if (!match) return null;
  const body = match[1].trim();
  const imageMatch = body.match(/!\[\]\(attachments\/([^\)]+)\)/);
  const note = { filename, heading: frontmatterValue(contents, "title"), tag: frontmatterValue(contents, "tag"), timestamp: frontmatterValue(contents, "timestamp"), preview: body.replace(/!\[\]\(attachments\/[^\)]+\)/g, "").trim().slice(0, 100) };
  return detail ? {
    ...note,
    body: body.replace(/!\[\]\(attachments\/[^\)]+\)/g, "").trim(),
    imageFilename: imageMatch?.[1] || null,
    imagePath: imageMatch ? `attachments/${imageMatch[1]}` : null,
  } : note;
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function nextFilename(baseName, capturedAt) {
  let filename = `${baseName}.md`;
  let counter = 1;
  while (true) {
    try {
      await fs.access(path.join(notesDirectory, filename));
      filename = `${baseName}-${timestampSuffix(capturedAt)}${counter > 1 ? `-${counter}` : ""}.md`;
      counter += 1;
    } catch (error) {
      if (error.code === "ENOENT") return filename;
      throw error;
    }
  }
}

async function appendToTagLog({ heading, tag, filename, timestamp }) {
  let log = "";
  try { log = await fs.readFile(logPath, "utf8"); } catch (error) { if (error.code !== "ENOENT") throw error; }
  const bullet = `- [${heading}](${filename}) — ${timestamp}`;
  const tagHeader = new RegExp(`^## ${escapeRegExp(tag)}\\s*$`, "m");
  const headerMatch = tagHeader.exec(log);

  if (!headerMatch) {
    const separator = log && !log.endsWith("\n") ? "\n" : "";
    log += `${separator}${log ? "\n" : ""}## ${tag}\n${bullet}\n`;
  } else {
    const insertAt = headerMatch.index + headerMatch[0].length;
    log = `${log.slice(0, insertAt)}\n${bullet}${log.slice(insertAt)}`;
  }
  await fs.writeFile(logPath, log, "utf8");
}

function createCaptureService(wss) {
  async function saveCapture({ heading, tag, body, imageFilename = null }) {
    if (typeof heading !== "string" || typeof tag !== "string" || typeof body !== "string") {
      throw new Error("heading, tag, and body must be strings.");
    }
    const title = heading.trim() || "Untitled";
    const normalizedTag = tag.trim().replace(/[\r\n]+/g, " ") || "untagged";
    const capturedAt = new Date();
    const timestamp = capturedAt.toISOString();
    await fs.mkdir(notesDirectory, { recursive: true });
    const filename = await nextFilename(sanitizeFilename(title), capturedAt);
    const safeImageFilename = typeof imageFilename === "string" && path.basename(imageFilename) === imageFilename ? imageFilename : null;
    const imageMarkdown = safeImageFilename ? `\n\n![](attachments/${safeImageFilename})` : "";
    const contents = `---\ntitle: ${JSON.stringify(title)}\ntag: ${JSON.stringify(normalizedTag)}\ntimestamp: ${JSON.stringify(timestamp)}\n---\n\n${body}${imageMarkdown}`;
    await fs.writeFile(path.join(notesDirectory, filename), contents, "utf8");
    await appendToTagLog({ heading: title, tag: normalizedTag, filename, timestamp });

    const payload = { heading: title, tag: normalizedTag, filename, imageFilename: safeImageFilename, preview: body.slice(0, 100) };
    broadcastMessage(wss, createMessage("capture.new", payload, "axon-core"));
    return payload;
  }

  return { saveCapture };
}

function createCaptureRoutes(captureService) {
  const router = express.Router();

  router.get("/space/notes", async (req, res, next) => {
    try {
      let entries;
      try { entries = await fs.readdir(notesDirectory, { withFileTypes: true }); } catch (error) {
        if (error.code === "ENOENT") return res.json([]);
        throw error;
      }
      const notes = (await Promise.all(entries
        .filter((entry) => entry.isFile() && entry.name.endsWith(".md") && entry.name !== "log.md")
        .map(async (entry) => parseSpaceNote(entry.name, await fs.readFile(path.join(notesDirectory, entry.name), "utf8")))))
        .filter(Boolean)
        .sort((a, b) => new Date(b.timestamp) - new Date(a.timestamp));
      return res.json(notes);
    } catch (error) { return next(error); }
  });

  router.get("/space/notes/:filename", async (req, res, next) => {
    const filename = path.basename(req.params.filename);
    if (filename !== req.params.filename || !filename.endsWith(".md") || filename === "log.md") return res.status(400).json({ error: "Invalid note filename." });
    try {
      const note = parseSpaceNote(filename, await fs.readFile(path.join(notesDirectory, filename), "utf8"), true);
      return note ? res.json(note) : res.status(404).json({ error: "Note not found." });
    } catch (error) { if (error.code === "ENOENT") return res.status(404).json({ error: "Note not found." }); return next(error); }
  });

  router.delete("/space/notes/:filename", async (req, res, next) => {
    const filename = path.basename(req.params.filename);
    if (filename !== req.params.filename || !filename.endsWith(".md") || filename === "log.md") return res.status(400).json({ error: "Invalid note filename." });
    try {
      const notePath = path.join(notesDirectory, filename);
      const note = parseSpaceNote(filename, await fs.readFile(notePath, "utf8"), true);
      if (!note) return res.status(404).json({ error: "Note not found." });

      await fs.unlink(notePath);
      if (note.imageFilename) await fs.unlink(path.join(attachmentsDirectory, note.imageFilename)).catch((error) => {
        if (error.code !== "ENOENT") throw error;
      });
      return res.status(204).end();
    } catch (error) {
      if (error.code === "ENOENT") return res.status(404).json({ error: "Note not found." });
      return next(error);
    }
  });

  router.get("/space/attachments/:filename", (req, res) => {
    const filename = path.basename(req.params.filename);
    if (filename !== req.params.filename) return res.status(400).json({ error: "Invalid attachment filename." });
    return res.sendFile(filename, { root: attachmentsDirectory }, (error) => { if (error && !res.headersSent) res.status(error.code === "ENOENT" ? 404 : 500).json({ error: "Attachment not found." }); });
  });

  router.get("/capture/tags", async (req, res, next) => {
    try {
      let entries;
      try { entries = await fs.readdir(notesDirectory, { withFileTypes: true }); } catch (error) {
        if (error.code === "ENOENT") return res.json([]);
        throw error;
      }
      const tags = new Set();
      await Promise.all(entries
        .filter((entry) => entry.isFile() && entry.name.endsWith(".md") && entry.name !== "log.md")
        .map(async (entry) => {
          const tag = frontmatterValue(await fs.readFile(path.join(notesDirectory, entry.name), "utf8"), "tag");
          if (tag) tags.add(tag);
        }));
      return res.json([...tags].sort((a, b) => a.localeCompare(b)));
    } catch (error) { return next(error); }
  });

  router.post("/capture", async (req, res, next) => {
    const input = req.body || {};
    const body = typeof input.body === "string" ? input.body : input.text;
    if (typeof body !== "string") return res.status(400).json({ error: "body must be a string." });
    const heading = typeof input.heading === "string" ? input.heading : "Admin capture";
    const tag = typeof input.tag === "string" ? input.tag : (typeof input.source === "string" ? input.source : "admin-console");
    try {
      return res.status(201).json(await captureService.saveCapture({ heading, tag, body, imageFilename: input.imageFilename }));
    } catch (error) { return next(error); }
  });

  router.post("/capture/upload-image", upload.single("image"), async (req, res, next) => {
    if (!req.file || !req.file.mimetype.startsWith("image/")) {
      return res.status(400).json({ error: "An image file is required." });
    }
    try {
      await fs.mkdir(attachmentsDirectory, { recursive: true });
      const filename = `${timestampSuffix(new Date())}-${crypto.randomUUID().slice(0, 8)}${imageExtension(req.file)}`;
      await fs.writeFile(path.join(attachmentsDirectory, filename), req.file.buffer);
      return res.status(201).json({ filename });
    } catch (error) { return next(error); }
  });

  router.post("/capture/upload-voice", upload.single("audio"), async (req, res, next) => {
    if (!req.file || !req.file.buffer?.length || !/^audio\//.test(req.file.mimetype || "")) {
      return res.status(400).json({ error: "A non-empty WAV audio file is required." });
    }
    try {
      const transcript = await transcribeVoice(req.file.buffer);
      const heading = typeof req.body?.heading === "string" && req.body.heading.trim() ? req.body.heading : `Voice ${new Date().toLocaleString()}`;
      // The phone requests transcription-only while the user is editing the
      // generated capture. Queued uploads save immediately after reconnect.
      const capture = req.body?.save === "false" ? { heading, tag: "voice" } : await captureService.saveCapture({ heading, tag: "voice", body: transcript });
      return res.status(201).json({ transcript, capture });
    } catch (error) {
      return res.status(422).json({ error: error.message || "Voice transcription failed." });
    }
  });

  return router;
}

module.exports = { createCaptureRoutes, createCaptureService, transcribeVoice };
