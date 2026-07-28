const { assetPath, deploymentPath } = require("./runtime-paths");
require("dotenv").config({ path: deploymentPath(".env") });
const express = require("express");
const path = require("path");
const http = require("http");
const os = require("os");
const { broadcastMessage, setupWebSocketServer } = require("./ws");
const { createMessage } = require("../shared/message-contract");
const healthRoutes = require("./routes/health");
const { createCaptureRoutes, createCaptureService } = require("./routes/capture");
const { createMacroRoutes } = require("./routes/macro");
const { createFileRoutes } = require("./routes/files");
const { createLlmRoutes } = require("./routes/llm");
const { createTelemetryService, createTelemetryRoutes } = require("./routes/telemetry");
const { createNewsRoutes } = require("./routes/news");
const { createTodoRoutes } = require("./routes/todos");
const { createSchedWallRoutes } = require("./routes/schedwall");
const { createPointsRoutes } = require("./routes/points");
const { createInventoryRoutes } = require("./routes/inventory");
const { createClipboardRoutes } = require("./routes/clipboard");
const { saveEntry } = require("./clipboard-history");
const { createLectureRoutes } = require("./routes/lecture");
const Bonjour = require("bonjour");
const platform = require("./platform");
const { prepareStorage } = require("./storage");

const app = express();
app.disable("x-powered-by");
const server = http.createServer(app);
const PORT = process.env.PORT || 4101;

function localNetworkUrls(port) {
  const addresses = Object.values(os.networkInterfaces())
    .flat()
    .filter((entry) => entry && entry.family === "IPv4" && !entry.internal)
    .map((entry) => `http://${entry.address}:${port}`);
  return addresses.length ? addresses : [`http://localhost:${port}`];
}

app.use((req, res, next) => {
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("X-Frame-Options", "SAMEORIGIN");
  res.setHeader("Referrer-Policy", "no-referrer");
  res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
  next();
});
app.use(express.json({ limit: "1mb" }));
app.use(express.static(assetPath("public")));
app.use("/api", healthRoutes);
const wsHandlers = {};
const wss = setupWebSocketServer(server, wsHandlers);
const telemetryService = createTelemetryService(wss);
wsHandlers.onSubscriptionChange = (subscription, subscribed) => {
  if (subscription === "telemetry") telemetryService.adjustSubscribers(subscribed ? 1 : -1);
};
const captureService = createCaptureService(wss);
wsHandlers.saveDeviceCapture = captureService.saveCapture;
const clipboardSync = new platform.ClipboardSync((text) => {
  console.log(`[clipboard] laptop change detected: "${text.substring(0, 80)}..."`);
  try { saveEntry(text, "laptop"); } catch (error) { console.error(`[clipboard] failed to save history: ${error.message}`); }
  console.log(`[clipboard] broadcasting to ${wss.clients.size} clients`);
  broadcastMessage(wss, createMessage("clipboard.update", { text, source: "laptop" }, "axon-core"));
  broadcastMessage(wss, createMessage("clipboard.history", { entry: { text, source: "laptop", timestamp: new Date().toISOString() } }, "axon-core"));
});
wsHandlers.writePhoneClipboard = (text) => clipboardSync.writeFromPhone(text);
app.use("/api", createCaptureRoutes(captureService));
app.use("/api", createMacroRoutes());
app.use("/api", createFileRoutes(wss));
app.use("/api", createLlmRoutes());
app.use("/api", createTelemetryRoutes(telemetryService));
app.use("/api", createNewsRoutes());
app.use("/api", createTodoRoutes(wss));
app.use("/api/schedwall", createSchedWallRoutes(wss));
app.use("/api", createPointsRoutes());
app.use("/api", createInventoryRoutes());
app.use("/api", createClipboardRoutes());
app.use("/api", createLectureRoutes());
app.get("/schedwall", (_req, res) => res.redirect("/schedwall/admin"));
app.get("/schedwall/admin", (_req, res) => res.sendFile(assetPath("SchedWall", "views", "admin.html")));
app.get("/schedwall/wallpaper", (_req, res) => res.sendFile(assetPath("SchedWall", "views", "wallpaper.html")));

app.get("/", (req, res) => {
  res.sendFile(assetPath("public", "index.html"));
});

app.use((error, _req, res, _next) => {
  console.error(`[http] ${error.message}`);
  if (res.headersSent) return;
  res.status(error.status || 500).json({ error: error.status && error.status < 500 ? error.message : "Internal server error." });
});

async function start() {
  await prepareStorage();
  server.listen(PORT, () => {
  console.log(`axon-core (${platform.name}) listening on port ${PORT}`);
  localNetworkUrls(PORT).forEach((url) => console.log(`VeCTR address: ${url}`));
  const stopPlatformServices = platform.startPlatformServices();
  server.once("close", stopPlatformServices);
  clipboardSync.start();
  try {
    const bonjour = Bonjour();
    // bonjour exposes multicast-dns errors asynchronously; handle them so a
    // blocked multicast socket never brings down the HTTP/WebSocket server.
    bonjour._server.mdns.on("error", (error) => {
      console.error(`mDNS advertisement failed; manual IP connections remain available: ${error.message}`);
    });
    bonjour._server.mdns.once("ready", () => {
      console.log(`mDNS advertising vectr-core as _vectr._tcp on port ${PORT}`);
    });
    const service = bonjour.publish({ name: "vectr-core", type: "vectr", protocol: "tcp", port: Number(PORT) });
    service.on("error", (error) => {
      console.error(`mDNS advertisement failed; manual IP connections remain available: ${error.message}`);
    });
    server.once("close", () => bonjour.destroy());
  } catch (error) {
    console.error(`mDNS advertisement failed to start; manual IP connections remain available: ${error.message}`);
  }
  });
}

start().catch((error) => {
  console.error(`axon-core could not prepare ~/axon storage: ${error.message}`);
  process.exitCode = 1;
});

server.once("close", () => { clipboardSync.stop(); telemetryService.stop(); });
