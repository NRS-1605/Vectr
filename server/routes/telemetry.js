const express = require("express");
const os = require("os");
const fs = require("fs/promises");
const { execFile } = require("child_process");
const { promisify } = require("util");
const { broadcastMessage } = require("../ws");
const { createMessage } = require("../../shared/message-contract");

const execFileAsync = promisify(execFile);

function cpuTimes() {
  return os.cpus().reduce((total, cpu) => {
    const times = cpu.times;
    return { idle: total.idle + times.idle, total: total.total + Object.values(times).reduce((sum, value) => sum + value, 0) };
  }, { idle: 0, total: 0 });
}

async function cpuTemperature() {
  try {
    const zones = await fs.readdir("/sys/class/thermal", { withFileTypes: true });
    const readings = await Promise.all(zones.filter((entry) => entry.name.startsWith("thermal_zone")).map(async (entry) => {
      const raw = Number((await fs.readFile(`/sys/class/thermal/${entry.name}/temp`, "utf8")).trim());
      return raw > 1000 ? raw / 1000 : raw;
    }));
    const valid = readings.filter((value) => Number.isFinite(value) && value > 0 && value < 150);
    return valid.length ? Math.round(Math.max(...valid) * 10) / 10 : null;
  } catch (_) { return null; }
}

async function gpuStats() {
  try {
    const { stdout } = await execFileAsync("nvidia-smi", ["--query-gpu=utilization.gpu,temperature.gpu,memory.used,memory.total", "--format=csv,noheader,nounits"], { timeout: 1500 });
    const values = stdout.trim().split("\n")[0].split(",").map((value) => Number(value.trim()));
    if (values.length !== 4 || values.some((value) => !Number.isFinite(value))) return null;
    return { usage: values[0], temp: values[1], memory: { used: values[2] * 1024 * 1024, total: values[3] * 1024 * 1024 } };
  } catch (_) { return null; }
}

function createTelemetryService(wss) {
  let previousCpu = cpuTimes();
  let current = { cpu: 0, cpuTemp: null, ram: { used: 0, total: os.totalmem() }, gpu: null };
  let interval;
  let subscriberCount = 0;
  async function collect() {
    const nextCpu = cpuTimes();
    const totalDelta = nextCpu.total - previousCpu.total;
    const idleDelta = nextCpu.idle - previousCpu.idle;
    previousCpu = nextCpu;
    current = {
      cpu: totalDelta > 0 ? Math.round((1 - idleDelta / totalDelta) * 1000) / 10 : 0,
      cpuTemp: await cpuTemperature(),
      ram: { used: os.totalmem() - os.freemem(), total: os.totalmem() },
      gpu: await gpuStats(),
    };
    broadcastMessage(wss, createMessage("telemetry.update", current, "axon-core"));
    return current;
  }
  function start() {
    if (interval) return;
    collect().catch((error) => console.error(`[telemetry] collection failed: ${error.message}`));
    interval = setInterval(() => { collect().catch((error) => console.error(`[telemetry] collection failed: ${error.message}`)); }, 2000);
    interval.unref();
  }

  function stop() {
    if (!interval) return;
    clearInterval(interval);
    interval = undefined;
  }

  function adjustSubscribers(delta) {
    const previousCount = subscriberCount;
    subscriberCount = Math.max(0, subscriberCount + delta);
    if (previousCount === 0 && subscriberCount > 0) start();
    if (previousCount > 0 && subscriberCount === 0) stop();
  }

  return { current: () => current, adjustSubscribers, stop };
}

function createTelemetryRoutes(service) {
  const router = express.Router();
  router.get("/telemetry", async (req, res, next) => {
    try { res.json(service.current()); } catch (error) { next(error); }
  });
  return router;
}

module.exports = { createTelemetryService, createTelemetryRoutes };
