import {
    acceptTicket,
    addComment,
    rejectAndReturnToDispatch,
    rollbackToRequest,
    updateTicketStatus
} from '../api/ticketsApi.js';
import {state} from './state.js';
import {formatDateTime} from '../util/format.js';
import {$, clearElement, createElement} from '../util/dom.js';
import {renderTickets} from './renderTickets.js';
import {getActorContext} from './actorContext.js';
import {fetchDocumentContent} from '../api/documentsApi.js';

export function renderTicketDetail(ticket) {
    const container = $('#detail-pane');
    if (!container) return;

    clearElement(container);

    const detail = createElement('div', 'ticket-detail');

    // Header
    const header = createElement('div', 'ticket-header');
    const title = createElement('h2', 'ticket-title', `Ticket #${ticket.id}`);
    header.appendChild(title);

    const meta = createElement('div', 'ticket-meta');
    meta.innerHTML = `
        <span><strong>Type:</strong> ${ticket.ticketType}</span>
        <span><strong>Status:</strong> ${ticket.status}</span>
        <span><strong>Source:</strong> ${ticket.source}</span>
        <span><strong>Team:</strong> <span style="color: #666; font-style: italic;">${ticket.assignedTeam}</span> <small style="color: #999;">(auto-assigned)</small></span>
        ${ticket.assignedTo ? `<span><strong>Assigned to:</strong> ${ticket.assignedTo}</span>` : ''}
        ${ticket.urgencyFlag ? '<span><strong>URGENT</strong></span>' : ''}
        ${ticket.urgencyScore ? `<span><strong>Urgency Score:</strong> ${ticket.urgencyScore}/10</span>` : ''}
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

    // Accept button (for FROM_AI or FROM_DISPATCH)
    if (ticket.status === 'FROM_AI' || ticket.status === 'FROM_DISPATCH') {
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
            if (confirm('Are you sure you want to convert this AI ticket back to an incoming request? The ticket will be hidden from normal views.')) {
                try {
                    await rollbackToRequest(ticket.id);
                    alert('Ticket converted back to request! It will reappear in the dispatcher inbox.');
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
            }
        });
        actionsDiv.appendChild(rollbackBtn);
    }

    // Reject & Return to Dispatch (for FROM_AI without incoming request)
    if (ticket.status === 'FROM_AI' && ticket.rollbackAllowed && !ticket.incomingRequestId) {
        const rejectBtn = createElement('button', 'btn btn-danger', 'Reject & Return to Dispatch');
        rejectBtn.addEventListener('click', async () => {
            if (confirm('Are you sure you want to reject and return this ticket to dispatch?')) {
                try {
                    await rejectAndReturnToDispatch(ticket.id);
                    alert('Ticket returned to dispatch!');
                    await renderTickets('inbox');
                    await loadTicketDetail(ticket.id);
                } catch (error) {
                    alert('Error rejecting ticket: ' + error.message);
                }
            }
        });
        actionsDiv.appendChild(rejectBtn);
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
            {value: 'OTHER', label: 'Other (Needs Dispatcher Review)', group: 'Unclassified'}
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
    if (ticket.status === 'TRIAGED' || ticket.status === 'IN_PROGRESS' || ticket.status === 'WAITING_ON_USER') {
        const statusSelect = createElement('select', 'status-select');
        const statuses = ['TRIAGED', 'IN_PROGRESS', 'WAITING_ON_USER', 'COMPLETED'];
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
                await updateTicketStatus(ticket.id, {status: statusSelect.value});
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
            relatedLink.href = '#';
            relatedLink.addEventListener('click', async (e) => {
                e.preventDefault();
                // Switch to inbox tab first
                const {switchTab} = await import('./router.js');
                switchTab('inbox');
                // Wait a bit for the list to render, then select and load the ticket
                setTimeout(async () => {
                    // Select the ticket in the list
                    const listItem = document.querySelector(`.list-item[data-ticket-id="${ticketId}"]`);
                    if (listItem) {
                        document.querySelectorAll('.list-item').forEach(el => {
                            el.classList.remove('selected');
                        });
                        listItem.classList.add('selected');
                        state.selectedTicketId = ticketId;
                        // Scroll to the selected item
                        listItem.scrollIntoView({behavior: 'smooth', block: 'center'});
                    }
                    // Add to navigation history
                    state.addTicketToHistory(ticketId);
                    if (window.updateNavButtons) {
                        window.updateNavButtons();
                    }
                    // Load the ticket detail
                    await loadTicketDetail(ticketId);
                }, 100);
            });
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
    const relatedDocsList = createElement('div', 'related-list');

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
        policyCitations.forEach(citation => {
            const relatedItem = createElement('div', 'related-item');

            // Check RBAC: only show document link if user's team is in rbacTeams
            const hasAccess = citation.rbacTeams &&
                Array.isArray(citation.rbacTeams) &&
                citation.rbacTeams.some(team => team.toLowerCase() === userTeam);

            // Debug logging
            if (citation.documentName) {
                console.log('Document:', citation.documentName, 'User team:', userTeam, 'RBAC teams:', citation.rbacTeams, 'Has access:', hasAccess);
            }

            if (hasAccess && citation.documentName) {
                // User has access - show clickable document link
                const docLink = createElement('a', 'related-link', citation.documentName || 'Document');
                docLink.href = '#';
                docLink.style.cursor = 'pointer';
                docLink.style.textDecoration = 'underline';
                docLink.style.color = '#0066cc';

                // Add click handler to fetch and display document
                docLink.addEventListener('click', async (e) => {
                    e.preventDefault();
                    try {
                        await showDocument(citation.documentName, citation.documentLink);
                    } catch (error) {
                        console.error('Error opening document:', error);
                        alert('Error loading document: ' + error.message);
                    }
                });

                relatedItem.appendChild(docLink);
            } else if (citation.documentName) {
                // User doesn't have access - show document name but not as link
                const docName = createElement('span', 'related-link');
                docName.textContent = citation.documentName;
                docName.style.color = '#999';
                docName.style.fontStyle = 'italic';
                if (!hasAccess) {
                    docName.title = 'You do not have access to this document';
                }
                relatedItem.appendChild(docName);
            }

            // Show citation preview
            if (citation.citation) {
                const quote = createElement('div', 'related-quote');
                quote.style.fontSize = '0.85rem';
                quote.style.color = '#666';
                quote.style.marginTop = '0.25rem';
                quote.style.fontStyle = 'italic';
                quote.textContent = `"${citation.citation.substring(0, 100)}${citation.citation.length > 100 ? '...' : ''}"`;
                relatedItem.appendChild(quote);
            }

            // Show relevance score if available
            if (citation.score !== undefined && citation.score !== null) {
                const score = createElement('div', 'related-score');
                score.style.fontSize = '0.75rem';
                score.style.color = '#999';
                score.style.marginTop = '0.25rem';
                score.textContent = `Relevance: ${(citation.score * 100).toFixed(1)}%`;
                relatedItem.appendChild(score);
            }

            relatedDocsList.appendChild(relatedItem);
        });
    } else {
        const emptyMsg = createElement('div', 'related-empty', 'No related company documents');
        relatedDocsList.appendChild(emptyMsg);
    }

    relatedDocsSection.appendChild(relatedDocsList);
    detail.appendChild(relatedDocsSection);

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
async function showDocument(documentName, documentLink) {
    // Use documentLink if available (it might have the full path), otherwise use documentName
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
        contentDiv.style.whiteSpace = 'pre-wrap';
        contentDiv.style.fontFamily = 'monospace';
        contentDiv.style.fontSize = '0.9rem';
        contentDiv.style.lineHeight = '1.5';
        contentDiv.textContent = content;

        modal.appendChild(header);
        modal.appendChild(contentDiv);
        overlay.appendChild(modal);

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
        alert('Error loading document "' + documentName + '": ' + errorMsg + '\n\nPlease check:\n- Document name: ' + documentName + '\n- API endpoint: http://localhost:8084/api/documents/content/' + encodeURIComponent(documentName));
        throw error; // Re-throw so caller can handle it
    }
}
