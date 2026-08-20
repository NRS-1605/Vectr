const express = require("express");
const fs = require("fs/promises");
const { exec } = require("child_process");
const { promisify } = require("util");
const { input } = require("../platform");
const { configPath } = require("../runtime-paths");

const execAsync = promisify(exec);
const CONFIG_PATH = configPath;
const MACRO_IDS = Array.from({ length: 8 }, (_, index) => index + 1);
const DEFAULT_PRESET = "General";

function defaultMacros() {
  return MACRO_IDS.map((id) => ({ id, label: `Macro ${id}`, type: "shell", command: "" }));
}

async function readConfig() {
  let config;
  try {
    config = JSON.parse(await fs.readFile(CONFIG_PATH, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return { macroPresets: { [DEFAULT_PRESET]: defaultMacros() }, activeMacroPreset: DEFAULT_PRESET };
    throw error;
  }
  if (config.macroPresets && typeof config.macroPresets === "object") {
    if (!config.macroPresets[DEFAULT_PRESET]) config.macroPresets[DEFAULT_PRESET] = defaultMacros();
    if (config.activeMacroPreset == null) config.activeMacroPreset = DEFAULT_PRESET;
    return config;
  }
  const legacy = Array.isArray(config.macros) ? config.macros : defaultMacros();
  return { ...config, macroPresets: { [DEFAULT_PRESET]: legacy }, activeMacroPreset: DEFAULT_PRESET };
}

async function readPresets() {
  const config = await readConfig();
  const presets = config.macroPresets || {};
  const active = presets[config.activeMacroPreset] ? config.activeMacroPreset : Object.keys(presets)[0] || DEFAULT_PRESET;
  return { presets, active };
}

async function readMacros(presetName = null) {
  const { presets, active } = await readPresets();
  const name = presetName && presets[presetName] ? presetName : active;
  return Array.isArray(presets[name]) ? presets[name] : defaultMacros();
}

async function persistPresets(macroPresets, activeMacroPreset) {
  const config = await readConfig();
  const next = { ...config, macroPresets, activeMacroPreset };
  delete next.macros;
  await fs.writeFile(CONFIG_PATH, `${JSON.stringify(next, null, 2)}\n`, "utf8");
}

function validateMacros(macros) {
  if (!Array.isArray(macros) || macros.length !== 8) return "Exactly 8 macro mappings are required.";
  const ids = new Set();
  for (const macro of macros) {
    if (!macro || !MACRO_IDS.includes(macro.id) || ids.has(macro.id)) return "Macro ids must be unique numbers from 1 to 8.";
    if (typeof macro.label !== "string" || typeof macro.command !== "string") return "Macro labels and commands must be strings.";
    if (macro.type !== "shell" && macro.type !== "keypress") return "Macro type must be shell or keypress.";
    ids.add(macro.id);
  }
  return null;
}

function sanitizePresetName(value) {
  if (typeof value !== "string") return null;
  const name = value.trim();
  if (!name || name.length > 40) return null;
  return name;
}

async function createPreset(name) {
  const { presets } = await readPresets();
  if (presets[name]) return { error: "A preset with that name already exists." };
  const next = { ...presets, [name]: defaultMacros() };
  const { active } = await readPresets();
  await persistPresets(next, active);
  return { presets: Object.keys(next), active };
}

async function renamePreset(oldName, newName) {
  const { presets, active } = await readPresets();
  if (!presets[oldName]) return { error: "Preset not found." };
  if (presets[newName]) return { error: "A preset with that name already exists." };
  const next = {};
  for (const [key, value] of Object.entries(presets)) next[key === oldName ? newName : key] = value;
  await persistPresets(next, active === oldName ? newName : active);
  return { presets: Object.keys(next), active: active === oldName ? newName : active };
}

async function deletePreset(name) {
  const { presets, active } = await readPresets();
  if (!presets[name]) return { error: "Preset not found." };
  if (Object.keys(presets).length <= 1) return { error: "Cannot delete the last preset." };
  const next = { ...presets };
  delete next[name];
  const newActive = active === name ? Object.keys(next)[0] : active;
  await persistPresets(next, newActive);
  return { presets: Object.keys(next), active: newActive };
}

async function activatePreset(name) {
  const { presets } = await readPresets();
  if (!presets[name]) return { error: "Preset not found." };
  await persistPresets(presets, name);
  return { presets: Object.keys(presets), active: name };
}

function runKeypress(command) {
  const trimmed = command.trim();
  const [mode, remainder] = trimmed.startsWith("type ")
    ? ["type", trimmed.slice(5)]
    : trimmed.startsWith("key ")
      ? ["key", trimmed.slice(4)]
      : ["key", trimmed];
  const args = mode === "type" ? [mode, remainder] : [mode, ...remainder.split(/\s+/).filter(Boolean)];

  return input.keypress(`${mode} ${remainder}`);
}

async function executeMacro(buttonId, requestId) {
  const macros = await readMacros();
  const macro = macros.find((item) => item.id === buttonId);
  const timestamp = Date.now();
  if (!macro) return { buttonId, requestId, success: false, label: "Unknown macro", timestamp, error: "No mapping found for this button." };
  if (!macro.command.trim()) return { buttonId, requestId, success: false, label: macro.label, timestamp, error: "No command configured." };

  try {
    if (macro.type === "shell") await execAsync(macro.command);
    else await runKeypress(macro.command);
    return { buttonId, requestId, success: true, label: macro.label, timestamp, error: null };
  } catch (error) {
    return { buttonId, requestId, success: false, label: macro.label, timestamp, error: error.message || "Macro execution failed." };
  }
}

function createMacroRoutes() {
  const router = express.Router();

  router.get("/macro/presets", async (_req, res, next) => {
    try {
      const { presets, active } = await readPresets();
      res.json({ presets: Object.keys(presets), active });
    } catch (error) { next(error); }
  });

  router.post("/macro/presets", async (req, res, next) => {
    const name = sanitizePresetName(req.body?.name);
    if (!name) return res.status(400).json({ error: "Preset name is required (max 40 characters)." });
    try {
      const result = await createPreset(name);
      if (result.error) return res.status(409).json({ error: result.error });
      res.status(201).json(result);
    } catch (error) { next(error); }
  });

  router.post("/macro/presets/:name/activate", async (req, res, next) => {
    try {
      const name = sanitizePresetName(decodeURIComponent(req.params.name));
      if (!name) return res.status(400).json({ error: "Preset name is required." });
      const result = await activatePreset(name);
      if (result.error) return res.status(404).json({ error: result.error });
      res.json(result);
    } catch (error) { next(error); }
  });

  router.post("/macro/presets/:name/rename", async (req, res, next) => {
    const newName = sanitizePresetName(req.body?.name);
    if (!newName) return res.status(400).json({ error: "Preset name is required (max 40 characters)." });
    try {
      const oldName = sanitizePresetName(decodeURIComponent(req.params.name));
      if (!oldName) return res.status(400).json({ error: "Preset name is required." });
      const result = await renamePreset(oldName, newName);
      if (result.error) return res.status(409).json({ error: result.error });
      res.json(result);
    } catch (error) { next(error); }
  });

  router.delete("/macro/presets/:name", async (req, res, next) => {
    try {
      const name = sanitizePresetName(decodeURIComponent(req.params.name));
      if (!name) return res.status(400).json({ error: "Preset name is required." });
      const result = await deletePreset(name);
      if (result.error) return res.status(result.error === "Preset not found." ? 404 : 409).json({ error: result.error });
      res.json(result);
    } catch (error) { next(error); }
  });

  router.get("/macro/config", async (req, res, next) => {
    try {
      const presetName = typeof req.query.preset === "string" ? sanitizePresetName(req.query.preset) : null;
      res.json(await readMacros(presetName));
    } catch (error) {
      next(error);
    }
  });

  router.post("/macro/config", async (req, res, next) => {
    const macros = req.body?.macros;
    const validationError = validateMacros(macros);
    if (validationError) return res.status(400).json({ error: validationError });
    try {
      const presetName = typeof req.body?.preset === "string" ? sanitizePresetName(req.body.preset) : null;
      res.json(await saveMacros(macros, presetName));
    } catch (error) {
      next(error);
    }
  });

  return router;
}

async function saveMacros(macros, presetName = null) {
  const { presets, active } = await readPresets();
  const name = presetName && presets[presetName] ? presetName : active;
  const sortedMacros = [...macros].sort((a, b) => a.id - b.id);
  await persistPresets({ ...presets, [name]: sortedMacros }, active);
  return sortedMacros;
}

module.exports = { createMacroRoutes, executeMacro };