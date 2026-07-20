const fs = require("fs/promises");
const os = require("os");
const path = require("path");

const axonDirectory = path.join(os.homedir(), "axon");
const storage = Object.freeze({
  root: axonDirectory,
  captures: path.join(axonDirectory, "captures"),
  captureNotes: path.join(axonDirectory, "captures", "notes"),
  captureAttachments: path.join(axonDirectory, "captures", "notes", "attachments"),
  files: path.join(axonDirectory, "files"),
  filesIncoming: path.join(axonDirectory, "files", "incoming"),
  filesOutgoing: path.join(axonDirectory, "files", "outgoing"),
  inventory: path.join(axonDirectory, "inventory"),
  inventoryPhotos: path.join(axonDirectory, "inventory", "photos"),
});

async function moveLegacyDirectory(from, to) {
  try { await fs.access(from); } catch (error) { if (error.code === "ENOENT") return; throw error; }
  try { await fs.access(to); return; } catch (error) { if (error.code !== "ENOENT") throw error; }
  await fs.mkdir(path.dirname(to), { recursive: true });
  await fs.rename(from, to);
}

async function prepareStorage() {
  await moveLegacyDirectory(path.join(os.homedir(), "axon-inbox", "NotesFromPhone"), storage.captureNotes);
  await moveLegacyDirectory(path.join(os.homedir(), "axon-transfer"), storage.files);
  await moveLegacyDirectory(path.join(os.homedir(), "axon-inventory"), storage.inventory);
  await Promise.all([storage.captureAttachments, storage.filesIncoming, storage.filesOutgoing, storage.inventoryPhotos].map((directory) => fs.mkdir(directory, { recursive: true })));
}

module.exports = { storage, prepareStorage };
