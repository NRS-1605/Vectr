const { ClipboardSync } = require("./clipboard");
const input = require("./input");

function startPlatformServices() {
  console.log("[platform] Windows native clipboard and input controls are ready.");
  return () => {};
}

module.exports = { ClipboardSync, input, startPlatformServices, name: "Windows" };
