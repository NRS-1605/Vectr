const WebSocket = require("ws");
const { MESSAGE_TYPES, createMessage } = require("../../shared/message-contract");
const { executeMacro } = require("../routes/macro");
const { input } = require("../platform");
const { saveEntry } = require("../clipboard-history");

function numericDelta(value) {
  const number = Number(value);
  return Number.isFinite(number) ? Math.round(number) : 0;
}

function executeTouchpad(type, payload) {
  if (type === "touchpad.move") {
    const dx = numericDelta(payload.dx);
    const dy = numericDelta(payload.dy);
    if (!dx && !dy) return Promise.resolve();
    return input.move(dx, dy);
  }
  if (type === "touchpad.click") {
    return input.click(payload.button);
  }
  const dy = numericDelta(payload.dy);
  if (!dy) return Promise.resolve();
  return input.scroll(dy);
}

function broadcastMessage(wss, message) {
  const serialized = JSON.stringify(message);
  wss.clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(serialized);
    }
  });
}

function setupWebSocketServer(server, handlers = {}) {
  const wss = new WebSocket.Server({ server, path: "/ws", maxPayload: 64 * 1024 });

  wss.on("connection", (ws, req) => {
    const requestUrl = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);
    const deviceId = requestUrl.searchParams.get("deviceId") || "unknown";
    const subscriptions = new Set();
    // Process one message at a time so a fast first swipe cannot overtake its
    // own subscribe request.
    let messageQueue = Promise.resolve();

    async function handleMessage(rawMessage) {
      let parsed;
      try {
        parsed = JSON.parse(rawMessage.toString());
      } catch (error) {
        console.warn(`[ws] invalid message from ${deviceId}`);
        return;
      }

      const subscriptionMatch = /^(macro|touchpad)\.(subscribe|unsubscribe)$/.exec(parsed?.type || "");
      if (subscriptionMatch) {
        const [, subscription, action] = subscriptionMatch;
        const subscribing = action === "subscribe";
        const changed = subscribing ? !subscriptions.has(subscription) : subscriptions.has(subscription);
        if (subscribing) subscriptions.add(subscription);
        else subscriptions.delete(subscription);
        if (changed && typeof handlers.onSubscriptionChange === "function") {
          handlers.onSubscriptionChange(subscription, subscribing, { deviceId, ws });
        }
        return;
      }

      if (parsed?.type === "macro.trigger") {
        if (!subscriptions.has("macro")) {
          console.warn(`[macro] ignored trigger from unsubscribed client: ${deviceId}`);
          return;
        }
        const buttonId = parsed?.payload?.buttonId;
        const requestId = parsed?.payload?.requestId;
        executeMacro(buttonId, requestId)
          .then((result) => {
            broadcastMessage(wss, createMessage("macro.result", result, "axon-core"));
          })
          .catch((error) => {
            broadcastMessage(wss, createMessage("macro.result", {
            buttonId,
            requestId,
            success: false,
            label: "Unknown macro",
            timestamp: Date.now(),
            error: error.message || "Macro execution failed.",
            }, "axon-core"));
          });
      }

      if (parsed?.type === "capture.new_from_device") {
        const payload = parsed.payload || {};
        if (typeof handlers.saveDeviceCapture === "function") {
          handlers.saveDeviceCapture({ heading: payload.heading, tag: payload.tag, body: payload.body, imageFilename: payload.imageFilename })
            .catch((error) => console.error(`[capture] failed to save device capture: ${error.message}`));
        }
      }

      if (parsed?.type === "clipboard.update") {
        const text = parsed?.payload?.text;
        const source = parsed?.payload?.source === "phone" ? "phone" : "laptop";
        console.log(`[clipboard] WS update: source=${source}, text="${(text||"").substring(0,80)}...", hasWriteHandler=${typeof handlers.writePhoneClipboard}`);
        if (typeof text === "string") {
          try { saveEntry(text, source); } catch (error) { console.error(`[clipboard] failed to save history: ${error.message}`); }
          broadcastMessage(wss, createMessage("clipboard.history", { entry: { text, source, timestamp: new Date().toISOString() } }, "axon-core"));
          if (source === "phone" && typeof handlers.writePhoneClipboard === "function") {
            handlers.writePhoneClipboard(text)
              .catch((error) => console.error(`[clipboard] phone update failed: ${error.message}`));
          }
        }
        return;
      }

      if (["touchpad.move", "touchpad.click", "touchpad.scroll"].includes(parsed?.type)) {
        if (!subscriptions.has("touchpad")) {
          console.warn(`[touchpad] ignored ${parsed.type} from unsubscribed client: ${deviceId}`);
          return;
        }
        executeTouchpad(parsed.type, parsed.payload || {})
          .catch((error) => console.error(`[touchpad] ${parsed.type} failed: ${error.message}`));
        return;
      }

      if (parsed?.type && MESSAGE_TYPES.includes(parsed.type)) {
        const response = {
          ...parsed,
          timestamp: Date.now(),
        };

        broadcastMessage(wss, response);
      }
    }

    ws.on("message", (rawMessage) => {
      messageQueue = messageQueue
        .then(() => handleMessage(rawMessage))
        .catch((error) => console.error(`[ws] message handling failed for ${deviceId}: ${error.message}`));
    });

    ws.on("close", () => {
      subscriptions.forEach((subscription) => handlers.onSubscriptionChange?.(subscription, false, { deviceId, ws }));
    });

    ws.on("error", (error) => {
      console.error(`[ws] error for ${deviceId}:`, error.message);
    });
  });

  return wss;
}

module.exports = { broadcastMessage, setupWebSocketServer };
