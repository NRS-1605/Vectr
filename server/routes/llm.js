const express = require("express");
const fs = require("fs/promises");
const { configPath } = require("../runtime-paths");

const CONFIG_PATH = configPath;
const DEFAULT_CONFIG = { defaultMode: "local", localModel: process.env.MODEL_LOCAL || "llama3", cloudModel: process.env.MODEL_CLOUD || "gpt-4.1-mini" };

async function readLlmConfig() {
  try {
    const config = JSON.parse(await fs.readFile(CONFIG_PATH, "utf8"));
    const llm = config.llm || {};
    return {
      defaultMode: llm.defaultMode === "cloud" ? "cloud" : "local",
      localModel: typeof llm.localModel === "string" && llm.localModel.trim() ? llm.localModel.trim() : DEFAULT_CONFIG.localModel,
      cloudModel: typeof llm.cloudModel === "string" && llm.cloudModel.trim() ? llm.cloudModel.trim() : DEFAULT_CONFIG.cloudModel,
    };
  } catch (error) {
    if (error.code === "ENOENT") return { ...DEFAULT_CONFIG };
    throw error;
  }
}

async function saveLlmConfig(next) {
  let config = {};
  try { config = JSON.parse(await fs.readFile(CONFIG_PATH, "utf8")); } catch (error) { if (error.code !== "ENOENT") throw error; }
  const llm = {
    defaultMode: next.defaultMode === "cloud" ? "cloud" : "local",
    localModel: next.localModel.trim(),
    cloudModel: next.cloudModel.trim(),
  };
  await fs.writeFile(CONFIG_PATH, `${JSON.stringify({ ...config, llm }, null, 2)}\n`, "utf8");
  return llm;
}

function cleanProviderError(provider, error) {
  const message = error instanceof Error ? error.message : String(error);
  return new Error(`${provider} request failed: ${message}`);
}

async function queryLlm(prompt, requestedMode) {
  if (typeof prompt !== "string" || !prompt.trim()) throw new Error("prompt must be a non-empty string.");
  const config = await readLlmConfig();
  const mode = requestedMode || config.defaultMode;
  if (mode !== "local" && mode !== "cloud") throw new Error("mode must be local or cloud.");
  try {
    if (mode === "local") {
      const response = await fetch(`${process.env.OLLAMA_URL || "http://localhost:11434"}/api/generate`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ model: config.localModel, prompt, stream: false }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || `Ollama returned ${response.status}`);
      if (typeof body.response !== "string") throw new Error("Ollama returned no response text.");
      return body.response;
    }
    if (!process.env.OPENAI_API_KEY) throw new Error("OPENAI_API_KEY is not configured.");
    const response = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${process.env.OPENAI_API_KEY}` },
      body: JSON.stringify({ model: config.cloudModel, input: prompt }),
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error?.message || `OpenAI returned ${response.status}`);
    const result = body.output_text || body.output?.flatMap((item) => item.content || []).find((content) => content.type === "output_text")?.text;
    if (typeof result !== "string") throw new Error("OpenAI returned no response text.");
    return result;
  } catch (error) {
    throw cleanProviderError(mode === "local" ? "Ollama" : "OpenAI", error);
  }
}

function createLlmRoutes() {
  const router = express.Router();
  router.get("/llm/config", async (req, res, next) => { try { res.json(await readLlmConfig()); } catch (error) { next(error); } });
  router.post("/llm/config", async (req, res, next) => {
    const input = req.body || {};
    if (!["local", "cloud"].includes(input.defaultMode) || typeof input.localModel !== "string" || !input.localModel.trim() || typeof input.cloudModel !== "string" || !input.cloudModel.trim()) {
      return res.status(400).json({ error: "defaultMode, localModel, and cloudModel are required." });
    }
    try { return res.json(await saveLlmConfig(input)); } catch (error) { return next(error); }
  });
  router.post("/llm/query", async (req, res) => {
    try { return res.json({ result: await queryLlm(req.body?.prompt, req.body?.mode) }); }
    catch (error) { return res.status(502).json({ error: error.message || "LLM request failed." }); }
  });
  return router;
}

module.exports = { createLlmRoutes, queryLlm, readLlmConfig };
