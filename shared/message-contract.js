const MESSAGE_TYPES = Object.freeze(["PING", "PONG", "DATA", "STATUS", "test.ping", "capture.new", "capture.new_from_device", "file.received", "telemetry.update", "telemetry.subscribe", "telemetry.unsubscribe", "macro.subscribe", "macro.unsubscribe", "macro.trigger", "macro.result", "touchpad.subscribe", "touchpad.unsubscribe", "touchpad.move", "touchpad.click", "touchpad.scroll", "clipboard.update", "todos.update", "schedwall.state", "schedwall.scroll", "gate.denied"]);

const MESSAGE_SHAPE = Object.freeze({
  type: "string",
  payload: "object",
  deviceId: "string",
  timestamp: "number",
});

function createMessage(type, payload = {}, deviceId, timestamp = Date.now()) {
  if (!MESSAGE_TYPES.includes(type)) {
    throw new Error(`Unsupported message type: ${type}`);
  }

  return {
    type,
    payload,
    deviceId,
    timestamp,
  };
}

module.exports = {
  MESSAGE_SHAPE,
  MESSAGE_TYPES,
  createMessage,
};
