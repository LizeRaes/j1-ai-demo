const API_BASE = '/api';

let logs = [];
let documents = [];
let chunks = [];

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

// Render documents table
function renderDocuments() {
    const tbody = document.getElementById('documents-body');
    if (documents.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="no-results">No documents loaded yet.</td></tr>';
        return;
    }
    
    tbody.innerHTML = documents.map(doc => {
        const teams = doc.rbacTeams && doc.rbacTeams.length > 0 
            ? doc.rbacTeams.join(', ') 
            : '<span class="company-wide">Company-wide</span>';
        const fileType = getFileTypeLabel(doc.documentName);
        const previewMode = isTextPreviewable(doc.documentName) ? 'preview' : 'download';
        return `
            <tr>
                <td class="document-name">
                    <a href="#" class="doc-link" data-doc-name="${escapeHtml(doc.documentName)}" data-open-mode="${previewMode}">${escapeHtml(doc.documentName)}</a>
                </td>
                <td class="doc-type">${escapeHtml(fileType)}</td>
                <td class="rbac-teams">${teams}</td>
                <td class="doc-actions">
                    <button class="delete-doc-btn" data-doc-name="${escapeHtml(doc.documentName)}" title="Delete document and embeddings">🗑</button>
                </td>
            </tr>
        `;
    }).join('');
    
    // Add click handlers for document links
    tbody.querySelectorAll('.doc-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const docName = e.target.getAttribute('data-doc-name');
            const openMode = e.target.getAttribute('data-open-mode');
            showDocument(docName, openMode);
        });
    });

    // Add click handlers for delete actions
    tbody.querySelectorAll('.delete-doc-btn').forEach(button => {
        button.addEventListener('click', async (e) => {
            const docName = e.target.getAttribute('data-doc-name');
            await deleteDocumentByName(docName);
        });
    });
}


// Escape HTML to prevent XSS
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Fetch all documents
async function fetchDocuments() {
    try {
        const response = await fetch(`${API_BASE}/documents/all`);
        if (response.ok) {
            const data = await response.json();
            documents = data.documents || [];
            renderDocuments();
        }
    } catch (error) {
        console.error('Error fetching documents:', error);
    }
}

// Fetch logs
async function fetchLogs() {
    try {
        const response = await fetch(`${API_BASE}/documents/logs`);
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
    }
}

// Fetch all chunks
async function fetchChunks() {
    try {
        const response = await fetch(`${API_BASE}/documents/chunks`);
        if (response.ok) {
            const data = await response.json();
            chunks = data.chunks || [];
            renderChunks();
        }
    } catch (error) {
        console.error('Error fetching chunks:', error);
    }
}

// Render chunks table
function renderChunks() {
    const tbody = document.getElementById('chunks-body');
    if (chunks.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="no-results">No chunks loaded yet.</td></tr>';
        return;
    }
    
    tbody.innerHTML = chunks.map((chunk, index) => {
        const vectorPreview = chunk.vector && chunk.vector.length > 0
            ? chunk.vector.slice(0, 50).map(v => v.toFixed(2)).join(', ') + '...'
            : 'N/A';
        const fullText = chunk.text || 'N/A';
        const chunkNum = chunk.chunkIndex != null ? chunk.chunkIndex : 'N/A';
        const documentName = chunk.documentName || 'N/A';
        const isClickableDocument = documentName !== 'N/A';
        const documentOpenMode = isTextPreviewable(documentName) ? 'preview' : 'download';
        const documentCell = isClickableDocument
            ? `<a href="#" class="chunk-doc-link" data-doc-name="${escapeHtml(documentName)}" data-open-mode="${documentOpenMode}">${escapeHtml(documentName)}</a>`
            : 'N/A';
        // Truncate text for display, show full on click
        const displayText = fullText.length > 100 ? fullText.substring(0, 100) + '...' : fullText;
        const isTruncated = fullText.length > 100;
        return `
            <tr>
                <td class="document-name">${documentCell}</td>
                <td class="chunk-index">${chunkNum}</td>
                <td class="chunk-text ${isTruncated ? 'chunk-text-clickable' : ''}" 
                    data-full-text="${escapeHtml(fullText)}" 
                    data-chunk-id="chunk-${index}">
                    ${escapeHtml(displayText)}
                </td>
                <td class="vector-preview" title="${chunk.vector ? chunk.vector.map(v => v.toFixed(4)).join(', ') : 'N/A'}">${vectorPreview}</td>
            </tr>
        `;
    }).join('');

    // Add click handlers for chunk document links
    tbody.querySelectorAll('.chunk-doc-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const docName = e.target.getAttribute('data-doc-name');
            const openMode = e.target.getAttribute('data-open-mode');
            showDocument(docName, openMode);
        });
    });
    
    // Add click handlers for truncated chunk text
    tbody.querySelectorAll('.chunk-text-clickable').forEach(element => {
        element.addEventListener('click', (e) => {
            const fullText = e.target.getAttribute('data-full-text');
            showChunkTextPopup(fullText);
        });
    });
}

// Show chunk text in a popup
function showChunkTextPopup(text) {
    // Remove existing popup if any
    const existingPopup = document.getElementById('chunk-text-popup');
    if (existingPopup) {
        existingPopup.remove();
    }
    
    // Create popup
    const popup = document.createElement('div');
    popup.id = 'chunk-text-popup';
    popup.className = 'chunk-text-popup';
    popup.innerHTML = `
        <div class="popup-content">
            <div class="popup-header">
                <h3>Full Chunk Text</h3>
                <button class="popup-close" id="chunk-popup-close">&times;</button>
            </div>
            <div class="popup-body">
                <pre>${escapeHtml(text)}</pre>
            </div>
        </div>
    `;
    
    document.body.appendChild(popup);
    
    // Close handlers
    document.getElementById('chunk-popup-close').addEventListener('click', () => {
        popup.remove();
    });
    
    popup.addEventListener('click', (e) => {
        if (e.target === popup) {
            popup.remove();
        }
    });
    
    // Close on Escape key
    const escapeHandler = (e) => {
        if (e.key === 'Escape') {
            popup.remove();
            document.removeEventListener('keydown', escapeHandler);
        }
    };
    document.addEventListener('keydown', escapeHandler);
}

// Poll for updates
function startPolling() {
    // Fetch documents, chunks and logs every 1 second
    fetchDocuments();
    fetchChunks();
    fetchLogs();
    setInterval(() => {
        fetchDocuments();
        fetchChunks();
        fetchLogs();
    }, 1000);
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
        const response = await fetch(`${API_BASE}/documents/config`);
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

// Show document content
async function showDocument(documentName, openMode = null) {
    const mode = openMode || (isTextPreviewable(documentName) ? 'preview' : 'download');
    if (mode !== 'preview') {
        downloadDocument(documentName);
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/documents/content/${encodeURIComponent(documentName)}`);
        if (response.ok) {
            const data = await response.json();
            showDocumentModal(documentName, data.content);
        } else {
            alert('Error loading document: ' + documentName);
        }
    } catch (error) {
        console.error('Error fetching document:', error);
        alert('Error loading document: ' + documentName);
    }
}

// Show document in modal
function showDocumentModal(documentName, content) {
    // Create modal if it doesn't exist
    let modal = document.getElementById('document-modal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'document-modal';
        modal.className = 'document-modal';
        modal.innerHTML = `
            <div class="modal-content">
                <div class="modal-header">
                    <h2 id="modal-doc-name">${escapeHtml(documentName)}</h2>
                    <button class="modal-close" id="modal-close">&times;</button>
                </div>
                <div class="modal-body">
                    <pre id="modal-doc-content"></pre>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        
        // Close modal handlers
        document.getElementById('modal-close').addEventListener('click', () => {
            modal.style.display = 'none';
        });
        modal.addEventListener('click', (e) => {
            if (e.target === modal) {
                modal.style.display = 'none';
            }
        });
    }
    
    // Update content and show
    document.getElementById('modal-doc-name').textContent = documentName;
    document.getElementById('modal-doc-content').textContent = content;
    modal.style.display = 'flex';
}

function isTextPreviewable(documentName) {
    const lower = (documentName || '').toLowerCase();
    return lower.endsWith('.txt') || lower.endsWith('.md');
}

function getFileTypeLabel(documentName) {
    const name = documentName || '';
    const idx = name.lastIndexOf('.');
    if (idx === -1 || idx === name.length - 1) return 'unknown';
    return name.substring(idx + 1).toLowerCase();
}

function downloadDocument(documentName) {
    const a = document.createElement('a');
    a.href = `${API_BASE}/documents/download/${encodeURIComponent(documentName)}`;
    a.download = documentName;
    document.body.appendChild(a);
    a.click();
    a.remove();
}

async function uploadDocumentFile() {
    const fileInput = document.getElementById('upload-file');
    const teamsInput = document.getElementById('upload-rbac');
    const file = fileInput.files && fileInput.files[0];

    if (!file) {
        alert('Select a file first.');
        return;
    }

    const formData = new FormData();
    formData.append('documentName', file.name);
    formData.append('file', file);
    if (teamsInput.value && teamsInput.value.trim()) {
        formData.append('rbacTeams', teamsInput.value.trim());
    }

    try {
        const response = await fetch(`${API_BASE}/documents/upload`, {
            method: 'POST',
            body: formData
        });
        if (!response.ok) {
            throw new Error(await response.text());
        }
        addLog(`Uploaded document: ${file.name}`, 'upsert');
        fileInput.value = '';
        teamsInput.value = '';
        fetchDocuments();
        fetchChunks();
    } catch (error) {
        console.error('Upload failed:', error);
        addLog(`Upload failed: ${file.name}`, 'error');
        alert(`Upload failed for ${file.name}`);
    }
}

async function deleteDocumentByName(documentName) {
    if (!documentName) return;
    const confirmed = window.confirm(`Delete "${documentName}" and its embeddings?`);
    if (!confirmed) return;

    try {
        const response = await fetch(`${API_BASE}/documents/${encodeURIComponent(documentName)}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            // Keep UI resilient even if backend returns non-OK.
            addLog(`Delete request completed with status ${response.status} for ${documentName}`, 'warn');
        } else {
            addLog(`Deleted document: ${documentName}`, 'delete');
        }
    } catch (error) {
        console.error('Delete failed:', error);
        addLog(`Delete request failed for ${documentName}: ${error.message}`, 'warn');
    } finally {
        // Refresh tables regardless, so UI never gets stuck.
        fetchDocuments();
        fetchChunks();
    }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    addLog('Dashboard initialized', 'info');
    startPolling();
    
    // Set up toggle handlers
    const leftPaneHeader = document.querySelector('.left-pane h2');
    const expandButton = document.getElementById('expand-button');
    
    leftPaneHeader.addEventListener('click', toggleLeftPane);
    expandButton.addEventListener('click', toggleLeftPane);
    
    // Set up zoom handlers
    document.getElementById('zoom-in').addEventListener('click', zoomIn);
    document.getElementById('zoom-out').addEventListener('click', zoomOut);
    document.getElementById('upload-button').addEventListener('click', uploadDocumentFile);
    
    // Load default zoom from config
    loadDefaultZoom();
});
