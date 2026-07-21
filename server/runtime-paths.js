const fs = require("fs");
const os = require("os");
const path = require("path");

const packaged = Boolean(process.pkg);
const sourceRoot = path.join(__dirname, "..");
const dataRoot = packaged ? path.join(os.homedir(), "axon") : __dirname;

if (packaged) fs.mkdirSync(dataRoot, { recursive: true });

function assetPath(...parts) {
  return path.join(sourceRoot, ...parts);
}

function deploymentPath(...parts) {
  return path.join(packaged ? path.dirname(process.execPath) : sourceRoot, ...parts);
}

function dataPath(...parts) {
  return path.join(dataRoot, ...parts);
}

module.exports = {
  packaged,
  assetPath,
  deploymentPath,
  dataPath,
  configPath: dataPath("config.json"),
  databasePath: dataPath("axon-core.sqlite"),
  schedWallStatePath: dataPath("schedwall.json"),
};
