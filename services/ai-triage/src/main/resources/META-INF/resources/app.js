const API_BASE = '/api/triage/v1';
const DEFAULT_ZOOM_PERCENT = Number(document.body.dataset.defaultZoomPercent || '100');
const POLLING_ENABLED = (document.body.dataset.pollingEnabled || 'true') === 'true';
const POLLING_INTERVAL_MS = Number(document.body.dataset.pollingIntervalMs || '2000');
const SHOW_EVENT_LOG = (document.body.dataset.showEventLog || 'false') === 'true';
const DOCUMENT_API_BASE = document.body.dataset.documentsApiBase || '';
const HELPDESK_APP_BASE = (document.body.dataset.helpdeskAppBase || '').replace(/\/$/, '');
const ZOOM_STEP = 10;
const MIN_ZOOM = 50;
const MAX_ZOOM = 200;

let currentZoom = 100;
let shouldFollowEventLog = true;
let shouldFollowTicketList = true;

function initializeUi() {
    currentZoom = DEFAULT_ZOOM_PERCENT;
    applyZoom();
    loadUiData();
    if (!SHOW_EVENT_LOG) {
        const panel = document.getElementById('eventsPanel');
        if (panel && !panel.classList.contains('collapsed')) {
            toggleEventsPanel();
        }
    }
    if (POLLING_ENABLED) {
        setInterval(loadUiData, Math.max(500, POLLING_INTERVAL_MS));
    }
}

function loadUiData() {
    loadEvents();
    loadTickets();
}

function applyZoom() {
    document.documentElement.style.fontSize = ((16 * currentZoom) / 100) + 'px';
    document.getElementById('zoomLevel').textContent = currentZoom;
    localStorage.setItem('uiZoom', currentZoom.toString());
}

function zoomIn() {
    if (currentZoom < MAX_ZOOM) {
        currentZoom = Math.min(currentZoom + ZOOM_STEP, MAX_ZOOM);
        applyZoom();
    }
}

function zoomOut() {
    if (currentZoom > MIN_ZOOM) {
        currentZoom = Math.max(currentZoom - ZOOM_STEP, MIN_ZOOM);
        applyZoom();
    }
}

function toggleEventsPanel() {
    const ep = document.getElementById('eventsPanel');
    const tp = document.getElementById('ticketsPanel');
    const cb = document.getElementById('eventsCollapseButton');
    const collapsed = ep.classList.contains('collapsed');
    ep.classList.toggle('collapsed', !collapsed);
    tp.classList.toggle('expanded', !collapsed);
    cb.classList.toggle('visible', !collapsed);
}

function formatTimestamp(ts) {
    return ts ? new Date(ts).toLocaleTimeString() : '';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text || '';
    return div.innerHTML;
}

function isNearTop(el, threshold = 8) {
    return el.scrollTop <= threshold;
}

function getHelpdeskTicketUrl(ticketId) {
    return HELPDESK_APP_BASE + '/?tab=inbox&ticketId=' + encodeURIComponent(ticketId);
}

function extractDocumentName(v) {
    if (!v) return '';
    if (!v.includes('/')) return v;
    const p = v.split('/');
    return p[p.length - 1] || '';
}

function isTextPreviewableDocument(v) {
    const n = extractDocumentName(v).toLowerCase();
    return n.endsWith('.txt') || n.endsWith('.md');
}

function getDocumentTypeLabel(v) {
    const n = extractDocumentName(v);
    const i = n.lastIndexOf('.');
    return i >= 0 && i < n.length - 1 ? n.substring(i + 1).toLowerCase() : 'unknown';
}

async function openDocument(documentName, documentLinkOrName, citation) {
    const normalizedName = extractDocumentName(documentLinkOrName || documentName);
    if (!normalizedName) return;
    if (!isTextPreviewableDocument(normalizedName)) {
        window.open(DOCUMENT_API_BASE + '/download/' + encodeURIComponent(normalizedName), '_blank');
        return;
    }
    const response = await fetch(DOCUMENT_API_BASE + '/content/' + encodeURIComponent(normalizedName));
    if (!response.ok) throw new Error('Could not load ' + normalizedName);
    const payload = await response.json();
    showDocumentPreviewModal(normalizedName, payload.content || '', citation || '');
}

function isMarkdownControlChar(ch) {
    return ch === '*' || ch === '_' || ch === '`' || ch === '~' || ch === '#' || ch === '>' ||
        ch === '[' || ch === ']' || ch === '(' || ch === ')' || ch === '!' || ch === '|';
}

function buildSearchProjection(text) {
    const chars = [];
    const map = [];
    let previousWasSpace = false;
    for (let i = 0; i < text.length; i += 1) {
        const ch = text[i];
        if (isMarkdownControlChar(ch)) {
            continue;
        }
        if (/\s/.test(ch)) {
            if (!previousWasSpace) {
                chars.push(' ');
                map.push(i);
                previousWasSpace = true;
            }
            continue;
        }
        previousWasSpace = false;
        chars.push(ch.toLowerCase());
        map.push(i);
    }
    return { projected: chars.join(''), map };
}

function stripBoundaryQuotes(text) {
    return (text || '').replace(/^[\s"'`“”‘’]+|[\s"'`“”‘’]+$/g, '');
}

function findCitationRangeInContent(content, citation) {
    const citationText = stripBoundaryQuotes(citation || '').trim();
    if (!citationText || citationText.length < 6) {
        return null;
    }

    const exactIndex = content.toLowerCase().indexOf(citationText.toLowerCase());
    if (exactIndex >= 0) {
        return { start: exactIndex, end: exactIndex + citationText.length };
    }

    const contentProjection = buildSearchProjection(content);
    const citationProjection = buildSearchProjection(citationText);
    if (!citationProjection.projected || citationProjection.projected.length < 6) {
        return null;
    }

    const projectedIndex = contentProjection.projected.indexOf(citationProjection.projected);
    if (projectedIndex < 0) {
        return null;
    }

    const mappedStart = contentProjection.map[projectedIndex];
    const mappedEnd = contentProjection.map[projectedIndex + citationProjection.projected.length - 1];
    if (!Number.isInteger(mappedStart) || !Number.isInteger(mappedEnd)) {
        return null;
    }
    return { start: mappedStart, end: mappedEnd + 1 };
}

function buildHighlightedPreviewHtml(content, citation) {
    const range = findCitationRangeInContent(content, citation);
    if (!range) {
        return escapeHtml(content);
    }
    const before = escapeHtml(content.slice(0, range.start));
    const hit = escapeHtml(content.slice(range.start, range.end));
    const after = escapeHtml(content.slice(range.end));
    return before + '<mark class="doc-citation-highlight" style="background:#fff59d;color:#111;padding:0 .08rem;border-radius:2px;">' + hit + '</mark>' + after;
}

function showDocumentPreviewModal(documentName, content, citation) {
    const overlay = document.createElement('div');
    overlay.style = 'position:fixed;top:0;left:0;width:100%;height:100%;background-color:rgba(0,0,0,0.65);z-index:3000;display:flex;align-items:center;justify-content:center;';
    const modal = document.createElement('div');
    modal.style = 'background:#fff;max-width:70%;max-height:80%;overflow:auto;border-radius:8px;padding:16px;box-shadow:0 4px 18px rgba(0,0,0,0.25);';
    const previewHtml = buildHighlightedPreviewHtml(content, citation);
    modal.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;"><strong>' + escapeHtml(documentName) + '</strong><button id="doc-preview-close">Close</button></div><pre style="white-space:pre-wrap;word-wrap:break-word;line-height:1.5;">' + previewHtml + '</pre>';
    overlay.appendChild(modal);
    document.body.appendChild(overlay);
    const mark = modal.querySelector('.doc-citation-highlight');
    if (mark) {
        mark.scrollIntoView({ block: 'center' });
    }
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) overlay.remove();
    });
    modal.querySelector('#doc-preview-close').addEventListener('click', () => overlay.remove());
}

function renderEvent(event) {
    const time = formatTimestamp(event.timestamp);
    const ticketIdPart = event.ticketId ? '<span class="event-ticket-id">[Ticket #' + event.ticketId + ']</span> ' : '';
    return '<div class="event-entry ' + event.level + '"><div class="event-time">' + time + '</div><div>' + ticketIdPart + escapeHtml(event.message) + '</div></div>';
}

function renderTicket(ticket) {
    const time = formatTimestamp(ticket.timestamp);
    const related = (ticket.relatedTicketIds || []).map((id) => '<a class="related-ticket-id" href="' + getHelpdeskTicketUrl(id) + '">#' + id + '</a>').join(' ');
    const citations = (ticket.policyCitations || []).map((c) => {
        const docName = c.documentName || 'Unknown Document';
        const docLink = c.documentLink || docName;
        const actionLabel = isTextPreviewableDocument(docLink) ? 'Preview' : 'Download';
        return '<div class="policy-citation"><strong>' + escapeHtml(docName) + '</strong> [' + escapeHtml(getDocumentTypeLabel(docLink)) + '] <a href="#" class="doc-action-link" data-doc-name="' + escapeHtml(docName) + '" data-doc-link="' + escapeHtml(docLink) + '" data-citation="' + escapeHtml(c.citation || '') + '">' + actionLabel + '</a><div>' + escapeHtml(c.citation || '') + '</div></div>';
    }).join('');
    const fail = ticket.status === 'FAILED' && ticket.failReason ? '<div class="fail-reason">' + escapeHtml(ticket.failReason) + '</div>' : '';
    const reqId = ticket.incomingRequestId ? ('Request ID: ' + ticket.incomingRequestId + ' • ') : '';
    return '<div class="ticket-card"><div style="display:flex;justify-content:space-between;"><div class="ticket-id">Ticket #' + ticket.ticketId + '</div><div class="ticket-status ' + ticket.status + '">' + ticket.status + '</div></div><div style="font-size:.75rem;color:#666;margin:.5rem 0;">' + reqId + time + '</div><div style="background:#f8f8f8;padding:.75rem;border-radius:.25rem;margin-bottom:.75rem;">' + escapeHtml(ticket.message) + '</div>' + (related ? '<div style="margin-bottom:.75rem;">' + related + '</div>' : '') + citations + fail + '</div>';
}

function loadEvents() {
    fetch(API_BASE + '/events').then((r) => r.json()).then((events) => {
        const c = document.getElementById('eventsContent');
        const prevTop = c.scrollTop;
        const prevHeight = c.scrollHeight;
        c.innerHTML = events.length ? events.map(renderEvent).join('') : '<div class="empty-state">No events yet</div>';
        c.scrollTop = (shouldFollowEventLog || isNearTop(c)) ? 0 : prevTop + (c.scrollHeight - prevHeight);
    }).catch((error) => console.error('Error loading events:', error));
}

function loadTickets() {
    fetch(API_BASE + '/tickets').then((r) => r.json()).then((tickets) => {
        const c = document.getElementById('ticketsContent');
        const prevTop = c.scrollTop;
        const prevHeight = c.scrollHeight;
        c.innerHTML = tickets.length ? tickets.map(renderTicket).join('') : '<div class="empty-state">No tickets yet</div>';
        wireDocumentActions(c);
        c.scrollTop = (shouldFollowTicketList || isNearTop(c)) ? 0 : prevTop + (c.scrollHeight - prevHeight);
    }).catch((error) => console.error('Error loading tickets:', error));
}

function wireDocumentActions(root) {
    root.querySelectorAll('.doc-action-link').forEach((link) => {
        link.addEventListener('click', async (e) => {
            e.preventDefault();
            try {
                await openDocument(
                    e.currentTarget.getAttribute('data-doc-name'),
                    e.currentTarget.getAttribute('data-doc-link'),
                    e.currentTarget.getAttribute('data-citation') || ''
                );
            } catch (error) {
                console.error('Error opening document:', error);
            }
        });
    });
}

document.getElementById('eventsContent').addEventListener('scroll', (e) => {
    shouldFollowEventLog = isNearTop(e.currentTarget);
});
document.getElementById('ticketsContent').addEventListener('scroll', (e) => {
    shouldFollowTicketList = isNearTop(e.currentTarget);
});

initializeUi();