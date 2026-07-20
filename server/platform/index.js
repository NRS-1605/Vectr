const platforms = {
  linux: require("./linux"),
  win32: require("./windows"),
};

const platform = platforms[process.platform];

if (!platform) {
  throw new Error(`VeCTR desktop control is not supported on ${process.platform}. Supported platforms: Linux and Windows.`);
}

module.exports = platform;
