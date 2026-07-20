const { ClipboardSync } = require("./clipboard");
const input = require("./input");
const { startPlatformServices } = require("./startup");

module.exports = { ClipboardSync, input, startPlatformServices, name: "Linux" };
