const { run, runDetached } = require("./powershell");

class ClipboardSync {
  constructor(onLaptopClipboardChange) {
    this.onLaptopClipboardChange = onLaptopClipboardChange;
    this.lastKnownValue = "";
    this.hasInitialValue = false;
    this.pollInFlight = false;
    this.timer = null;
  }

  start() { this.poll(); this.timer = setInterval(() => this.poll(), 1000); }
  stop() { if (this.timer) clearInterval(this.timer); this.timer = null; }

  async writeFromPhone(text) {
    const previousValue = this.lastKnownValue;
    this.lastKnownValue = text;
    this.hasInitialValue = true;
    try { await runDetached(`Set-Clipboard -Value ([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('${Buffer.from(text, "utf8").toString("base64")}')))`); }
    catch (error) { this.lastKnownValue = previousValue; throw error; }
  }

  async poll() {
    if (this.pollInFlight) return;
    this.pollInFlight = true;
    try {
      const { stdout } = await run("Get-Clipboard -Raw", { timeout: 2000 });
      const text = stdout.replace(/\r?\n$/, "");
      if (!this.hasInitialValue) { this.lastKnownValue = text; this.hasInitialValue = true; }
      else if (text !== this.lastKnownValue) { this.lastKnownValue = text; this.onLaptopClipboardChange(text); }
    } catch (error) { console.error(`[clipboard] watcher unavailable: ${error.message}`); }
    finally { this.pollInFlight = false; }
  }
}

module.exports = { ClipboardSync };
