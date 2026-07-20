const { execFile, spawn } = require("child_process");
const { promisify } = require("util");

const execFileAsync = promisify(execFile);
const executable = process.env.SystemRoot ? `${process.env.SystemRoot}\\System32\\WindowsPowerShell\\v1.0\\powershell.exe` : "powershell.exe";

function encoded(script) {
  return Buffer.from(script, "utf16le").toString("base64");
}

function run(script, options = {}) {
  return execFileAsync(executable, ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded(script)], {
    timeout: options.timeout || 3000,
    maxBuffer: 1024 * 1024,
  });
}

function runDetached(script) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded(script)]);
    let stderr = "";
    child.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    child.on("error", reject);
    child.on("close", (code) => code === 0 ? resolve() : reject(new Error(stderr.trim() || `PowerShell exited with code ${code}`)));
  });
}

module.exports = { run, runDetached };
