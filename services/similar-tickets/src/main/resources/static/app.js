const API_BASE = '/api/similarity';

let logs = [];
let tickets = [];
let ticketsFetchInFlight = false;
let logsFetchInFlight = false;

// Format time for display
function formatTime(date) {
    return date.toLocaleTimeString('en-US', { 
        hour12: false, 
        hour: '2-digit', 
        minute: '2-digit', 
        second: '2-digit',
        fractionalSecondDigits: 3
    });
}

// Add log entry
function addLog(message, type = 'info') {
    const now = new Date();
    const logEntry = {
        time: now,
        message: message,
        type: type,
        timestamp: now.getTime()
    };
    logs.unshift(logEntry); // Add to beginning (latest first)
    if (logs.length > 100) {
        logs = logs.slice(0, 100); // Keep only last 100 logs
    }
    renderLogs();
}

// Render logs
function renderLogs() {
    const container = document.getElementById('logs');
    container.innerHTML = logs.map(log => {
        const timeStr = formatTime(log.time);
        return `
            <div class="log-entry ${log.type}">
                <span class="log-time">[${timeStr}]</span>
                <span class="log-message">${escapeHtml(log.message)}</span>
            </div>
        `;
    }).join('');
}

// Render tickets table
function renderTickets() {
    const tbody = document.getElementById('tickets-body');
    tbody.innerHTML = tickets.map(ticket => {
        const vectorPreview = ticket.vector 
            ? ticket.vector.slice(0, 10).map(v => v.toFixed(2)).join(', ') + '...'
            : 'N/A';
        const fullText = ticket.text || 'N/A';
        return `
            <tr>
                <td class="ticket-id">#${ticket.ticketId}</td>
                <td><span class="ticket-type">${escapeHtml(ticket.ticketType || 'N/A')}</span></td>
                <td class="original-text">${escapeHtml(fullText)}</td>
                <td class="vector-preview" title="${ticket.vector ? ticket.vector.map(v => v.toFixed(4)).join(', ') : 'N/A'}">${vectorPreview}</td>
            </tr>
        `;
    }).join('');
}

// Escape HTML to prevent XSS
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}


async function fetchTickets() {
    if (ticketsFetchInFlight) {
        return;
    }
    ticketsFetchInFlight = true;
    try {
        const response = await fetch(`${API_BASE}/tickets/all`);
        if (response.ok) {
            const data = await response.json();
            tickets = data.tickets || [];
            renderTickets();
        }
    } catch (error) {
        console.error('Error fetching tickets:', error);
    } finally {
        ticketsFetchInFlight = false;
    }
}


async function fetchLogs() {
    if (logsFetchInFlight) {
        return;
    }
    logsFetchInFlight = true;
    try {
        const response = await fetch(`${API_BASE}/tickets/logs`);
        if (response.ok) {
            const data = await response.json();
            const serverLogs = data.logs || [];
            
            // Create a Set of existing log timestamps to avoid duplicates
            const existingTimestamps = new Set(logs.map(l => l.timestamp));
            
            // Add only new logs (by timestamp)
            let hasNewLogs = false;
            serverLogs.forEach(log => {
                if (!existingTimestamps.has(log.timestamp)) {
                    const date = new Date(log.timestamp);
                    logs.unshift({ // Add to beginning (latest first)
                        time: date,
                        message: log.message,
                        type: log.type,
                        timestamp: log.timestamp
                    });
                    hasNewLogs = true;
                }
            });
            
            // Keep only last 100 logs
            if (logs.length > 100) {
                logs = logs.slice(0, 100);
            }
            
            if (hasNewLogs) {
                renderLogs();
            }
        }
    } catch (error) {
        console.error('Error fetching logs:', error);
    } finally {
        logsFetchInFlight = false;
    }
}

function startPolling() {
    fetchTickets();
    fetchLogs();
    setInterval(() => {
        fetchTickets();
        fetchLogs();
    }, 2000);
}

// Toggle left pane
function toggleLeftPane() {
    const leftPane = document.getElementById('left-pane');
    const expandButton = document.getElementById('expand-button');
    const toggleArrow = document.getElementById('toggle-arrow');
    
    leftPane.classList.toggle('collapsed');
    
    if (leftPane.classList.contains('collapsed')) {
        // Pane is collapsed - show expand button on left edge
        expandButton.classList.add('visible');
        // Hide the arrow in the header (pane is hidden anyway)
        toggleArrow.style.display = 'none';
    } else {
        // Pane is visible - show collapse arrow in header
        expandButton.classList.remove('visible');
        toggleArrow.textContent = '◀';
        toggleArrow.style.display = 'inline-block';
    }
}

// Zoom functionality
let currentZoom = 100;
const ZOOM_STEP = 10;
const MIN_ZOOM = 50;
const MAX_ZOOM = 200;

function setZoom(zoom) {
    currentZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    // Set as unitless number for CSS calc() to work correctly
    document.documentElement.style.setProperty('--font-zoom', currentZoom.toString());
    document.getElementById('zoom-level').textContent = currentZoom + '%';
    localStorage.setItem('fontZoom', currentZoom.toString());
}

function zoomIn() {
    setZoom(currentZoom + ZOOM_STEP);
}

function zoomOut() {
    setZoom(currentZoom - ZOOM_STEP);
}

async function loadDefaultZoom() {
    try {
        const response = await fetch(`${API_BASE}/tickets/config`);
        if (response.ok) {
            const config = await response.json();
            setZoom(Number(config.defaultZoom || 100));
            applyEventLogVisibility(config.showEventLog === true);
        }
    } catch (error) {
        console.error('Error loading zoom config:', error);
        setZoom(100);
        applyEventLogVisibility(false);
    }
}

function applyEventLogVisibility(showEventLog) {
    if (showEventLog) {
        return;
    }
    const leftPane = document.getElementById('left-pane');
    if (leftPane && !leftPane.classList.contains('collapsed')) {
        toggleLeftPane();
    }
}

document.addEventListener('DOMContentLoaded', () => {
    addLog('Dashboard initialized', 'info');
    startPolling();
    
    const leftPaneHeader = document.querySelector('.left-pane h2');
    const expandButton = document.getElementById('expand-button');
    
    leftPaneHeader.addEventListener('click', toggleLeftPane);
    expandButton.addEventListener('click', toggleLeftPane);
    
    document.getElementById('zoom-in').addEventListener('click', zoomIn);
    document.getElementById('zoom-out').addEventListener('click', zoomOut);
    
    loadDefaultZoom();
});
