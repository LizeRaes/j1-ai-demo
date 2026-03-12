const logsEl = document.getElementById("logs");
const statusEl = document.getElementById("status");
const ASSISTANT_PREVIEW_LIMIT = 80;
const MAX_LOGS = 500;
const ZOOM_STEP = 10;
const MIN_ZOOM = 50;
const MAX_ZOOM = 200;
let logsState = [];
let eventSource = null;
let currentZoom = 100;

function startSse() {
  if (eventSource) {
    eventSource.close();
  }
  eventSource = new EventSource("/api/coding-assistant/logs/stream");
  eventSource.onopen = () => {
    statusEl.textContent = `Live via SSE ${new Date().toLocaleTimeString()}`;
  };
  eventSource.onmessage = (event) => {
    try {
      const log = JSON.parse(event.data);
      if (!log || !log.timestamp || !log.jobId) {
        return;
      }
      logsState.unshift(log);
      if (logsState.length > MAX_LOGS) {
        logsState = logsState.slice(0, MAX_LOGS);
      }
      renderLogs(logsState);
      statusEl.textContent = `Live update ${new Date().toLocaleTimeString()}`;
    } catch (err) {
      // Ignore malformed SSE payloads.
    }
  };
  eventSource.onerror = () => {
    statusEl.textContent = "SSE disconnected; retrying...";
  };
}

function renderLogs(logs) {
  const scrollingElement = document.scrollingElement || document.documentElement;
  const previousScrollTop = scrollingElement.scrollTop;
  const previousScrollHeight = scrollingElement.scrollHeight;
  const shouldFollowLatest = previousScrollTop < 20;

  if (!Array.isArray(logs) || logs.length === 0) {
    logsEl.innerHTML = '<div class="log-row"><div class="msg">No logs yet.</div></div>';
    if (!shouldFollowLatest) {
      const newScrollHeight = scrollingElement.scrollHeight;
      scrollingElement.scrollTop = previousScrollTop + (newScrollHeight - previousScrollHeight);
    }
    return;
  }

  logsEl.innerHTML = logs
    .map((log) => {
      const timestamp = escapeHtml(log.timestamp ?? "");
      const jobId = escapeHtml(log.jobId ?? "");
      const rawMessage = log.message ?? "";
      const { cssClass, message } = formatMessage(rawMessage);
      return `
        <article class="log-row ${cssClass}">
          <div class="meta">${timestamp} | Job ${jobId}</div>
          <div class="msg">${escapeHtml(message)}</div>
        </article>
      `;
    })
    .join("");

  // Keep the user's reading position stable when new rows are prepended.
  if (!shouldFollowLatest) {
    const newScrollHeight = scrollingElement.scrollHeight;
    scrollingElement.scrollTop = previousScrollTop + (newScrollHeight - previousScrollHeight);
  }
}

function formatMessage(rawMessage) {
  const isAssistantLine = rawMessage.startsWith("[ai-coding-assistant]");
  const isFinalAssistant = rawMessage.includes("Final agent message:");
  const isCallback = rawMessage.startsWith("Callback") || rawMessage.startsWith("Failed to post callback");
  const isJobDone = rawMessage.includes(" is done (");
  const isIncomingJob = rawMessage.startsWith("Job accepted.");

  if (isAssistantLine && !isFinalAssistant) {
    return {
      cssClass: "assistant-stream",
      message: truncate(rawMessage, ASSISTANT_PREVIEW_LIMIT),
    };
  }
  if (isFinalAssistant) {
    return {
      cssClass: "assistant-final",
      message: rawMessage,
    };
  }
  if (isCallback) {
    return {
      cssClass: "callback-block",
      message: rawMessage,
    };
  }
  if (isJobDone || isIncomingJob) {
    return {
      cssClass: "job-done",
      message: rawMessage,
    };
  }
  return {
    cssClass: "program-step",
    message: rawMessage,
  };
}

function truncate(value, limit) {
  if (!value || value.length <= limit) {
    return value;
  }
  return `${value.slice(0, limit)}...`;
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;");
}

startSse();

function setZoom(zoom) {
  currentZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
  document.documentElement.style.setProperty("--font-zoom", currentZoom.toString());
  const zoomLevelEl = document.getElementById("zoom-level");
  if (zoomLevelEl) {
    zoomLevelEl.textContent = `${currentZoom}%`;
  }
  localStorage.setItem("codingAssistantUiZoom", currentZoom.toString());
}

function zoomIn() {
  setZoom(currentZoom + ZOOM_STEP);
}

function zoomOut() {
  setZoom(currentZoom - ZOOM_STEP);
}

async function loadDefaultZoom() {
  try {
    const response = await fetch("/api/coding-assistant/config");
    if (response.ok) {
      const config = await response.json();
      setZoom(Number(config.defaultZoomPercent || 100));
      return;
    }
  } catch (error) {
    // Ignore and use fallback below
  }
  setZoom(100);
}

document.getElementById("zoom-in")?.addEventListener("click", zoomIn);
document.getElementById("zoom-out")?.addEventListener("click", zoomOut);
loadDefaultZoom();

window.addEventListener("beforeunload", () => {
  if (eventSource) {
    eventSource.close();
  }
});
