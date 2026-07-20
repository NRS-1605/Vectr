const fs = require("fs/promises");
const path = require("path");
const { DatabaseSync } = require("node:sqlite");

const CONFIG_PATH = path.join(__dirname, "config.json");
const DB_PATH = path.join(__dirname, "axon-core.sqlite");
const DEFAULT = {
  voyageRewards: { coastal: 600, open_waters: 1500, uncharted: 3000 },
  abandonPenaltyPercent: 20,
  featureCosts: { touchpad: 150, macros: 100, files: 80, telemetry: 50, news: 50 },
  dailyFreeAllowance: 3,
  customMarginalRatePerMinute: 40,
  customMinimumDurationSeconds: 5400,
};
const DURATIONS = { coastal: 1500, open_waters: 3000, uncharted: 5400 };
const db = new DatabaseSync(DB_PATH);
const admittedSessions = new Map();

db.exec(`CREATE TABLE IF NOT EXISTS points_ledger (
  id INTEGER PRIMARY KEY AUTOINCREMENT, delta INTEGER NOT NULL, reason TEXT NOT NULL CHECK(reason IN ('voyage_completed','abandon_ship','feature_use','manual_reset')), feature TEXT, timestamp TEXT NOT NULL
); CREATE TABLE IF NOT EXISTS voyages (
  id TEXT PRIMARY KEY, duration_tier TEXT NOT NULL CHECK(duration_tier IN ('coastal','open_waters','uncharted','custom')), duration_seconds INTEGER NOT NULL, start_time TEXT NOT NULL, end_time TEXT, status TEXT NOT NULL CHECK(status IN ('in_progress','completed','abandoned')), berries_awarded INTEGER, planned_reward INTEGER
); CREATE TABLE IF NOT EXISTS daily_feature_usage (usage_date TEXT NOT NULL, feature TEXT NOT NULL, entries INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (usage_date, feature));
CREATE TABLE IF NOT EXISTS feature_gate_usage (feature TEXT NOT NULL, subject TEXT NOT NULL, entries INTEGER NOT NULL DEFAULT 0, first_used_at TEXT NOT NULL, PRIMARY KEY (feature, subject));`);
db.exec(`CREATE TABLE IF NOT EXISTS inventory_items (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, manufacture_date TEXT NOT NULL,
  expiry_date TEXT NOT NULL, quantity INTEGER NOT NULL DEFAULT 1, photo_filename TEXT, created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);`);
if (!db.prepare("PRAGMA table_info(inventory_items)").all().some((column) => column.name === "quantity")) db.exec("ALTER TABLE inventory_items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1");

const voyageSchema = db.prepare("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'voyages'").get()?.sql || "";
if (!voyageSchema.includes("'custom'")) {
  db.exec("ALTER TABLE voyages RENAME TO voyages_legacy; CREATE TABLE voyages (id TEXT PRIMARY KEY, duration_tier TEXT NOT NULL CHECK(duration_tier IN ('coastal','open_waters','uncharted','custom')), duration_seconds INTEGER NOT NULL, start_time TEXT NOT NULL, end_time TEXT, status TEXT NOT NULL CHECK(status IN ('in_progress','completed','abandoned')), berries_awarded INTEGER, planned_reward INTEGER); INSERT INTO voyages (id,duration_tier,duration_seconds,start_time,end_time,status,berries_awarded) SELECT id,duration_tier,duration_seconds,start_time,end_time,status,berries_awarded FROM voyages_legacy; DROP TABLE voyages_legacy;");
} else if (!db.prepare("PRAGMA table_info(voyages)").all().some((column) => column.name === "planned_reward")) db.exec("ALTER TABLE voyages ADD COLUMN planned_reward INTEGER");
const usageSchema = db.prepare("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'daily_feature_usage'").get()?.sql || "";
if (!usageSchema.includes("feature TEXT")) {
  db.exec("ALTER TABLE daily_feature_usage RENAME TO daily_feature_usage_legacy; CREATE TABLE daily_feature_usage (usage_date TEXT NOT NULL, feature TEXT NOT NULL, entries INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (usage_date, feature)); DROP TABLE daily_feature_usage_legacy;");
}
const gateUsageSchema = db.prepare("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'feature_gate_usage'").get()?.sql || "";
if (!gateUsageSchema.includes("subject TEXT")) {
  db.exec("ALTER TABLE feature_gate_usage RENAME TO feature_gate_usage_legacy; CREATE TABLE feature_gate_usage (feature TEXT NOT NULL, subject TEXT NOT NULL, entries INTEGER NOT NULL DEFAULT 0, first_used_at TEXT NOT NULL, PRIMARY KEY (feature, subject)); DROP TABLE feature_gate_usage_legacy;");
}

function localDate() {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}
function balance() { return Number(db.prepare("SELECT COALESCE(SUM(delta), 0) AS balance FROM points_ledger").get().balance); }
function insertLedger(delta, reason, feature = null) { db.prepare("INSERT INTO points_ledger (delta, reason, feature, timestamp) VALUES (?, ?, ?, ?)").run(delta, reason, feature, new Date().toISOString()); }
async function config() {
  try {
    const raw = JSON.parse(await fs.readFile(CONFIG_PATH, "utf8"));
    return { ...DEFAULT, ...(raw.berryEconomy || {}), voyageRewards: { ...DEFAULT.voyageRewards, ...(raw.berryEconomy?.voyageRewards || {}) }, featureCosts: { ...DEFAULT.featureCosts, ...(raw.berryEconomy?.featureCosts || {}) } };
  } catch (error) { if (error.code === "ENOENT") return DEFAULT; throw error; }
}
async function saveConfig(next) {
  const raw = JSON.parse(await fs.readFile(CONFIG_PATH, "utf8").catch(() => "{}"));
  const existing = await config();
  const normalized = { voyageRewards: {}, featureCosts: {}, abandonPenaltyPercent: Number(next.abandonPenaltyPercent), dailyFreeAllowance: Number(next.dailyFreeAllowance), customMarginalRatePerMinute: Number(next.customMarginalRatePerMinute ?? existing.customMarginalRatePerMinute), customMinimumDurationSeconds: Number(next.customMinimumDurationSeconds ?? existing.customMinimumDurationSeconds) };
  for (const tier of Object.keys(DURATIONS)) normalized.voyageRewards[tier] = Math.max(0, Math.floor(Number(next.voyageRewards?.[tier])));
  for (const feature of Object.keys(DEFAULT.featureCosts)) normalized.featureCosts[feature] = Math.max(0, Math.floor(Number(next.featureCosts?.[feature])));
  if (!Number.isFinite(normalized.abandonPenaltyPercent) || normalized.abandonPenaltyPercent < 0 || normalized.abandonPenaltyPercent > 100 || !Number.isFinite(normalized.dailyFreeAllowance) || normalized.dailyFreeAllowance < 0 || !Number.isFinite(normalized.customMarginalRatePerMinute) || normalized.customMarginalRatePerMinute < 0 || !Number.isFinite(normalized.customMinimumDurationSeconds) || normalized.customMinimumDurationSeconds < 1) throw new Error("Invalid berry economy settings.");
  normalized.dailyFreeAllowance = Math.floor(normalized.dailyFreeAllowance);
  await fs.writeFile(CONFIG_PATH, `${JSON.stringify({ ...raw, berryEconomy: normalized }, null, 2)}\n`);
  return normalized;
}
async function allowanceRemaining() {
  const gatedFeatures = Object.entries((await config()).featureCosts).filter(([, cost]) => Number(cost) > 0).map(([feature]) => feature);
  if (!gatedFeatures.length) return 0;
  const used = Number(db.prepare(`SELECT COUNT(*) AS count FROM feature_gate_usage WHERE feature IN (${gatedFeatures.map(() => "?").join(",")}) AND entries > 0`).get(...gatedFeatures).count);
  return Math.max(0, gatedFeatures.length - used);
}
async function checkAndChargeGate(feature, screenSessionId = "", subject = "anonymous") {
  const settings = await config(); const cost = Number(settings.featureCosts[feature] || 0);
  if (!cost) return { allowed: true, usedFreeAllowance: false, newBalance: balance() };
  const key = screenSessionId ? `${feature}:${screenSessionId}` : "";
  if (key && admittedSessions.has(key)) return admittedSessions.get(key);
  const safeSubject = String(subject || "anonymous").slice(0, 120);
  const usage = Number(db.prepare("SELECT entries FROM feature_gate_usage WHERE feature = ? AND subject = ?").get(feature, safeSubject)?.entries || 0);
  let result;
  if (usage === 0) {
    db.prepare("INSERT INTO feature_gate_usage (feature, subject, entries, first_used_at) VALUES (?, ?, 1, ?) ON CONFLICT(feature, subject) DO UPDATE SET entries = entries + 1").run(feature, safeSubject, new Date().toISOString());
    result = { allowed: true, usedFreeAllowance: true, newBalance: balance() };
  } else if (balance() >= cost) {
    insertLedger(-cost, "feature_use", feature); result = { allowed: true, usedFreeAllowance: false, newBalance: balance() };
  } else result = { allowed: false, usedFreeAllowance: false, newBalance: balance(), cost };
  if (key && result.allowed) admittedSessions.set(key, result);
  return result;
}
function clearSession(feature, screenSessionId) { if (screenSessionId) admittedSessions.delete(`${feature}:${screenSessionId}`); }

module.exports = { db, DURATIONS, balance, config, saveConfig, allowanceRemaining, checkAndChargeGate, clearSession, insertLedger };
