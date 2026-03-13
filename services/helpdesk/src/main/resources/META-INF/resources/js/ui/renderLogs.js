import {getRecentEvents} from '../api/eventsApi.js';
import {formatTime, getEventColorClass} from '../util/format.js';
import {$, createElement} from '../util/dom.js';
import {switchTab} from './router.js';
import {UI_CONFIG} from '../config.js';
import {state} from './state.js';

let lastEventTime = null;
let pollInterval = null;
let logsCollapsed = false;
let logsZoomLevel = 100; // Default zoom level
let mainZoomLevel = 100; // Default zoom level for main content
let currentFilter = 'all'; // Current event filter: 'all', 'info', 'warning', 'error'
let allEvents = []; // Store all events for filtering
let consecutiveLoadFailures = 0;

export function startEventPolling() {
    // Clear initial loading message
    const container = $('#logs-content');
    if (container && container.querySelector('.loading')) {
        container.innerHTML = '';
    }

    // Initialize zoom level from config or localStorage
    const savedLogsZoom = localStorage.getItem('logsZoomLevel');
    if (savedLogsZoom) {
        logsZoomLevel = parseInt(savedLogsZoom, 10);
    } else {
        logsZoomLevel = UI_CONFIG.DEFAULT_ZOOM_LEVEL;
    }
    applyLogsZoom();

    // Initialize main zoom level
    const savedMainZoom = localStorage.getItem('mainZoomLevel');
    if (savedMainZoom) {
        mainZoomLevel = parseInt(savedMainZoom, 10);
    } else {
        mainZoomLevel = UI_CONFIG.DEFAULT_ZOOM_LEVEL;
    }
    applyMainZoom();

    // Setup toggle button
    setupLogsToggle();

    // Setup zoom controls
    setupZoomControls();

    // Setup filter buttons
    setupFilterButtons();

    loadEvents();
    pollInterval = setInterval(loadEvents, 1500); // Poll every 1.5 seconds
}

export function setLogsPanelCollapsed(collapsed) {
    logsCollapsed = !!collapsed;
    const toggleBtn = $('#logs-toggle-btn');
    const logsPane = $('#logs-pane');
    if (!toggleBtn || !logsPane) return;

    logsPane.classList.toggle('collapsed', logsCollapsed);
    const arrow = toggleBtn.querySelector('.toggle-arrow');
    if (arrow) {
        arrow.textContent = logsCollapsed ? '▶' : '◀';
    }
    toggleBtn.title = logsCollapsed ? 'Show Event Log' : 'Hide Event Log';
}

function setupFilterButtons() {
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            // Update active state
            document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            // Update filter
            currentFilter = btn.dataset.filter;

            // Re-render events with filter
            applyFilter();
        });
    });
}

function applyFilter() {
    const container = $('#logs-content');
    if (!container) return;

    // Filter events based on current filter
    let filteredEvents = allEvents;
    if (currentFilter !== 'all') {
        filteredEvents = allEvents.filter(event => {
            const severity = event.severity?.toLowerCase() || '';
            return severity === currentFilter.toLowerCase();
        });
    }

    // Clear container and re-render
    container.innerHTML = '';
    if (filteredEvents.length > 0) {
        // Reverse to show newest at top
        const reversedEvents = [...filteredEvents].reverse();
        renderEvents(reversedEvents, false); // false = don't update allEvents
    } else {
        const emptyMsg = createElement('div', 'log-entry', 'No events match the filter');
        emptyMsg.dataset.empty = 'true';
        emptyMsg.style.color = '#888';
        emptyMsg.style.fontStyle = 'italic';
        emptyMsg.style.padding = '1rem';
        emptyMsg.style.textAlign = 'center';
        container.appendChild(emptyMsg);
    }
}

function setupLogsToggle() {
    const toggleBtn = $('#logs-toggle-btn');
    const logsPane = $('#logs-pane');
    if (!toggleBtn || !logsPane) return;

    toggleBtn.addEventListener('click', () => {
        setLogsPanelCollapsed(!logsCollapsed);
    });
}

function setupZoomControls() {
    // Logs zoom controls
    const logsZoomOutBtn = $('#logs-zoom-out');
    const logsZoomInBtn = $('#logs-zoom-in');
    const logsZoomLevelEl = $('#logs-zoom-level');

    // Debug: Check if buttons are found
    if (!logsZoomOutBtn) {
        console.error('logs-zoom-out button not found');
    }
    if (!logsZoomInBtn) {
        console.error('logs-zoom-in button not found');
    }
    if (!logsZoomLevelEl) {
        console.error('logs-zoom-level element not found');
    }

    if (logsZoomOutBtn && logsZoomInBtn && logsZoomLevelEl) {
        logsZoomOutBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (logsZoomLevel > 50) {
                logsZoomLevel -= 10;
                applyLogsZoom();
                localStorage.setItem('logsZoomLevel', logsZoomLevel.toString());
            }
        });

        logsZoomInBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (logsZoomLevel < 200) {
                logsZoomLevel += 10;
                applyLogsZoom();
                localStorage.setItem('logsZoomLevel', logsZoomLevel.toString());
            }
        });
    } else {
        console.error('Failed to setup logs zoom controls - one or more elements not found');
    }

    // Main content zoom controls
    const mainZoomOutBtn = $('#main-zoom-out');
    const mainZoomInBtn = $('#main-zoom-in');
    const mainZoomLevelEl = $('#main-zoom-level');

    if (mainZoomOutBtn && mainZoomInBtn && mainZoomLevelEl) {
        mainZoomOutBtn.addEventListener('click', () => {
            if (mainZoomLevel > 50) {
                mainZoomLevel -= 10;
                applyMainZoom();
                localStorage.setItem('mainZoomLevel', mainZoomLevel.toString());
            }
        });

        mainZoomInBtn.addEventListener('click', () => {
            if (mainZoomLevel < 200) {
                mainZoomLevel += 10;
                applyMainZoom();
                localStorage.setItem('mainZoomLevel', mainZoomLevel.toString());
            }
        });
    }
}

function applyLogsZoom() {
    const logsPane = $('#logs-pane');
    const logsPaneContent = logsPane?.querySelector('.logs-pane-content');
    const logsContent = $('#logs-content');
    const logsZoomLevelEl = $('#logs-zoom-level');
    if (logsPane && logsPaneContent && logsContent && logsZoomLevelEl) {
        // Apply zoom to the content area that contains the log entries
        // This will affect all child elements including .log-entry
        logsPaneContent.style.fontSize = `${logsZoomLevel}%`;
        logsZoomLevelEl.textContent = `${logsZoomLevel}%`;
    }
}

function applyMainZoom() {
    const mainPane = $('#main-pane');
    const mainZoomLevelEl = $('#main-zoom-level');
    if (mainPane && mainZoomLevelEl) {
        mainPane.style.fontSize = `${mainZoomLevel}%`;
        mainZoomLevelEl.textContent = `${mainZoomLevel}%`;
    }
}

export function stopEventPolling() {
    if (pollInterval) {
        clearInterval(pollInterval);
        pollInterval = null;
    }
}

function refreshTicketViews() {
    // Refresh all ticket views to show new tickets
    // We refresh all relevant views, not just the current tab, so users see new tickets
    // regardless of which tab they're on

    // Refresh "All Tickets" view (inbox)
    import('./renderTickets.js').then(({renderTickets}) => {
        renderTickets('inbox').catch(err => {
            console.error('Error refreshing inbox tickets:', err);
        });
    });

    // Refresh dispatcher inbox (in case a new request was converted)
    import('./renderDispatcherInbox.js').then(({renderDispatcherInbox}) => {
        renderDispatcherInbox().catch(err => {
            console.error('Error refreshing dispatcher inbox:', err);
        });
    });

    // Note: We don't refresh team/mine views as those are filtered by actor context
    // and new tickets might not belong to the current user/team
}

function refreshSelectedTicketDetailIfNeeded(changedTicketIds) {
    if (!Array.isArray(changedTicketIds) || changedTicketIds.length === 0) return;
    const selectedTicketId = state.selectedTicketId;
    if (!selectedTicketId) return;

    const selectedTicketIdStr = String(selectedTicketId);
    const hasSelectedTicketUpdate = changedTicketIds.some(id => String(id) === selectedTicketIdStr);
    if (!hasSelectedTicketUpdate) return;

    import('./renderTicketDetail.js')
        .then(({loadTicketDetail}) => loadTicketDetail(selectedTicketId))
        .catch(err => {
            console.error('Error refreshing selected ticket detail:', err);
        });
}

async function loadEvents() {
    try {
        const events = await getRecentEvents(lastEventTime, 200);
        consecutiveLoadFailures = 0;
        const container = $('#logs-content');
        if (!container) return;

        // Clear loading message
        if (container.querySelector('.loading')) {
            container.innerHTML = '';
        }

        if (events.length > 0) {
            // Track new ticket creation events to trigger refresh
            const newTicketEvents = [];
            const callbackUpdatedTicketIds = [];

            // Add new events to allEvents array (avoid duplicates)
            events.forEach(event => {
                const exists = allEvents.find(e => e.id === event.id);
                if (!exists) {
                    allEvents.push(event);
                    // Check if this is a ticket creation event
                    const eventType = event.eventType?.toUpperCase() || '';
                    if (eventType === 'TICKET_CREATED' || eventType === 'AI_TICKET_CREATED') {
                        newTicketEvents.push(event);
                    }
                    if (event.source === 'coding-assistant-callback' && event.ticketId) {
                        callbackUpdatedTicketIds.push(event.ticketId);
                    }
                }
            });

            // Sort allEvents by createdAt (oldest first) to maintain order
            allEvents.sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

            // Apply current filter and render
            applyFilter();

            // If new tickets were created, refresh the ticket lists
            const shouldRefreshTicketViews = newTicketEvents.length > 0 || callbackUpdatedTicketIds.length > 0;
            if (shouldRefreshTicketViews) {
                refreshTicketViews();
            }
            if (callbackUpdatedTicketIds.length > 0) {
                refreshSelectedTicketDetailIfNeeded(callbackUpdatedTicketIds);
            }

            // Update last event time to most recent (last item in ASC array)
            lastEventTime = events[events.length - 1].createdAt;
        } else {
            // No new events - still apply filter to show current state
            if (allEvents.length === 0) {
                // Show empty state if no events at all
                const container = $('#logs-content');
                if (container && (container.children.length === 0 || container.querySelector('.log-entry[data-empty="true"]'))) {
                    // Remove existing empty message if present
                    const existingEmpty = container.querySelector('.log-entry[data-empty="true"]');
                    if (existingEmpty) {
                        existingEmpty.remove();
                    }
                    const emptyMsg = createElement('div', 'log-entry', 'No events yet');
                    emptyMsg.dataset.empty = 'true';
                    emptyMsg.style.color = '#888';
                    emptyMsg.style.fontStyle = 'italic';
                    emptyMsg.style.padding = '1rem';
                    emptyMsg.style.textAlign = 'center';
                    container.appendChild(emptyMsg);
                }
            } else {
                // We have events but no new ones - just apply filter to show current filtered view
                applyFilter();
            }
        }
    } catch (error) {
        console.error('Error loading events:', error);
        const container = $('#logs-content');
        consecutiveLoadFailures += 1;
        if (!container) return;

        // On first-load/transient startup failures, keep UX calm and let polling recover.
        if (allEvents.length === 0 && consecutiveLoadFailures <= 3) {
            container.innerHTML = '';
            const emptyMsg = createElement('div', 'log-entry', 'No events yet');
            emptyMsg.dataset.empty = 'true';
            emptyMsg.style.color = '#888';
            emptyMsg.style.fontStyle = 'italic';
            emptyMsg.style.padding = '1rem';
            emptyMsg.style.textAlign = 'center';
            container.appendChild(emptyMsg);
            return;
        }

        container.innerHTML = '<div class="log-entry" style="color: #f44336;">Error loading events</div>';
    }
}

function renderEvents(events, updateAllEvents = true) {
    const container = $('#logs-content');
    if (!container) return;

    // Remove empty state message if events arrive
    const emptyMsg = container.querySelector('.log-entry[data-empty="true"]');
    if (emptyMsg) {
        emptyMsg.remove();
    }

    // Clear container and render all filtered events
    container.innerHTML = '';

    // Create entries for all events
    const entries = events.map(event => createLogEntry(event));

    // Prepend in reverse order so newest is at top
    entries.reverse().forEach(entry => {
        container.insertBefore(entry, container.firstChild);
    });

    // Keep scroll at top (latest events)
    if (entries.length > 0) {
        container.scrollTop = 0;
    }
}

function createLogEntry(event) {
    const entry = createElement('div', 'log-entry');
    entry.dataset.eventId = event.id;

    const colorClass = getEventColorClass(event);
    entry.classList.add(colorClass);

    const time = createElement('span', 'log-entry-time', formatTime(event.createdAt));
    const message = createElement('span', 'log-entry-message', event.message);

    entry.appendChild(time);
    entry.appendChild(message);

    // Click handler to navigate to ticket if available
    if (event.ticketId) {
        entry.style.cursor = 'pointer';
        entry.addEventListener('click', () => {
            // Navigate to ticket detail
            switchTab('inbox');
        });
    }

    return entry;
}
