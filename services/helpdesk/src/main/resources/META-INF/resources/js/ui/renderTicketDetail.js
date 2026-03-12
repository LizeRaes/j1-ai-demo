import {
    acceptTicket,
    addComment,
    addPullRequest,
    rollbackToRequest,
    updateTicketStatus
} from '../api/ticketsApi.js';
import {state} from './state.js';
import {formatDateTime} from '../util/format.js';
import {$, clearElement, createElement} from '../util/dom.js';
import {renderTickets} from './renderTickets.js';
import {getActorContext} from './actorContext.js';
import {
    fetchDocumentContent,
    getDocumentDownloadUrl,
    isTextPreviewableDocument
} from '../api/documentsApi.js';

export function renderTicketDetail(ticket) {
    const container = $('#detail-pane');
    if (!container) return;

    clearElement(container);

    const detail = createElement('div', 'ticket-detail');

    // Header
    const header = createElement('div', 'ticket-header');
    const title = createElement('h2', 'ticket-title', `Ticket #${ticket.id}`);
    header.appendChild(title);

    const urgencyScore = Number(ticket.urgencyScore);
    const hasUrgencyScore = Number.isFinite(urgencyScore);
    const isCriticalUrgency = hasUrgencyScore && urgencyScore >= 8;
    const isUrgent = hasUrgencyScore && urgencyScore >= 6 && urgencyScore < 8;
    const urgencyLabel = isCriticalUrgency
        ? '<span class="text-critical-urgency"><strong>CRITICAL</strong></span>'
        : (isUrgent ? '<span class="text-urgent"><strong>URGENT</strong></span>' : '');
    const meta = createElement('div', 'ticket-meta');
    meta.innerHTML = `
        <span><strong>Type:</strong> ${ticket.ticketType}</span>
        <span><strong>Status:</strong> ${ticket.status}</span>
        <span><strong>Source:</strong> ${ticket.source}</span>
        <span><strong>Team:</strong> <span style="color: #666; font-style: italic;">${ticket.assignedTeam}</span> <small style="color: #999;">(auto-assigned)</small></span>
        ${ticket.assignedTo ? `<span><strong>Assigned to:</strong> ${ticket.assignedTo}</span>` : ''}
        ${urgencyLabel}
        ${ticket.urgencyScore ? `<span class="${isCriticalUrgency ? 'text-critical-urgency' : ''}"><strong>Urgency Score:</strong> ${ticket.urgencyScore}/10</span>` : ''}
        ${ticket.aiConfidence ? `<span><strong>AI Confidence:</strong> ${(ticket.aiConfidence * 100).toFixed(1)}%</span>` : ''}
        <span><strong>Created:</strong> ${formatDateTime(ticket.createdAt)}</span>
    `;
    header.appendChild(meta);
    detail.appendChild(header);

    // Original Request
    const originalSection = createElement('div', 'ticket-section');
    originalSection.innerHTML = '<h4>Original Request</h4>';
    const originalText = createElement('div', 'ticket-original-request', ticket.originalRequest);
    originalSection.appendChild(originalText);
    detail.appendChild(originalSection);

    // Actions
    const actionsSection = createElement('div', 'ticket-section');
    actionsSection.innerHTML = '<h4>Actions</h4>';
    const actionsDiv = createElement('div', 'ticket-actions');

    // Accept button (for FROM_AI only)
    if (ticket.status === 'FROM_AI') {
        const acceptBtn = createElement('button', 'btn btn-success', 'Accept');
        acceptBtn.addEventListener('click', async () => {
            try {
                await acceptTicket(ticket.id, state.currentUserId);
                alert('Ticket accepted!');
                await renderTickets('inbox');
                await loadTicketDetail(ticket.id);
            } catch (error) {
                alert('Error accepting ticket: ' + error.message);
            }
        });
        actionsDiv.appendChild(acceptBtn);
    }

    // Rollback to Request (for FROM_AI tickets created from incoming requests)
    if (ticket.status === 'FROM_AI' && ticket.rollbackAllowed && ticket.incomingRequestId) {
        const rollbackBtn = createElement('button', 'btn btn-warning', 'Convert back to request');
        rollbackBtn.style.background = '#ff9800';
        rollbackBtn.style.color = 'white';
        rollbackBtn.addEventListener('click', async () => {
            try {
                await rollbackToRequest(ticket.id);
                // Refresh the view
                const {renderTickets} = await import('./renderTickets.js');
                await renderTickets('inbox');
                // Clear detail pane since ticket is now hidden
                const container = $('#detail-pane');
                if (container) {
                    container.innerHTML = '<div class="detail-placeholder"><p>Ticket converted back to request. Select another item to view details.</p></div>';
                }
            } catch (error) {
                alert('Error converting ticket: ' + error.message);
            }
        });
        actionsDiv.appendChild(rollbackBtn);
    }

    // Ticket Type change (for dispatcher - can change ticketType, team updates automatically)
    if (ticket.status === 'FROM_DISPATCH' || ticket.status === 'FROM_AI' || ticket.status === 'RETURNED_TO_DISPATCH') {
        const typeSelect = createElement('select', 'form-select');
        typeSelect.style.marginRight = '8px';
        typeSelect.style.marginBottom = '8px';

        const ticketTypes = [
            {value: 'BILLING_REFUND', label: 'Billing - Refund', group: 'Billing'},
            {value: 'BILLING_OTHER', label: 'Billing - Other', group: 'Billing'},
            {value: 'SCHEDULING_CANCELLATION', label: 'Scheduling - Cancellation', group: 'Scheduling'},
            {value: 'SCHEDULING_OTHER', label: 'Scheduling - Other', group: 'Scheduling'},
            {value: 'ACCOUNT_ACCESS', label: 'Account - Access', group: 'Account / General Support'},
            {value: 'SUPPORT_OTHER', label: 'Support - Other', group: 'Account / General Support'},
            {value: 'BUG_APP', label: 'Bug - App', group: 'Bugs / Engineering'},
            {value: 'BUG_BACKEND', label: 'Bug - Backend', group: 'Bugs / Engineering'},
            {value: 'ENGINEERING_OTHER', label: 'Engineering - Other', group: 'Bugs / Engineering'},
            {value: 'OTHER', label: 'Other', group: 'Unclassified'}
        ];

        ticketTypes.forEach(type => {
            const option = createElement('option');
            option.value = type.value;
            option.textContent = type.label;
            if (type.value === ticket.ticketType) option.selected = true;
            typeSelect.appendChild(option);
        });

        const updateTypeBtn = createElement('button', 'btn btn-primary', 'Update Type');
        updateTypeBtn.style.marginBottom = '8px';
        updateTypeBtn.addEventListener('click', async () => {
            try {
                const {updateTicketType} = await import('../api/ticketsApi.js');
                await updateTicketType(ticket.id, {ticketType: typeSelect.value});
                alert('Ticket type updated! Team will be updated automatically.');
                await loadTicketDetail(ticket.id);
            } catch (error) {
                alert('Error updating ticket type: ' + error.message);
            }
        });

        const typeLabel = createElement('label', 'form-label');
        typeLabel.textContent = 'Change Ticket Type (team updates automatically):';
        typeLabel.style.display = 'block';
        typeLabel.style.marginBottom = '4px';
        actionsDiv.appendChild(typeLabel);
        actionsDiv.appendChild(typeSelect);
        actionsDiv.appendChild(updateTypeBtn);
    }

    // Status change (for TRIAGED+)
    if (ticket.status === 'FROM_DISPATCH' || ticket.status === 'TRIAGED' || ticket.status === 'IN_PROGRESS' || ticket.status === 'WAITING_ON_USER') {
        const statusSelect = createElement('select', 'status-select');
        const statuses = ticket.status === 'FROM_DISPATCH'
            ? ['IN_PROGRESS', 'WAITING_ON_USER', 'COMPLETED']
            : ['TRIAGED', 'IN_PROGRESS', 'WAITING_ON_USER', 'COMPLETED'];
        statuses.forEach(status => {
            const option = createElement('option');
            option.value = status;
            option.textContent = status;
            if (status === ticket.status) option.selected = true;
            statusSelect.appendChild(option);
        });

        const updateBtn = createElement('button', 'btn btn-primary', 'Update Status');
        updateBtn.addEventListener('click', async () => {
            try {
                await updateTicketStatus(ticket.id, statusSelect.value);
                alert('Status updated!');
                await loadTicketDetail(ticket.id);
            } catch (error) {
                alert('Error updating status: ' + error.message);
            }
        });

        actionsDiv.appendChild(statusSelect);
        actionsDiv.appendChild(updateBtn);
    }

    actionsSection.appendChild(actionsDiv);
    detail.appendChild(actionsSection);

    // Related Tickets
    const relatedTicketsSection = createElement('div', 'ticket-section related-section');
    relatedTicketsSection.innerHTML = '<h4>Related Tickets</h4>';
    const relatedTicketsList = createElement('div', 'related-list');

    // Parse aiPayloadJson to extract relatedTicketIds
    let relatedTicketIds = [];
    if (ticket.aiPayloadJson) {
        try {
            const aiPayload = JSON.parse(ticket.aiPayloadJson);
            if (aiPayload.relatedTicketIds && Array.isArray(aiPayload.relatedTicketIds)) {
                relatedTicketIds = aiPayload.relatedTicketIds;
            }
        } catch (e) {
            console.error('Error parsing aiPayloadJson:', e);
        }
    }

    if (relatedTicketIds.length > 0) {
        relatedTicketIds.forEach(ticketId => {
            const relatedItem = createElement('div', 'related-item');
            const relatedLink = createElement('a', 'related-link', `Ticket #${ticketId}`);
            relatedLink.href = `/?tab=inbox&ticketId=${ticketId}`;
            relatedLink.target = '_blank';
            relatedLink.rel = 'noopener noreferrer';
            relatedItem.appendChild(relatedLink);
            relatedTicketsList.appendChild(relatedItem);
        });
    } else {
        const emptyMsg = createElement('div', 'related-empty', 'No related tickets');
        relatedTicketsList.appendChild(emptyMsg);
    }

    relatedTicketsSection.appendChild(relatedTicketsList);
    detail.appendChild(relatedTicketsSection);

    // Related Company Docs
    const relatedDocsSection = createElement('div', 'ticket-section related-section');
    relatedDocsSection.innerHTML = '<h4>Related Company Docs</h4>';
    const relatedDocsList = createElement('div', 'related-list related-docs-list');

    // Parse aiPayloadJson to extract policyCitations
    let policyCitations = [];
    if (ticket.aiPayloadJson) {
        try {
            const aiPayload = JSON.parse(ticket.aiPayloadJson);
            if (aiPayload.policyCitations && Array.isArray(aiPayload.policyCitations)) {
                policyCitations = aiPayload.policyCitations;
            }
        } catch (e) {
            console.error('Error parsing aiPayloadJson:', e);
        }
    }

    // Get current user's team for RBAC check
    const actorContext = getActorContext();
    const userTeam = actorContext.team ? actorContext.team.toLowerCase() : null;

    if (policyCitations.length > 0) {
        const citationGroups = groupCitationsByDocument(policyCitations);

        citationGroups.forEach(group => {
            const relatedItem = createElement('div', 'related-item related-doc-item');
            const docNameValue = group.documentName || 'Document';
            const docLinkValue = group.documentLink || group.documentName;

            // Check RBAC: only show document link if user's team is in rbacTeams
            const hasAccess = group.citations.some(citation => citation.rbacTeams &&
                Array.isArray(citation.rbacTeams) &&
                (citation.rbacTeams.length === 0 || citation.rbacTeams.some(team => team.toLowerCase() === userTeam)));

            // Debug logging
            if (docNameValue) {
                console.log('Document:', docNameValue, 'User team:', userTeam, 'Has access:', hasAccess, 'Citations:', group.citations.length);
            }

            if (hasAccess && docNameValue) {
                // User has access - show clickable document link
                const docLink = createElement('a', 'related-link', docNameValue);
                docLink.href = '#';
                docLink.style.cursor = 'pointer';
                docLink.style.textDecoration = 'underline';
                docLink.style.color = '#0066cc';

                // Add click handler to fetch and display document
                docLink.addEventListener('click', async (e) => {
                    e.preventDefault();
                    try {
                        const allCitations = group.citations.map(c => c.citation).filter(Boolean);
                        if (isTextPreviewableDocument(docLinkValue)) {
                            await showDocument(docNameValue, docLinkValue, allCitations);
                        } else {
                            downloadDocument(docNameValue, docLinkValue);
                        }
                    } catch (error) {
                        console.error('Error opening document:', error);
                        alert('Error loading document: ' + error.message);
                    }
                });

                relatedItem.appendChild(docLink);
            } else if (docNameValue) {
                // User doesn't have access - show document name but not as link
                const docName = createElement('span', 'related-link');
                docName.textContent = docNameValue;
                docName.style.color = '#999';
                docName.style.fontStyle = 'italic';
                if (!hasAccess) {
                    docName.title = 'You do not have access to this document';
                }
                relatedItem.appendChild(docName);
            }

            // Show all citations for this document (wrapped, no truncation)
            const citationList = createElement('div', 'related-citation-list');
            citationList.style.marginTop = '0.35rem';
            group.citations.forEach((citation, index) => {
                if (citation.citation) {
                    const citationEntry = createElement('div', 'related-citation-entry');
                    if (index > 0) {
                        citationEntry.classList.add('related-citation-entry-separated');
                    }

                    const citationText = createElement('div', 'related-citation');
                    citationText.textContent = citation.citation;
                    citationEntry.appendChild(citationText);

                    if (citation.score !== undefined && citation.score !== null) {
                        const citationScore = createElement('div', 'related-citation-relevance');
                        citationScore.textContent = `Relevance: ${(citation.score * 100).toFixed(1)}%`;
                        citationEntry.appendChild(citationScore);
                    }

                    citationList.appendChild(citationEntry);
                }
            });
            relatedItem.appendChild(citationList);

            relatedDocsList.appendChild(relatedItem);
        });
    } else {
        const emptyMsg = createElement('div', 'related-empty', 'No related company documents');
        relatedDocsList.appendChild(emptyMsg);
    }

    relatedDocsSection.appendChild(relatedDocsList);
    detail.appendChild(relatedDocsSection);

    // Pull Requests
    const pullRequestsSection = createElement('div', 'ticket-section related-section');
    pullRequestsSection.innerHTML = '<h4>Pull Requests</h4>';
    const pullRequestsList = createElement('div', 'related-list');
    const pullRequests = Array.isArray(ticket.pullRequests) ? ticket.pullRequests : [];

    if (pullRequests.length > 0) {
        pullRequests.forEach(pr => {
            const prItem = createElement('div', 'related-item pr-item');
            const prLink = createElement('a', 'related-link', pr.prUrl);
            prLink.href = pr.prUrl;
            prLink.target = '_blank';
            prLink.rel = 'noopener noreferrer';
            prItem.appendChild(prLink);

            if (pr.aiGenerated) {
                const aiBadge = createElement('span', 'pr-ai-badge', 'AI');
                prItem.appendChild(aiBadge);
            }

            pullRequestsList.appendChild(prItem);
        });
    } else {
        const emptyPrs = createElement('div', 'related-empty', 'No pull requests yet');
        pullRequestsList.appendChild(emptyPrs);
    }

    pullRequestsSection.appendChild(pullRequestsList);

    const addPrForm = createElement('div', 'form-group');
    const addPrLabel = createElement('label', 'form-label');
    addPrLabel.textContent = 'Add PR link manually';
    const addPrInput = createElement('input', 'form-input');
    addPrInput.placeholder = 'https://github.com/org/repo/pull/123';
    const addPrBtn = createElement('button', 'btn btn-primary', 'Add PR');
    addPrBtn.style.marginTop = '0.5rem';
    addPrBtn.addEventListener('click', async () => {
        const prUrl = addPrInput.value.trim();
        if (!prUrl) {
            alert('Please enter a PR URL');
            return;
        }
        try {
            await addPullRequest(ticket.id, prUrl);
            addPrInput.value = '';
            await loadTicketDetail(ticket.id);
        } catch (error) {
            alert('Error adding PR: ' + error.message);
        }
    });
    addPrForm.appendChild(addPrLabel);
    addPrForm.appendChild(addPrInput);
    addPrForm.appendChild(addPrBtn);
    pullRequestsSection.appendChild(addPrForm);
    detail.appendChild(pullRequestsSection);

    // Comments
    const commentsSection = createElement('div', 'ticket-section');
    commentsSection.innerHTML = '<h4>Comments</h4>';
    const commentsList = createElement('div', 'comments-list');

    if (ticket.comments && ticket.comments.length > 0) {
        ticket.comments.forEach(comment => {
            const commentItem = createElement('div', 'comment-item');
            const commentHeader = createElement('div', 'comment-header');
            commentHeader.innerHTML = `<span>${comment.authorId}</span> <span>${formatDateTime(comment.createdAt)}</span>`;
            const commentBody = createElement('div', 'comment-body', comment.body);
            commentItem.appendChild(commentHeader);
            commentItem.appendChild(commentBody);
            commentsList.appendChild(commentItem);
        });
    }

    // Add comment form
    const commentForm = createElement('div', 'form-group');
    const commentTextarea = createElement('textarea', 'form-textarea');
    commentTextarea.placeholder = 'Add a comment...';
    const addCommentBtn = createElement('button', 'btn btn-primary', 'Add Comment');
    addCommentBtn.addEventListener('click', async () => {
        if (!commentTextarea.value.trim()) {
            alert('Please enter a comment');
            return;
        }
        try {
            await addComment(ticket.id, state.currentUserId, commentTextarea.value);
            commentTextarea.value = '';
            alert('Comment added!');
            await loadTicketDetail(ticket.id);
        } catch (error) {
            alert('Error adding comment: ' + error.message);
        }
    });
    commentForm.appendChild(commentTextarea);
    commentForm.appendChild(addCommentBtn);
    commentsSection.appendChild(commentsList);
    commentsSection.appendChild(commentForm);

    detail.appendChild(commentsSection);

    container.appendChild(detail);
}

export async function loadTicketDetail(ticketId) {
    try {
        const {getTicket} = await import('../api/ticketsApi.js');
        const ticket = await getTicket(ticketId);
        renderTicketDetail(ticket);
        // Update navigation buttons after loading
        if (window.updateNavButtons) {
            window.updateNavButtons();
        }
    } catch (error) {
        console.error('Error loading ticket detail:', error);
    }
}

/**
 * Show document content in a modal/overlay
 */
async function showDocument(documentName, documentLink, citations) {
    const documentToFetch = documentLink || documentName;

    if (!documentToFetch) {
        throw new Error('Document name or link is required');
    }

    try {
        console.log('Fetching document:', documentToFetch, '(name:', documentName + ')');
        // fetchDocumentContent will extract the filename from the link if needed
        const content = await fetchDocumentContent(documentToFetch);

        // Create modal overlay
        const overlay = createElement('div', 'document-overlay');
        overlay.style.position = 'fixed';
        overlay.style.top = '0';
        overlay.style.left = '0';
        overlay.style.width = '100%';
        overlay.style.height = '100%';
        overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.7)';
        overlay.style.zIndex = '10000';
        overlay.style.display = 'flex';
        overlay.style.alignItems = 'center';
        overlay.style.justifyContent = 'center';

        // Create modal content
        const modal = createElement('div', 'document-modal');
        modal.style.backgroundColor = 'white';
        modal.style.padding = '2rem';
        modal.style.borderRadius = '8px';
        modal.style.maxWidth = '80%';
        modal.style.maxHeight = '80%';
        modal.style.overflow = 'auto';
        modal.style.boxShadow = '0 4px 6px rgba(0, 0, 0, 0.3)';

        // Header with close button
        const header = createElement('div', 'document-header');
        header.style.display = 'flex';
        header.style.justifyContent = 'space-between';
        header.style.alignItems = 'center';
        header.style.marginBottom = '1rem';
        header.style.borderBottom = '1px solid #ddd';
        header.style.paddingBottom = '0.5rem';

        const title = createElement('h3', 'document-title', documentName);
        title.style.margin = '0';

        const closeBtn = createElement('button', 'btn btn-secondary', 'Close');
        closeBtn.style.marginLeft = '1rem';
        closeBtn.addEventListener('click', () => {
            document.body.removeChild(overlay);
        });

        header.appendChild(title);
        header.appendChild(closeBtn);

        // Document content
        const contentDiv = createElement('div', 'document-content');
        contentDiv.style.fontSize = '0.9rem';
        contentDiv.style.lineHeight = '1.5';
        contentDiv.style.maxWidth = '100%';
        contentDiv.style.overflowWrap = 'anywhere';

        const isMarkdown = (documentName || '').toLowerCase().endsWith('.md');
        if (isMarkdown) {
            contentDiv.classList.add('markdown-content');
            contentDiv.innerHTML = renderMarkdownToHtml(content);
        } else {
            contentDiv.style.whiteSpace = 'pre-wrap';
            contentDiv.style.fontFamily = 'monospace';
            contentDiv.textContent = content;
        }

        modal.appendChild(header);
        modal.appendChild(contentDiv);
        overlay.appendChild(modal);

        const highlightedMarks = highlightCitationsInElement(contentDiv, citations || []);
        if (highlightedMarks.length > 0) {
            highlightedMarks[0].scrollIntoView({block: 'center', behavior: 'smooth'});
        }

        // Close on overlay click
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                document.body.removeChild(overlay);
            }
        });

        // Close on Escape key
        const escapeHandler = (e) => {
            if (e.key === 'Escape') {
                document.body.removeChild(overlay);
                document.removeEventListener('keydown', escapeHandler);
            }
        };
        document.addEventListener('keydown', escapeHandler);

        document.body.appendChild(overlay);
    } catch (error) {
        console.error('Error showing document:', error);
        // Show error in a visible alert with more details
        const errorMsg = error.message || 'Unknown error';
        alert('Error loading document "' + documentName + '": ' + errorMsg + '\n\nPlease check:\n- Document name: ' + documentName + '\n- Documents service/proxy availability');
        throw error; // Re-throw so caller can handle it
    }
}

function downloadDocument(documentName, documentLink) {
    const url = getDocumentDownloadUrl(documentLink || documentName);
    const a = createElement('a');
    a.href = url;
    a.download = documentName || '';
    document.body.appendChild(a);
    a.click();
    a.remove();
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value || '';
    return div.innerHTML;
}

function stripBoundaryQuotes(text) {
    return (text || '').replace(/^[\s"'`“”‘’]+|[\s"'`“”‘’]+$/g, '');
}

function normalizeCitationForSearch(citation) {
    return (citation || '')
        .replace(/\r\n/g, '\n')
        .split('\n')
        .map(line => line
            .replace(/^\s*[-*+]\s+/, '')
            .replace(/^\s*\d+\.\s+/, '')
            .trim())
        .filter(Boolean)
        .join(' ');
}

function isMarkdownControlChar(ch) {
    return ch === '*' || ch === '_' || ch === '`' || ch === '~' || ch === '#' || ch === '>' ||
        ch === '[' || ch === ']' || ch === '(' || ch === ')' || ch === '!' || ch === '|' ||
        ch === '-' || ch === '+';
}

function isIgnorableSearchChar(ch) {
    return isMarkdownControlChar(ch) ||
        ch === '.' || ch === ',' || ch === ':' || ch === ';' || ch === '?' || ch === '"' ||
        ch === '\'' || ch === '/' || ch === '\\' || ch === '{' || ch === '}' || ch === '=' ||
        ch === '&';
}

function buildSearchProjection(text, stripFormattingChars) {
    const chars = [];
    const map = [];
    let previousWasSpace = false;

    for (let i = 0; i < text.length; i += 1) {
        const ch = text[i];
        if (stripFormattingChars && isIgnorableSearchChar(ch)) {
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
    return {projected: chars.join(''), map};
}

function findCitationRangeInText(text, citation) {
    const citationText = stripBoundaryQuotes(normalizeCitationForSearch(citation)).trim();
    if (!citationText || citationText.length < 6) {
        return null;
    }

    const exactIndex = text.toLowerCase().indexOf(citationText.toLowerCase());
    if (exactIndex >= 0) {
        return {start: exactIndex, end: exactIndex + citationText.length};
    }

    const textProjection = buildSearchProjection(text, true);
    const citationProjection = buildSearchProjection(citationText, true);
    if (!citationProjection.projected || citationProjection.projected.length < 6) {
        return null;
    }

    const projectedIndex = textProjection.projected.indexOf(citationProjection.projected);
    if (projectedIndex < 0) {
        return null;
    }

    const start = textProjection.map[projectedIndex];
    const end = textProjection.map[projectedIndex + citationProjection.projected.length - 1];
    if (!Number.isInteger(start) || !Number.isInteger(end)) {
        return null;
    }
    return {start, end: end + 1};
}

function normalizeCitationList(citations) {
    if (Array.isArray(citations)) {
        return citations.filter(Boolean);
    }
    if (typeof citations === 'string' && citations.trim() !== '') {
        return [citations];
    }
    return [];
}

function findCitationRangesInText(text, citations) {
    const ranges = normalizeCitationList(citations)
        .map(citation => findCitationRangeInText(text, citation))
        .filter(Boolean)
        .sort((a, b) => a.start - b.start);

    if (ranges.length === 0) {
        return [];
    }

    const merged = [ranges[0]];
    for (let i = 1; i < ranges.length; i += 1) {
        const current = ranges[i];
        const previous = merged[merged.length - 1];
        if (current.start <= previous.end) {
            previous.end = Math.max(previous.end, current.end);
        } else {
            merged.push(current);
        }
    }
    return merged;
}

function highlightCitationsInElement(root, citations) {
    const ranges = findCitationRangesInText(root.textContent || '', citations);
    if (ranges.length === 0) {
        return [];
    }

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    const segments = [];
    let total = 0;
    while (walker.nextNode()) {
        const node = walker.currentNode;
        const length = node.nodeValue.length;
        if (length > 0) {
            segments.push({node, start: total, end: total + length});
            total += length;
        }
    }

    const createdMarks = [];
    for (let r = ranges.length - 1; r >= 0; r -= 1) {
        const range = ranges[r];
        for (let i = segments.length - 1; i >= 0; i -= 1) {
            const segment = segments[i];
            if (segment.end <= range.start || segment.start >= range.end) {
                continue;
            }
            const localStart = Math.max(0, range.start - segment.start);
            const localEnd = Math.min(segment.end - segment.start, range.end - segment.start);
            if (localStart >= localEnd) {
                continue;
            }

            let targetNode = segment.node;
            if (localEnd < targetNode.nodeValue.length) {
                targetNode.splitText(localEnd);
            }
            if (localStart > 0) {
                targetNode = targetNode.splitText(localStart);
            }

            const mark = document.createElement('mark');
            mark.className = 'doc-citation-highlight';
            targetNode.parentNode.replaceChild(mark, targetNode);
            mark.appendChild(targetNode);
            createdMarks.push(mark);
        }
    }

    return createdMarks.sort((a, b) => {
        const pos = a.compareDocumentPosition(b);
        return (pos & Node.DOCUMENT_POSITION_FOLLOWING) ? -1 : 1;
    });
}

function renderMarkdownInline(text) {
    let html = escapeHtml(text);
    html = html.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
    html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
    html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>');
    html = html.replace(/~~([^~]+)~~/g, '<del>$1</del>');
    return html;
}

function renderMarkdownToHtml(markdown) {
    const lines = (markdown || '').replace(/\r\n/g, '\n').split('\n');
    const out = [];
    let paragraph = [];
    let inCode = false;
    let inUl = false;
    let inOl = false;

    const flushParagraph = () => {
        if (paragraph.length === 0) return;
        out.push('<p>' + paragraph.join('<br>') + '</p>');
        paragraph = [];
    };

    const closeLists = () => {
        if (inUl) {
            out.push('</ul>');
            inUl = false;
        }
        if (inOl) {
            out.push('</ol>');
            inOl = false;
        }
    };

    for (const line of lines) {
        if (line.startsWith('```')) {
            flushParagraph();
            closeLists();
            if (!inCode) {
                out.push('<pre><code>');
                inCode = true;
            } else {
                out.push('</code></pre>');
                inCode = false;
            }
            continue;
        }

        if (inCode) {
            out.push(escapeHtml(line));
            continue;
        }

        if (line.trim() === '') {
            flushParagraph();
            closeLists();
            continue;
        }

        const heading = /^(#{1,6})\s+(.+)$/.exec(line);
        if (heading) {
            flushParagraph();
            closeLists();
            const level = heading[1].length;
            out.push(`<h${level}>${renderMarkdownInline(heading[2])}</h${level}>`);
            continue;
        }

        const unordered = /^\s*[-*]\s+(.+)$/.exec(line);
        if (unordered) {
            flushParagraph();
            if (inOl) {
                out.push('</ol>');
                inOl = false;
            }
            if (!inUl) {
                out.push('<ul>');
                inUl = true;
            }
            out.push('<li>' + renderMarkdownInline(unordered[1]) + '</li>');
            continue;
        }

        const ordered = /^\s*\d+\.\s+(.+)$/.exec(line);
        if (ordered) {
            flushParagraph();
            if (inUl) {
                out.push('</ul>');
                inUl = false;
            }
            if (!inOl) {
                out.push('<ol>');
                inOl = true;
            }
            out.push('<li>' + renderMarkdownInline(ordered[1]) + '</li>');
            continue;
        }

        const quote = /^\s*>\s?(.+)$/.exec(line);
        if (quote) {
            flushParagraph();
            closeLists();
            out.push('<blockquote><p>' + renderMarkdownInline(quote[1]) + '</p></blockquote>');
            continue;
        }

        closeLists();
        paragraph.push(renderMarkdownInline(line));
    }

    flushParagraph();
    closeLists();
    if (inCode) {
        out.push('</code></pre>');
    }
    return out.join('\n');
}

function groupCitationsByDocument(policyCitations) {
    const groups = new Map();
    (policyCitations || []).forEach(citation => {
        const documentName = citation.documentName || '';
        const documentLink = citation.documentLink || documentName;
        const key = `${documentName}||${documentLink}`;
        if (!groups.has(key)) {
            groups.set(key, {
                documentName,
                documentLink,
                citations: []
            });
        }
        groups.get(key).citations.push(citation);
    });
    return Array.from(groups.values());
}
