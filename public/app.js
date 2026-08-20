(() => {
  const MAX_LOG_ENTRIES = 200;
  const devices = new Map();
  const logEntries = [];
  const captures = [];
  let socket;
  let reconnectTimer;
  let reconnectDelay = 1000;
  let telemetrySubscribed = false;
  let currentMacroPreset = null;

  const elements = {
    indicator: document.querySelector("#connection-indicator"),
    connectionLabel: document.querySelector("#connection-label"),
    socketState: document.querySelector("#socket-state"),
    socketDetail: document.querySelector("#socket-detail"),
    deviceCount: document.querySelector("#device-count"),
    deviceSummary: document.querySelector("#device-summary"),
    deviceList: document.querySelector("#device-list"),
    log: document.querySelector("#message-log"),
    clearLog: document.querySelector("#clear-log"),
    captureForm: document.querySelector("#capture-form"),
    captureText: document.querySelector("#capture-text"),
    captureButton: document.querySelector("#capture-button"),
    captureStatus: document.querySelector("#capture-status"),
    captureList: document.querySelector("#capture-list"),
    macroList: document.querySelector("#macro-list"),
    macroStatus: document.querySelector("#macro-status"),
    saveMacros: document.querySelector("#save-macros"),
    macroPresetSelect: document.querySelector("#macro-preset-select"),
    addMacroPreset: document.querySelector("#add-macro-preset"),
    renameMacroPreset: document.querySelector("#rename-macro-preset"),
    deleteMacroPreset: document.querySelector("#delete-macro-preset"),
    feedList: document.querySelector("#feed-list"),
    addFeed: document.querySelector("#add-feed"),
    saveFeeds: document.querySelector("#save-feeds"),
    feedStatus: document.querySelector("#feed-status"),
    fileNotification: document.querySelector("#file-notification"),
    llmMode: document.querySelector("#llm-mode"),
    llmLocalModel: document.querySelector("#llm-local-model"),
    llmCloudModel: document.querySelector("#llm-cloud-model"),
    saveLlmConfig: document.querySelector("#save-llm-config"),
    llmConfigStatus: document.querySelector("#llm-config-status"),
    llmTestPrompt: document.querySelector("#llm-test-prompt"),
    runLlmTest: document.querySelector("#run-llm-test"),
    llmTestStatus: document.querySelector("#llm-test-status"),
    llmTestResult: document.querySelector("#llm-test-result"),
    telemetryCpuChart: document.querySelector("#telemetry-cpu-chart"),
    telemetryRamChart: document.querySelector("#telemetry-ram-chart"),
    telemetryCpuTempGauge: document.querySelector("#telemetry-cpu-temp-gauge"),
    telemetryCpuTempLabel: document.querySelector("#telemetry-cpu-temp-label"),
    telemetryGpuCard: document.querySelector("#telemetry-gpu-card"),
    telemetryGpuChart: document.querySelector("#telemetry-gpu-chart"),
    telemetryGpuTempGauge: document.querySelector("#telemetry-gpu-temp-gauge"),
    telemetryGpuTempLabel: document.querySelector("#telemetry-gpu-temp-label"),
    todoList: document.querySelector("#todo-list"), todoForm: document.querySelector("#todo-form"), todoInput: document.querySelector("#todo-input"), clearTodos: document.querySelector("#clear-todos"),
    clipboardList: document.querySelector("#clipboard-list"), clearClipboard: document.querySelector("#clear-clipboard-history"),
  };

  function socketUrl() {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/ws?deviceId=admin-console`;
  }

  function telemetryTabIsVisible() {
    return document.querySelector("#telemetry-tab")?.classList.contains("is-active") && !document.hidden;
  }

  function syncTelemetrySubscription() {
    const shouldSubscribe = telemetryTabIsVisible();
    if (!socket || socket.readyState !== WebSocket.OPEN || shouldSubscribe === telemetrySubscribed) return;
    socket.send(JSON.stringify({
      type: shouldSubscribe ? "telemetry.subscribe" : "telemetry.unsubscribe",
      payload: {},
      deviceId: "admin-console",
      timestamp: Date.now(),
    }));
    telemetrySubscribed = shouldSubscribe;
  }

  function setConnectionState(state, detail) {
    const connected = state === "Connected";
    elements.connectionLabel.textContent = state;
    elements.socketState.textContent = state;
    elements.socketDetail.textContent = detail;
    elements.indicator.classList.toggle("status-dot--online", connected);
    elements.indicator.classList.toggle("status-dot--offline", !connected);
  }

  function formatTime(timestamp) {
    const date = new Date(timestamp);
    return Number.isNaN(date.getTime()) ? "Unknown time" : date.toLocaleTimeString();
  }

  function compactJson(value) {
    try { return JSON.stringify(value); } catch (_) { return String(value); }
  }

  const telemetryHistory = { cpu: [], ram: [], gpu: [] };
  let telemetryCharts;
  const chartColor = "#79a7ff";
  function temperatureColor(value) { return value >= 85 ? "#f27986" : value >= 70 ? "#f0b35f" : chartColor; }
  function makeLine(canvas, label) {
    return new Chart(canvas, { type: "line", data: { labels: [], datasets: [{ label, data: [], borderColor: chartColor, backgroundColor: "rgba(121,167,255,.16)", fill: true, tension: .3, pointRadius: 0, borderWidth: 2 }] }, options: { animation: false, maintainAspectRatio: false, plugins: { legend: { labels: { color: "#949ba8", font: { family: "JetBrains Mono" } } } }, scales: { x: { ticks: { color: "#949ba8", maxTicksLimit: 6, font: { family: "JetBrains Mono" } }, grid: { color: "#2c3039" } }, y: { min: 0, max: 100, ticks: { color: "#949ba8", callback: (v) => `${v}%`, font: { family: "JetBrains Mono" } }, grid: { color: "#2c3039" } } } } });
  }
  function makeGauge(canvas) {
    return new Chart(canvas, { type: "doughnut", data: { datasets: [{ data: [0, 100], backgroundColor: [chartColor, "#2c3039"], borderWidth: 0, circumference: 270, rotation: 225 }] }, options: { animation: false, cutout: "76%", plugins: { legend: { display: false }, tooltip: { enabled: false } } } });
  }
  function ensureTelemetryCharts() {
    if (telemetryCharts || !window.Chart) return;
    telemetryCharts = { cpu: makeLine(elements.telemetryCpuChart, "CPU"), ram: makeLine(elements.telemetryRamChart, "RAM"), cpuTemp: makeGauge(elements.telemetryCpuTempGauge), gpu: makeLine(elements.telemetryGpuChart, "GPU"), gpuTemp: makeGauge(elements.telemetryGpuTempGauge) };
  }
  function updateHistory(name, value, timestamp) {
    const series = telemetryHistory[name];
    series.push({ timestamp, value });
    while (series[0] && series[0].timestamp < timestamp - 60_000) series.shift();
    const chart = telemetryCharts[name];
    chart.data.labels = series.map((point) => new Date(point.timestamp).toLocaleTimeString());
    chart.data.datasets[0].data = series.map((point) => point.value);
    chart.update("none");
  }
  function updateGauge(chart, label, temp) {
    if (!Number.isFinite(temp)) {
      chart.data.datasets[0].data = [0, 100];
      chart.data.datasets[0].backgroundColor[0] = chartColor;
      chart.update("none");
      label.textContent = "—";
      return;
    }
    chart.data.datasets[0].data = [Math.min(100, temp), Math.max(0, 100 - temp)];
    chart.data.datasets[0].backgroundColor[0] = temperatureColor(temp);
    chart.update("none");
    label.textContent = `${temp}°C`;
  }
  function renderTelemetry(data) {
    if (!data) return;
    ensureTelemetryCharts();
    if (!telemetryCharts) return;
    const timestamp = Date.now();
    updateHistory("cpu", Number(data.cpu) || 0, timestamp);
    updateHistory("ram", data.ram?.total ? (data.ram.used / data.ram.total * 100) : 0, timestamp);
    updateGauge(telemetryCharts.cpuTemp, elements.telemetryCpuTempLabel, data.cpuTemp == null ? NaN : Number(data.cpuTemp));
    elements.telemetryGpuCard.hidden = !data.gpu;
    if (data.gpu) {
      updateHistory("gpu", Number(data.gpu.usage) || 0, timestamp);
      updateGauge(telemetryCharts.gpuTemp, elements.telemetryGpuTempLabel, data.gpu.temp == null ? NaN : Number(data.gpu.temp));
    }
  }

  async function loadTelemetry() {
    try { const response = await fetch("/api/telemetry"); if (response.ok) renderTelemetry(await response.json()); } catch (_) { /* Live updates will retry. */ }
  }

  function renderDevices() {
    const entries = [...devices.values()].sort((a, b) => b.timestamp - a.timestamp);
    elements.deviceCount.textContent = entries.length;
    elements.deviceSummary.textContent = entries.length === 1 ? "1 device reporting" : `${entries.length} devices reporting`;
    elements.deviceList.replaceChildren();

    if (!entries.length) {
      const empty = document.createElement("p");
      empty.className = "empty-state";
      empty.textContent = "No device messages received yet.";
      elements.deviceList.append(empty);
      return;
    }

    entries.forEach((device) => {
      const row = document.createElement("article");
      row.className = "device-row";
      const text = document.createElement("div");
      const id = document.createElement("span");
      id.className = "device-id";
      id.textContent = device.deviceId;
      const message = document.createElement("span");
      message.className = "device-message";
      message.textContent = `${device.type} ${compactJson(device.payload)}`;
      const time = document.createElement("time");
      time.className = "device-time";
      time.textContent = formatTime(device.timestamp);
      text.append(id, message);
      row.append(text, time);
      elements.deviceList.append(row);
    });
  }

  function renderLog() {
    elements.log.replaceChildren();
    if (!logEntries.length) {
      const empty = document.createElement("p");
      empty.className = "empty-state";
      empty.textContent = "Waiting for WebSocket messages…";
      elements.log.append(empty);
      return;
    }
    logEntries.forEach((entry) => {
      const row = document.createElement("div");
      row.className = "log-entry";
      const time = document.createElement("time");
      time.className = "log-time";
      time.textContent = formatTime(entry.timestamp);
      const type = document.createElement("span");
      type.className = "log-type";
      type.textContent = entry.type || "EVENT";
      const content = document.createElement("span");
      content.className = "log-content";
      content.textContent = compactJson(entry);
      row.append(time, type, content);
      elements.log.append(row);
    });
    elements.log.scrollTop = elements.log.scrollHeight;
  }

  function renderCaptures() {
    elements.captureList.replaceChildren();
    if (!captures.length) {
      const empty = document.createElement("p");
      empty.className = "empty-state";
      empty.textContent = "Waiting for new captures…";
      elements.captureList.append(empty);
      return;
    }
    captures.forEach((capture) => {
      const row = document.createElement("article");
      row.className = "capture-row";
      const content = document.createElement("div");
      const filename = document.createElement("span");
      filename.className = "capture-filename";
      filename.textContent = capture.filename;
      const preview = document.createElement("span");
      preview.className = "capture-preview";
      preview.textContent = capture.preview || "(empty capture)";
      content.append(filename, preview);
      row.append(content);
      elements.captureList.append(row);
    });
  }

  function recordCapture(payload) {
    if (!payload || typeof payload.filename !== "string") return;
    if (captures.some((capture) => capture.filename === payload.filename)) return;
    captures.unshift(payload);
    renderCaptures();
  }

  async function loadCaptures() {
    try {
      const response = await fetch("/api/space/notes");
      if (!response.ok) throw new Error("Could not load captures.");
      const savedCaptures = await response.json();
      if (!Array.isArray(savedCaptures)) throw new Error("Invalid capture list.");
      savedCaptures.forEach((capture) => {
        if (capture && typeof capture.filename === "string" && !captures.some((item) => item.filename === capture.filename)) {
          captures.push(capture);
        }
      });
      renderCaptures();
    } catch (error) {
      elements.captureList.replaceChildren();
      const message = document.createElement("p");
      message.className = "empty-state";
      message.textContent = "Could not load saved captures.";
      elements.captureList.append(message);
    }
  }

  function renderMacros(macros) {
    elements.macroList.replaceChildren();
    macros.sort((a, b) => a.id - b.id).forEach((macro) => {
      const row = document.createElement("div");
      row.className = "macro-row";
      row.dataset.id = macro.id;
      const number = document.createElement("span");
      number.className = "macro-number";
      number.textContent = String(macro.id).padStart(2, "0");
      const label = document.createElement("input");
      label.className = "macro-input macro-label";
      label.value = macro.label;
      label.placeholder = "Label";
      label.setAttribute("aria-label", `Macro ${macro.id} label`);
      const type = document.createElement("select");
      type.className = "macro-select";
      type.setAttribute("aria-label", `Macro ${macro.id} type`);
      ["shell", "keypress"].forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        option.selected = macro.type === value;
        type.append(option);
      });
      const command = document.createElement("input");
      command.className = "macro-input macro-command";
      command.value = macro.command;
      command.placeholder = "Command";
      command.setAttribute("aria-label", `Macro ${macro.id} command`);
      row.append(number, label, type, command);
      elements.macroList.append(row);
    });
  }

  function populatePresetSelect(presets, active) {
    elements.macroPresetSelect.replaceChildren();
    presets.forEach((name) => {
      const option = document.createElement("option");
      option.value = name;
      option.textContent = name === active ? `${name} (active)` : name;
      elements.macroPresetSelect.append(option);
    });
    elements.macroPresetSelect.value = active;
  }

  function setMacroStatus(message, isError = false) {
    elements.macroStatus.classList.toggle("is-error", isError);
    elements.macroStatus.textContent = message;
  }

  async function loadMacros(presetName) {
    try {
      const query = presetName ? `?preset=${encodeURIComponent(presetName)}` : "";
      const response = await fetch(`/api/macro/config${query}`);
      if (!response.ok) throw new Error("Could not load macro configuration.");
      const macros = await response.json();
      renderMacros(macros);
    } catch (_) {
      elements.macroList.replaceChildren();
      const message = document.createElement("p");
      message.className = "empty-state";
      message.textContent = "Could not load macro configuration.";
      elements.macroList.append(message);
    }
  }

  async function loadMacroPresets() {
    try {
      const response = await fetch("/api/macro/presets");
      if (!response.ok) throw new Error("Could not load macro presets.");
      const { presets, active } = await response.json();
      currentMacroPreset = active;
      populatePresetSelect(presets, active);
      await loadMacros(active);
    } catch (_) {
      elements.macroList.replaceChildren();
      const message = document.createElement("p");
      message.className = "empty-state";
      message.textContent = "Could not load macro configuration.";
      elements.macroList.append(message);
    }
  }

  async function activateMacroPreset(name) {
    const response = await fetch(`/api/macro/presets/${encodeURIComponent(name)}/activate`, { method: "POST" });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.error || "Could not activate preset.");
    return body;
  }

  async function loadLlmConfig() {
    try {
      const response = await fetch("/api/llm/config");
      if (!response.ok) throw new Error("Could not load LLM settings.");
      const config = await response.json();
      elements.llmMode.value = config.defaultMode;
      elements.llmLocalModel.value = config.localModel;
      elements.llmCloudModel.value = config.cloudModel;
    } catch (error) { elements.llmConfigStatus.textContent = error.message; elements.llmConfigStatus.classList.add("is-error"); }
  }

  function renderTodos(items) {
    elements.todoList.replaceChildren();
    if (!items.length) { const empty = document.createElement("p"); empty.className = "empty-state"; empty.textContent = "Nothing to do."; elements.todoList.append(empty); return; }
    items.forEach((todo) => {
      const row = document.createElement("div"); row.className = `todo-row${todo.checked ? " is-checked" : ""}`;
      const check = document.createElement("input"); check.type = "checkbox"; check.checked = todo.checked; check.addEventListener("change", () => todoRequest(`/api/todos/${todo.id}`, "PATCH", { checked: check.checked }));
      const text = document.createElement("span"); text.textContent = todo.text;
      const remove = document.createElement("button"); remove.className = "text-button"; remove.textContent = "×"; remove.setAttribute("aria-label", `Delete ${todo.text}`); remove.addEventListener("click", () => todoRequest(`/api/todos/${todo.id}`, "DELETE"));
      row.append(check, text, remove); elements.todoList.append(row);
    });
  }
  async function loadTodos() { try { const response = await fetch("/api/todos"); if (!response.ok) throw new Error(); renderTodos(await response.json()); } catch (_) { elements.todoList.textContent = "Could not load todos."; } }
  async function todoRequest(url, method, body) { const response = await fetch(url, { method, headers: body ? { "Content-Type": "application/json" } : undefined, body: body ? JSON.stringify(body) : undefined }); if (!response.ok) await loadTodos(); }

  function formatClipboardTime(timestamp) {
    const date = new Date(timestamp);
    return Number.isNaN(date.getTime()) ? "Unknown" : date.toLocaleString();
  }

  function renderClipboard(entries) {
    elements.clipboardList.replaceChildren();
    if (!entries || !entries.length) {
      const empty = document.createElement("p");
      empty.className = "empty-state";
      empty.textContent = "No clipboard entries yet.";
      elements.clipboardList.append(empty);
      return;
    }
    entries.forEach((entry) => {
      const card = document.createElement("article");
      card.className = "clipboard-card";
      card.setAttribute("role", "button");
      card.setAttribute("tabindex", "0");
      const source = document.createElement("span");
      source.className = `clipboard-source clipboard-source--${entry.source}`;
      source.textContent = entry.source === "laptop" ? "LAPTOP" : "MOBILE";
      const text = document.createElement("p");
      text.className = "clipboard-text";
      text.textContent = entry.text;
      const time = document.createElement("time");
      time.className = "clipboard-time";
      time.textContent = formatClipboardTime(entry.timestamp);
      card.append(source, text, time);
      card.addEventListener("click", () => copyClipboardEntry(entry));
      card.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); copyClipboardEntry(entry); } });
      elements.clipboardList.append(card);
    });
  }

  function copyClipboardEntry(entry) {
    navigator.clipboard.writeText(entry.text).then(() => {
      const toast = document.createElement("div");
      toast.className = "clipboard-toast";
      toast.textContent = "Copied to clipboard";
      document.body.append(toast);
      window.setTimeout(() => toast.remove(), 1500);
    }).catch(() => {
      const toast = document.createElement("div");
      toast.className = "clipboard-toast clipboard-toast--error";
      toast.textContent = "Could not copy";
      document.body.append(toast);
      window.setTimeout(() => toast.remove(), 1500);
    });
  }

  async function loadClipboard() {
    try {
      const response = await fetch("/api/clipboard/history");
      if (!response.ok) throw new Error("Could not load clipboard history.");
      renderClipboard(await response.json());
    } catch (_) {
      elements.clipboardList.replaceChildren();
      const message = document.createElement("p");
      message.className = "empty-state";
      message.textContent = "Could not load clipboard history.";
      elements.clipboardList.append(message);
    }
  }

  function addFeedRow(url = "") {
    const row = document.createElement("div");
    row.className = "feed-row";
    const input = document.createElement("input");
    input.className = "feed-input";
    input.type = "url";
    input.placeholder = "https://example.com/feed.xml";
    input.value = url;
    input.setAttribute("aria-label", "RSS feed URL");
    const remove = document.createElement("button");
    remove.className = "secondary-button";
    remove.type = "button";
    remove.textContent = "Remove";
    remove.addEventListener("click", () => row.remove());
    row.append(input, remove);
    elements.feedList.append(row);
  }

  async function loadFeeds() {
    try {
      const response = await fetch("/api/news/feeds");
      if (!response.ok) throw new Error("Could not load news feeds.");
      const feeds = await response.json();
      elements.feedList.replaceChildren();
      feeds.forEach((feed) => addFeedRow(feed));
      if (!feeds.length) addFeedRow();
    } catch (error) {
      elements.feedStatus.classList.add("is-error");
      elements.feedStatus.textContent = error.message;
    }
  }

  function recordMessage(message) {
    const timestamp = typeof message.timestamp === "number" ? message.timestamp : Date.now();
    const normalized = { ...message, timestamp };
    logEntries.push(normalized);
    if (logEntries.length > MAX_LOG_ENTRIES) logEntries.shift();
    if (message.deviceId && message.deviceId !== "admin-console") {
      devices.set(message.deviceId, normalized);
      renderDevices();
    }
    if (message.type === "capture.new") recordCapture(message.payload);
    if (message.type === "file.received" && typeof message.payload?.filename === "string") showFileNotification(message.payload.filename);
    if (message.type === "telemetry.update") renderTelemetry(message.payload);
    if (message.type === "todos.update" && Array.isArray(message.payload?.items)) renderTodos(message.payload.items);
    if (message.type === "clipboard.history" && message.payload?.entry) {
      const tab = document.querySelector("#clipboard-tab");
      if (tab?.classList.contains("is-active")) {
        loadClipboard();
      }
    }
    renderLog();
  }

  let fileNotificationTimer;
  function showFileNotification(filename) {
    elements.fileNotification.textContent = `File received from phone: ${filename}`;
    elements.fileNotification.hidden = false;
    clearTimeout(fileNotificationTimer);
    fileNotificationTimer = window.setTimeout(() => { elements.fileNotification.hidden = true; }, 6000);
  }

  function connect() {
    clearTimeout(reconnectTimer);
    setConnectionState("Connecting", "Opening a live connection");
    socket = new WebSocket(socketUrl());
    socket.addEventListener("open", () => {
      reconnectDelay = 1000;
      setConnectionState("Connected", "Listening on /ws");
      syncTelemetrySubscription();
    });
    socket.addEventListener("message", (event) => {
      try {
        recordMessage(JSON.parse(event.data));
      } catch (_) {
        recordMessage({ type: "RAW", payload: event.data, timestamp: Date.now() });
      }
    });
    socket.addEventListener("close", () => {
      telemetrySubscribed = false;
      setConnectionState("Reconnecting", `Retrying in ${Math.round(reconnectDelay / 1000)}s`);
      reconnectTimer = window.setTimeout(connect, reconnectDelay);
      reconnectDelay = Math.min(reconnectDelay * 2, 10000);
    });
    socket.addEventListener("error", () => socket.close());
  }

  document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      document.querySelectorAll(".tab").forEach((item) => {
        const active = item === tab;
        item.classList.toggle("is-active", active);
        item.setAttribute("aria-selected", String(active));
      });
      document.querySelectorAll(".tab-panel").forEach((panel) => {
        panel.hidden = panel.id !== `${tab.dataset.tab}-panel`;
      });
      if (tab.dataset.tab === "telemetry") {
        // Charts are initialized while this panel is hidden; resize once it has
        // a real layout box so Chart.js can paint the canvases correctly.
        window.requestAnimationFrame(() => {
          Object.values(telemetryCharts || {}).forEach((chart) => chart.resize());
        });
      }
      syncTelemetrySubscription();
    });
  });

  document.addEventListener("visibilitychange", syncTelemetrySubscription);
  window.addEventListener("pagehide", () => {
    if (telemetrySubscribed && socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: "telemetry.unsubscribe", payload: {}, deviceId: "admin-console", timestamp: Date.now() }));
    }
    telemetrySubscribed = false;
  });

  elements.clearLog.addEventListener("click", () => {
    logEntries.length = 0;
    renderLog();
  });

  elements.captureForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const text = elements.captureText.value;
    elements.captureButton.disabled = true;
    elements.captureStatus.classList.remove("is-error");
    elements.captureStatus.textContent = "Saving…";
    try {
      const response = await fetch("/api/capture", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ text, source: "admin-console" }),
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.error || "Could not save capture.");
      }
      elements.captureText.value = "";
      elements.captureStatus.textContent = "Captured. Waiting for live update…";
    } catch (error) {
      elements.captureStatus.classList.add("is-error");
      elements.captureStatus.textContent = error.message || "Could not save capture.";
    } finally {
      elements.captureButton.disabled = false;
    }
  });

  elements.saveMacros.addEventListener("click", async () => {
    const macros = [...elements.macroList.querySelectorAll(".macro-row")].map((row) => ({
      id: Number(row.dataset.id),
      label: row.querySelector(".macro-label").value,
      type: row.querySelector(".macro-select").value,
      command: row.querySelector(".macro-command").value,
    }));
    elements.saveMacros.disabled = true;
    setMacroStatus("Saving…");
    try {
      const response = await fetch("/api/macro/config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ macros, preset: currentMacroPreset || undefined }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Could not save macros.");
      setMacroStatus("Saved");
      window.setTimeout(() => { setMacroStatus(""); }, 2200);
    } catch (error) {
      setMacroStatus(error.message || "Could not save macros.", true);
    } finally {
      elements.saveMacros.disabled = false;
    }
  });

  elements.macroPresetSelect.addEventListener("change", async () => {
    const name = elements.macroPresetSelect.value;
    if (!name || name === currentMacroPreset) return;
    try {
      const body = await activateMacroPreset(name);
      currentMacroPreset = body.active;
      populatePresetSelect(body.presets, body.active);
      await loadMacros(body.active);
    } catch (error) {
      elements.macroPresetSelect.value = currentMacroPreset;
      setMacroStatus(error.message || "Could not activate preset.", true);
    }
  });

  elements.addMacroPreset.addEventListener("click", async () => {
    const name = prompt("New preset name:");
    if (!name || !name.trim()) return;
    try {
      const response = await fetch("/api/macro/presets", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: name.trim() }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Could not create preset.");
      currentMacroPreset = body.active;
      populatePresetSelect(body.presets, body.active);
      await loadMacros(body.active);
    } catch (error) {
      setMacroStatus(error.message || "Could not create preset.", true);
    }
  });

  elements.renameMacroPreset.addEventListener("click", async () => {
    const oldName = elements.macroPresetSelect.value;
    if (!oldName) return;
    const name = prompt(`Rename "${oldName}" to:`, oldName);
    if (!name || !name.trim() || name.trim() === oldName) return;
    try {
      const response = await fetch(`/api/macro/presets/${encodeURIComponent(oldName)}/rename`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: name.trim() }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Could not rename preset.");
      currentMacroPreset = body.active;
      populatePresetSelect(body.presets, body.active);
      await loadMacros(body.active);
    } catch (error) {
      setMacroStatus(error.message || "Could not rename preset.", true);
    }
  });

  elements.deleteMacroPreset.addEventListener("click", async () => {
    const name = elements.macroPresetSelect.value;
    if (!name || !confirm(`Delete preset "${name}"?`)) return;
    try {
      const response = await fetch(`/api/macro/presets/${encodeURIComponent(name)}`, { method: "DELETE" });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Could not delete preset.");
      currentMacroPreset = body.active;
      populatePresetSelect(body.presets, body.active);
      await loadMacros(body.active);
    } catch (error) {
      setMacroStatus(error.message || "Could not delete preset.", true);
    }
  });

  elements.saveLlmConfig.addEventListener("click", async () => {
    elements.saveLlmConfig.disabled = true;
    elements.llmConfigStatus.classList.remove("is-error");
    try {
      const response = await fetch("/api/llm/config", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ defaultMode: elements.llmMode.value, localModel: elements.llmLocalModel.value, cloudModel: elements.llmCloudModel.value }) });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Could not save LLM settings.");
      elements.llmConfigStatus.textContent = "Saved";
      window.setTimeout(() => { elements.llmConfigStatus.textContent = ""; }, 2200);
    } catch (error) { elements.llmConfigStatus.classList.add("is-error"); elements.llmConfigStatus.textContent = error.message; }
    finally { elements.saveLlmConfig.disabled = false; }
  });

  elements.todoForm.addEventListener("submit", async (event) => { event.preventDefault(); const text = elements.todoInput.value.trim(); if (!text) return; await todoRequest("/api/todos", "POST", { text }); elements.todoInput.value = ""; });
  elements.clearTodos.addEventListener("click", () => todoRequest("/api/todos/clear", "POST"));
  elements.clearClipboard.addEventListener("click", async () => {
    if (!confirm("Clear all clipboard history?")) return;
    await fetch("/api/clipboard/clear", { method: "POST" });
    loadClipboard();
  });

  elements.addFeed.addEventListener("click", () => addFeedRow());
  elements.saveFeeds.addEventListener("click", async () => {
    const feeds = [...elements.feedList.querySelectorAll(".feed-input")].map((input) => input.value.trim()).filter(Boolean);
    elements.saveFeeds.disabled = true;
    elements.feedStatus.classList.remove("is-error");
    elements.feedStatus.textContent = "Saving…";
    try {
      const response = await fetch("/api/news/feeds", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ feeds }) });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "Could not save news feeds.");
      elements.feedStatus.textContent = "Saved";
      window.setTimeout(() => { elements.feedStatus.textContent = ""; }, 2200);
    } catch (error) {
      elements.feedStatus.classList.add("is-error");
      elements.feedStatus.textContent = error.message || "Could not save news feeds.";
    } finally { elements.saveFeeds.disabled = false; }
  });

  elements.runLlmTest.addEventListener("click", async () => {
    const prompt = elements.llmTestPrompt.value.trim();
    if (!prompt) return;
    elements.runLlmTest.disabled = true;
    elements.llmTestStatus.classList.remove("is-error");
    elements.llmTestStatus.textContent = "Running…";
    elements.llmTestResult.hidden = true;
    try {
      const response = await fetch("/api/llm/query", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ prompt, mode: elements.llmMode.value }) });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.error || "LLM request failed.");
      elements.llmTestResult.textContent = body.result;
      elements.llmTestResult.hidden = false;
      elements.llmTestStatus.textContent = "Done";
    } catch (error) { elements.llmTestStatus.classList.add("is-error"); elements.llmTestStatus.textContent = error.message; }
    finally { elements.runLlmTest.disabled = false; }
  });

  document.querySelectorAll(".tab").forEach((t) => t.addEventListener("click", () => {
    if (t.id === "clipboard-tab") loadClipboard();
  }));

  Promise.all([loadCaptures(), loadMacroPresets(), loadLlmConfig(), loadFeeds(), loadTodos()]).finally(connect);
})();
