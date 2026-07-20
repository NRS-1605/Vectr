const { spawn } = require("child_process");
const os = require("os");
const path = require("path");

function startPlatformServices() {
  if (process.env.VECTR_MANAGE_YDOTOOL === "false") return () => {};
  const socket = process.env.YDOTOOL_SOCKET || path.join(os.tmpdir(), `vectr-ydotool-${process.getuid?.() || "user"}.socket`);
  process.env.YDOTOOL_SOCKET = socket;
  const daemon = spawn("ydotoold", ["--socket-path", socket], { stdio: ["ignore", "pipe", "pipe"] });
  let reportedError = false;
  daemon.on("error", () => {
    if (!reportedError) console.warn("[input] ydotoold was not found. Install ydotool once to enable Linux touchpad and keypress control.");
    reportedError = true;
  });
  daemon.stderr.on("data", (chunk) => {
    const message = chunk.toString().trim();
    if (message) console.warn(`[input] ydotoold: ${message}`);
  });
  daemon.unref();
  console.log(`[input] starting managed ydotoold (${socket})`);
  return () => { if (!daemon.killed) daemon.kill(); };
}

module.exports = { startPlatformServices };
