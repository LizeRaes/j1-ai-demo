const logsEl = document.getElementById("logs");
const statusEl = document.getElementById("status");
const ASSISTANT_PREVIEW_LIMIT = 80;
const MAX_LOGS = 500;
let logsState = [];
let eventSource = null;

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
  if (!Array.isArray(logs) || logs.length === 0) {
    logsEl.innerHTML = '<div class="log-row"><div class="msg">No logs yet.</div></div>';
    return;
  }

  const jobNumbers = new Map();
  let nextJobNumber = 1;
  for (const log of logs) {
    const jobId = log.jobId ?? "";
    if (!jobNumbers.has(jobId)) {
      jobNumbers.set(jobId, nextJobNumber++);
    }
  }

  logsEl.innerHTML = logs
    .map((log) => {
      const timestamp = escapeHtml(log.timestamp ?? "");
      const jobId = escapeHtml(log.jobId ?? "");
      const rawMessage = log.message ?? "";
      const { cssClass, message } = formatMessage(rawMessage);
      const jobNumber = jobNumbers.get(log.jobId ?? "") ?? "-";
      return `
        <article class="log-row ${cssClass}">
          <div class="meta">${timestamp} | Job #${jobNumber} | ${jobId}</div>
          <div class="msg">${escapeHtml(message)}</div>
        </article>
      `;
    })
    .join("");
}

function formatMessage(rawMessage) {
  const isAssistantLine = rawMessage.startsWith("[ai-coding-assistant]");
  const isFinalAssistant = rawMessage.includes("Final agent message:");
  const isCallback = rawMessage.startsWith("Callback") || rawMessage.startsWith("Failed to post callback");
  const isJobDone = rawMessage.includes(" is done (");

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
  if (isJobDone) {
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

window.addEventListener("beforeunload", () => {
  if (eventSource) {
    eventSource.close();
  }
});
