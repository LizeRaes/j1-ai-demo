import {createTicketManual} from '../api/ticketsApi.js';
import {$, clearElement, createElement} from '../util/dom.js';
import {switchTab} from './router.js';
import {getActorContext} from './actorContext.js';
import {state} from './state.js';

export function renderCreateTicket() {
    const listContainer = $('#list-content');
    const detailContainer = $('#detail-pane');

    if (listContainer) {
        listContainer.innerHTML = '<div class="loading">Create a new ticket</div>';
    }

    if (!detailContainer) return;

    clearElement(detailContainer);

    const form = createElement('div', 'ticket-detail');
    form.innerHTML = '<h2>Create Ticket</h2>';
    const actorContext = getActorContext();
    const defaultActorId = (actorContext?.actorId || '').trim() || state.currentUserId || 'demo-user';

    const createForm = createElement('form');
    createForm.innerHTML = `
        <div class="form-group">
            <label class="form-label">Source</label>
            <input type="text" class="form-input" value="Manual" disabled>
        </div>
        <div class="form-group">
            <label class="form-label">User ID</label>
            <input type="text" class="form-input" id="create-user-id" value="${defaultActorId}" required>
        </div>
        <div class="form-group">
            <label class="form-label">Original Request</label>
            <textarea class="form-textarea" id="create-original-request" required></textarea>
        </div>
        <div class="form-group">
            <label class="form-label">Ticket Type</label>
            <select class="form-select" id="create-ticket-type" required>
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
                    <option value="OTHER">Other</option>
                </optgroup>
            </select>
            <small style="color: #666; display: block; margin-top: 4px;">Team will be assigned automatically based on ticket type</small>
        </div>
        <div class="form-group">
            <label class="form-label">Urgency Score (required, 1-10)</label>
            <input type="number" class="form-input" id="create-urgency-score" min="1" max="10" step="0.1" required>
        </div>
        <div class="ticket-actions">
            <button type="submit" class="btn btn-primary">Create Ticket</button>
        </div>
    `;

    createForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const userId = document.getElementById('create-user-id').value.trim();
        const originalRequest = document.getElementById('create-original-request').value.trim();
        const ticketType = document.getElementById('create-ticket-type').value;
        const urgencyScoreRaw = document.getElementById('create-urgency-score').value;
        const urgencyScore = Number.parseFloat(urgencyScoreRaw);

        if (!userId || !originalRequest || !ticketType || Number.isNaN(urgencyScore) || urgencyScore < 1 || urgencyScore > 10) {
            console.warn('Create ticket form validation failed.');
            return;
        }

        const manualData = {
            userId,
            originalRequest,
            ticketType,
            // assignedTeam is derived automatically from ticketType - not sent
            status: 'FROM_DISPATCH',
            urgencyFlag: urgencyScore >= 6,
            urgencyScore,
        };

        try {
            await createTicketManual(manualData);
            switchTab('inbox');
        } catch (error) {
            console.error('Error creating manual ticket:', error);
        }
    });

    form.appendChild(createForm);
    detailContainer.appendChild(form);
}
