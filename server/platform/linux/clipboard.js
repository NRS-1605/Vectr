const { execFile, spawn } = require("child_process");
const { promisify } = require("util");

const execFileAsync = promisify(execFile);

class ClipboardSync {
  constructor(onLaptopClipboardChange) {
    this.onLaptopClipboardChange = onLaptopClipboardChange;
    this.lastKnownValue = "";
    this.hasInitialValue = false;
    this.pollInFlight = false;
    this.clipboardAvailable = true;
    this.timer = null;
  }

  start() {
    this.poll();
    this.timer = setInterval(() => this.poll(), 1000);
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  async writeFromPhone(text) {
    const previousValue = this.lastKnownValue;
    const hadInitialValue = this.hasInitialValue;
    this.lastKnownValue = text;
    this.hasInitialValue = true;
    try {
      await writeClipboard(text);
    } catch (error) {
      this.lastKnownValue = previousValue;
      this.hasInitialValue = hadInitialValue;
      throw error;
    }
  }

  async poll() {
    if (this.pollInFlight) return;
    this.pollInFlight = true;
    try {
      const text = await readClipboard();
      if (!this.hasInitialValue) {
        this.lastKnownValue = text;
        this.hasInitialValue = true;
      } else if (text !== this.lastKnownValue) {
        this.lastKnownValue = text;
        this.onLaptopClipboardChange(text);
      }
      if (!this.clipboardAvailable) console.log("[clipboard] wl-clipboard is available");
      this.clipboardAvailable = true;
    } catch (error) {
      if (this.clipboardAvailable) console.error(`[clipboard] watcher unavailable: ${error.message}`);
      this.clipboardAvailable = false;
    } finally {
      this.pollInFlight = false;
    }
  }
}

async function readClipboard() {
  const { stdout } = await execFileAsync("wl-paste", ["--no-newline"], { timeout: 2000, maxBuffer: 1024 * 1024 });
  return stdout;
}

function writeClipboard(text) {
  return new Promise((resolve, reject) => {
    const process = spawn("wl-copy", ["--type", "text/plain"]);
    let stderr = "";
    process.stderr.on("data", (chunk) => { stderr += chunk.toString(); });
    process.on("error", reject);
    process.on("close", (code) => code === 0 ? resolve() : reject(new Error(stderr.trim() || `wl-copy exited with code ${code}`)));
    process.stdin.end(text);
  });
}

module.exports = { ClipboardSync };
