import {getIncomingRequests} from '../api/requestsApi.js';
import {submitTicket} from '../api/dispatchApi.js';
import {formatDateTime} from '../util/format.js';
import {$, clearElement, createElement} from '../util/dom.js';
import {state} from './state.js';

export async function renderDispatcherInbox() {
    console.log('renderDispatcherInbox called');
    const container = $('#list-content');
    if (!container) {
        console.error('Container #list-content not found');
        return;
    }

    container.innerHTML = '<div class="loading">Loading incoming requests...</div>';

    try {
        console.log('Fetching incoming requests...');
        // Get dispatcher inbox requests (NEW and RETURNED_FROM_AI)
        // Pass null to get all dispatcher inbox requests
        const requests = await getIncomingRequests(null);
        console.log('Received requests:', requests);

        // Always clear the loading message first - do this immediately after API call
        clearElement(container);

        if (!requests || !Array.isArray(requests) || requests.length === 0) {
            const emptyMsg = createElement('div', 'loading');
            emptyMsg.textContent = 'No new incoming requests';
            emptyMsg.style.color = '#666';
            emptyMsg.style.fontStyle = 'italic';
            container.appendChild(emptyMsg);
            return;
        }

        // Render requests - invert order: prepend items so newest appear at top
        requests.forEach(request => {
            try {
                const item = createRequestListItem(request);
                container.insertBefore(item, container.firstChild);
            } catch (itemError) {
                console.error('Error creating request item:', itemError, request);
            }
        });
    } catch (error) {
        console.error('Error in renderDispatcherInbox:', error);
        // Ensure loading message is cleared even on error
        if (container.innerHTML.includes('Loading')) {
            clearElement(container);
        }
        const errorMsg = createElement('div', 'loading');
        errorMsg.textContent = `Error: ${error.message}`;
        errorMsg.style.color = '#f44336';
        container.appendChild(errorMsg);
    }
}

function createRequestListItem(request) {
    const item = createElement('div', 'list-item');
    item.dataset.requestId = request.id;

    const header = createElement('div', 'list-item-header');
    const title = createElement('div', 'list-item-title', `Request #${request.id} - ${request.userId}`);
    header.appendChild(title);

    const meta = createElement('div', 'list-item-meta');
    meta.textContent = `${request.channel} • ${formatDateTime(request.createdAt)}`;

    const preview = createElement('div', 'list-item-preview');
    preview.textContent = request.rawText.substring(0, 100) + (request.rawText.length > 100 ? '...' : '');

    item.appendChild(header);
    item.appendChild(meta);
    item.appendChild(preview);

    item.addEventListener('click', () => {
        document.querySelectorAll('.list-item').forEach(el => {
            el.classList.remove('selected');
        });
        item.classList.add('selected');
        state.selectedRequestId = request.id;
        showDispatchForm(request);
    });

    return item;
}

function showDispatchForm(request) {
    const container = $('#detail-pane');
    if (!container) return;

    clearElement(container);

    const form = createElement('div', 'ticket-detail');
    form.innerHTML = '<h2>Dispatch Request</h2>';

    const originalSection = createElement('div', 'ticket-section');
    originalSection.innerHTML = '<h4>Original Request</h4>';
    const originalText = createElement('div', 'ticket-original-request', request.rawText);
    originalSection.appendChild(originalText);
    form.appendChild(originalSection);

    const dispatchForm = createElement('form');
    dispatchForm.innerHTML = `
        <div class="form-group">
            <label class="form-label">Ticket Type</label>
            <select class="form-select" id="dispatch-ticket-type" required>
                <option value="">Select type</option>
                <optgroup label="Billing">
                    <option value="BILLING_REFUND">Billing - Refund</option>
                    <option value="BILLING_OTHER">Billing - Other</option>
                </optgroup>
                <optgroup label="Scheduling">
                    <option value="SCHEDULING_CANCELLATION">Scheduling - Cancellation</option>
                    <option value="SCHEDULING_OTHER">Scheduling - Other</option>
                </optgroup>
                <optgroup label="Account / General Support">
                    <option value="ACCOUNT_ACCESS">Account - Access</option>
                    <option value="SUPPORT_OTHER">Support - Other</option>
                </optgroup>
                <optgroup label="Bugs / Engineering">
                    <option value="BUG_APP">Bug - App</option>
                    <option value="BUG_BACKEND">Bug - Backend</option>
                    <option value="ENGINEERING_OTHER">Engineering - Other</option>
                </optgroup>
                <optgroup label="Unclassified">
                    <option value="OTHER">Other (Needs Dispatcher Review)</option>
                </optgroup>
            </select>
            <small style="color: #666; display: block; margin-top: 4px;">Team will be assigned automatically based on ticket type</small>
        </div>
        <div class="form-group">
            <label class="form-label">
                <input type="checkbox" id="dispatch-urgency"> Urgent <span style="font-weight: normal; color: #666;">(Urgency Score: 1-10)</span>
            </label>
        </div>
        <div class="form-group">
            <label class="form-label">Urgency Score (optional, 1-10)</label>
            <input type="number" class="form-input" id="dispatch-urgency-score" min="1" max="10" step="1">
        </div>
        <div class="form-group">
            <label class="form-label">Notes (optional)</label>
            <textarea class="form-textarea" id="dispatch-notes"></textarea>
        </div>
        <div class="ticket-actions">
            <button type="submit" class="btn btn-primary">Submit Ticket</button>
        </div>
    `;

    dispatchForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const dispatchData = {
            incomingRequestId: request.id,
            ticketType: document.getElementById('dispatch-ticket-type').value,
            // assignedTeam is derived automatically from ticketType - not sent
            urgencyFlag: document.getElementById('dispatch-urgency').checked,
            urgencyScore: document.getElementById('dispatch-urgency-score').value ?
                parseFloat(document.getElementById('dispatch-urgency-score').value) : null,
            dispatcherId: state.currentUserId,
            notes: document.getElementById('dispatch-notes').value,
        };

        try {
            await submitTicket(dispatchData);
            alert('Ticket created successfully!');
            await renderDispatcherInbox();
            container.innerHTML = '<div class="detail-placeholder"><p>Ticket created. Select another request to dispatch.</p></div>';
        } catch (error) {
            alert('Error creating ticket: ' + error.message);
        }
    });

    form.appendChild(dispatchForm);
    container.appendChild(form);
}
