const STORAGE_KEY = "softmusic.desktopPanel.connection";

const state = {
  connected: false,
  isPlaying: false,
  nowPlaying: null,
  progressPercent: 0,
  positionMs: 0,
  durationMs: 0,
  playbackMode: "Ordered",
  queue: [],
  playlists: [],
};

let pollTimer = null;

const elements = {
  connectView: document.querySelector("#connectView"),
  playerView: document.querySelector("#playerView"),
  connectForm: document.querySelector("#connectForm"),
  connectFeedback: document.querySelector("#connectFeedback"),
  hostInput: document.querySelector("#hostInput"),
  tokenInput: document.querySelector("#tokenInput"),
  connectButton: document.querySelector("#connectButton"),
  disconnectButton: document.querySelector("#disconnectButton"),
  connectionStatus: document.querySelector("#connectionStatus"),
  statusPill: document.querySelector("#statusPill"),
  modeLabel: document.querySelector("#modeLabel"),
  artwork: document.querySelector("#artwork"),
  songTitle: document.querySelector("#songTitle"),
  songMeta: document.querySelector("#songMeta"),
  elapsedTime: document.querySelector("#elapsedTime"),
  durationTime: document.querySelector("#durationTime"),
  progressFill: document.querySelector("#progressFill"),
  playPauseButton: document.querySelector("#playPauseButton"),
  queueCount: document.querySelector("#queueCount"),
  queueList: document.querySelector("#queueList"),
  playlistCount: document.querySelector("#playlistCount"),
  playlistList: document.querySelector("#playlistList"),
};

function loadConnectionSettings() {
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return;

  try {
    const settings = JSON.parse(raw);
    elements.hostInput.value = settings.host || "";
    elements.tokenInput.value = settings.token || "";
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
  }
}

function applyUrlConnectionSettings() {
  const params = new URLSearchParams(window.location.search);
  const token = params.get("token");
  if (window.location.host && token) {
    elements.hostInput.value = window.location.host;
  }
  if (token) {
    elements.tokenInput.value = token;
  }
}

function showConnect(message) {
  state.connected = false;
  elements.connectView.hidden = false;
  elements.playerView.hidden = true;
  setConnectFeedback(message || initialFeedbackMessage(), "neutral");
}

function showPlayer() {
  elements.connectView.hidden = true;
  elements.playerView.hidden = false;
  setConnectionMessage("Conectado al telefono.", true);
}

function initialFeedbackMessage() {
  const settings = currentSettings(false);
  if (settings.host && settings.token) {
    return "Datos detectados. Pulsa Conectar para abrir el reproductor.";
  }
  return "Esperando datos del telefono.";
}

function setConnectFeedback(message, tone = "neutral") {
  elements.connectFeedback.textContent = message;
  elements.connectFeedback.dataset.tone = tone;
}

function setConnectionMessage(message, ready) {
  elements.connectionStatus.textContent = message;
  elements.statusPill.textContent = ready ? "Conectado" : "Sin conexion";
  elements.statusPill.classList.toggle("ready", ready);
}

async function connect() {
  const settings = currentSettings(true);
  if (!settings.host || !settings.token) {
    setConnectFeedback("Completa host y token antes de conectar.", "error");
    return;
  }

  setConnectButtonBusy(true);
  setConnectFeedback("Conectando con Android...", "neutral");

  try {
    await fetchState();
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    state.connected = true;
    showPlayer();
    startPolling();
  } catch {
    state.connected = false;
    stopPolling();
    setConnectFeedback("No se pudo conectar. Revisa host, token y que ambos equipos esten en la misma red.", "error");
  } finally {
    setConnectButtonBusy(false);
  }
}

function disconnect() {
  stopPolling();
  showConnect("Sesion cerrada en este navegador. Puedes conectar de nuevo cuando quieras.");
}

function setConnectButtonBusy(busy) {
  elements.connectButton.disabled = busy;
  elements.connectButton.textContent = busy ? "Conectando..." : "Conectar";
}

function startPolling() {
  stopPolling();
  pollTimer = window.setInterval(() => {
    fetchState().catch(() => {
      stopPolling();
      setConnectionMessage("Conexion perdida.", false);
      showConnect("Conexion perdida. Vuelve a conectar desde esta pantalla.");
    });
  }, 1000);
}

function stopPolling() {
  window.clearInterval(pollTimer);
  pollTimer = null;
}

async function fetchState() {
  const response = await fetch(apiUrl("/api/state"), { cache: "no-store" });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);

  const data = await response.json();
  state.connected = true;
  state.isPlaying = Boolean(data.isPlaying);
  state.nowPlaying = data.currentSong;
  state.progressPercent = Number(data.progressPercent || 0);
  state.positionMs = Number(data.positionMs || 0);
  state.durationMs = Number(data.durationMs || state.nowPlaying?.durationMs || 0);
  state.playbackMode = data.playbackMode || "Ordered";
  state.queue = Array.isArray(data.queue) ? data.queue : [];
  state.playlists = Array.isArray(data.playlists) ? data.playlists : [];
  renderPlayer();
  renderLists();
}

function sendCommand(command) {
  if (!state.connected) {
    showConnect("Conecta el telefono antes de enviar comandos.");
    return;
  }

  fetch(apiUrl("/api/command"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ command }),
  })
    .then((response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      return fetchState();
    })
    .catch(() => {
      setConnectionMessage("No se pudo enviar el comando.", false);
    });
}

function renderPlayer() {
  const song = state.nowPlaying;
  const title = song?.title || "Sin cancion";
  const meta = [song?.artist, song?.album].filter(Boolean).join(" - ");

  elements.songTitle.textContent = title;
  elements.songMeta.textContent = meta || "Elige musica desde Android o usa Reproducir.";
  elements.artwork.textContent = title.trim().charAt(0).toUpperCase() || "S";
  elements.progressFill.style.width = `${Math.max(0, Math.min(state.progressPercent, 100))}%`;
  elements.elapsedTime.textContent = formatTime(state.positionMs);
  elements.durationTime.textContent = formatTime(state.durationMs);
  elements.modeLabel.textContent = playbackModeLabel(state.playbackMode);
  elements.playPauseButton.textContent = state.isPlaying ? "Pausar" : "Reproducir";
}

function renderLists() {
  elements.queueCount.textContent = state.queue.length.toString();
  elements.playlistCount.textContent = state.playlists.length.toString();

  elements.queueList.replaceChildren(...state.queue.map((song) => songItem(song)));
  elements.playlistList.replaceChildren(...state.playlists.map((playlist) => playlistItem(playlist)));

  if (state.queue.length === 0) {
    elements.queueList.append(emptyItem("La cola aparecera cuando Android sincronice el estado."));
  }
  if (state.playlists.length === 0) {
    elements.playlistList.append(emptyItem("Las playlists apareceran despues de conectar el telefono."));
  }
}

function songItem(song) {
  const item = document.createElement("li");
  if (song.id === state.nowPlaying?.id) item.classList.add("is-current");

  const title = document.createElement("strong");
  title.textContent = song.title || "Sin titulo";

  const meta = document.createElement("span");
  meta.textContent = [song.artist, song.album].filter(Boolean).join(" - ") || "Sin metadatos";

  item.append(title, meta);
  return item;
}

function playlistItem(playlist) {
  const item = document.createElement("li");
  const title = document.createElement("strong");
  title.textContent = playlist.name || "Playlist";

  const meta = document.createElement("span");
  const count = Number(playlist.songCount || 0);
  meta.textContent = `${count} ${count === 1 ? "cancion" : "canciones"}`;

  item.append(title, meta);
  return item;
}

function emptyItem(text) {
  const item = document.createElement("li");
  item.className = "empty-item";
  item.textContent = text;
  return item;
}

function apiUrl(path) {
  const settings = currentSettings(true);
  const protocol = window.location.protocol === "https:" ? "https" : "http";
  const url = new URL(`${protocol}://${settings.host}${path}`);
  url.searchParams.set("token", settings.token);
  return url.toString();
}

function currentSettings(updateInputs) {
  const parsed = parseHostAndToken(elements.hostInput.value.trim(), elements.tokenInput.value.trim());
  if (updateInputs) {
    elements.hostInput.value = parsed.host;
    elements.tokenInput.value = parsed.token;
  }
  return parsed;
}

function parseHostAndToken(hostValue, tokenValue) {
  if (!hostValue) {
    return { host: "", token: tokenValue };
  }

  try {
    const url = new URL(hostValue.includes("://") ? hostValue : `http://${hostValue}`);
    return {
      host: url.host,
      token: tokenValue || url.searchParams.get("token") || "",
    };
  } catch {
    return {
      host: hostValue.replace(/^https?:\/\//, "").replace(/\/.*$/, ""),
      token: tokenValue,
    };
  }
}

function formatTime(ms) {
  const totalSeconds = Math.max(0, Math.floor(Number(ms || 0) / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

function playbackModeLabel(mode) {
  return {
    Ordered: "Ordenado",
    Shuffle: "Aleatorio",
    RepeatCurrent: "Repetir cancion",
    RepeatList: "Repetir lista",
  }[mode] || "Modo";
}

elements.connectForm.addEventListener("submit", (event) => {
  event.preventDefault();
  connect();
});

elements.disconnectButton.addEventListener("click", disconnect);

document.querySelectorAll("[data-command]").forEach((button) => {
  button.addEventListener("click", () => sendCommand(button.dataset.command));
});

loadConnectionSettings();
applyUrlConnectionSettings();
renderPlayer();
renderLists();
showConnect();
