const express = require("express");
const fs = require("fs/promises");
const { exec } = require("child_process");
const { promisify } = require("util");
const { input } = require("../platform");
const { configPath } = require("../runtime-paths");

const execAsync = promisify(exec);
const CONFIG_PATH = configPath;
const MACRO_IDS = Array.from({ length: 8 }, (_, index) => index + 1);

function defaultMacros() {
  return MACRO_IDS.map((id) => ({ id, label: `Macro ${id}`, type: "shell", command: "" }));
}

async function readConfig() {
  try {
    return JSON.parse(await fs.readFile(CONFIG_PATH, "utf8"));
  } catch (error) {
    if (error.code === "ENOENT") return { macros: defaultMacros() };
    throw error;
  }
}

async function readMacros() {
  const config = await readConfig();
  return Array.isArray(config.macros) ? config.macros : defaultMacros();
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

async function saveMacros(macros) {
  const config = await readConfig();
  const sortedMacros = [...macros].sort((a, b) => a.id - b.id);
  await fs.writeFile(CONFIG_PATH, `${JSON.stringify({ ...config, macros: sortedMacros }, null, 2)}\n`, "utf8");
  return sortedMacros;
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

  router.get("/macro/config", async (req, res, next) => {
    try {
      res.json(await readMacros());
    } catch (error) {
      next(error);
    }
  });

  router.post("/macro/config", async (req, res, next) => {
    const macros = req.body?.macros;
    const validationError = validateMacros(macros);
    if (validationError) return res.status(400).json({ error: validationError });
    try {
      res.json(await saveMacros(macros));
    } catch (error) {
      next(error);
    }
  });

  return router;
}

module.exports = { createMacroRoutes, executeMacro };
