const { spawn } = require("child_process");

function runYdotool(args) {
  return new Promise((resolve, reject) => {
    const child = spawn("ydotool", args, { env: process.env });
    let stderr = "";
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("close", (code) => code === 0 ? resolve() : reject(new Error(stderr.trim() || `ydotool exited with code ${code}`)));
  });
}

function keypress(command) {
  const trimmed = command.trim();
  const [mode, remainder] = trimmed.startsWith("type ") ? ["type", trimmed.slice(5)] : trimmed.startsWith("key ") ? ["key", trimmed.slice(4)] : ["key", trimmed];
  return runYdotool(mode === "type" ? [mode, remainder] : [mode, ...remainder.split(/\s+/).filter(Boolean)]);
}

function move(dx, dy) { return runYdotool(["mousemove", "--", String(dx), String(dy)]); }
function click(button) { return runYdotool(["click", button === "right" ? "0xC1" : "0xC0"]); }
function scroll(dy) {
  const keyCode = dy > 0 ? "108" : "103";
  const steps = Math.max(1, Math.min(12, Math.round(Math.abs(dy) / 12)));
  return Promise.all(Array.from({ length: steps }, () => runYdotool(["key", `${keyCode}:1`, `${keyCode}:0`])));
}

module.exports = { keypress, move, click, scroll };
