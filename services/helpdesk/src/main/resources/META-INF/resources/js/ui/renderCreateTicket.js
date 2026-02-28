import {createTicketFromAi, createTicketManual} from '../api/ticketsApi.js';
import {$, clearElement, createElement} from '../util/dom.js';
import {switchTab} from './router.js';

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

    const createForm = createElement('form');
    createForm.innerHTML = `
        <div class="form-group">
            <label class="form-label">Source</label>
            <select class="form-select" id="create-source" required>
                <option value="MANUAL">Manual</option>
                <option value="AI">AI</option>
            </select>
        </div>
        <div class="form-group">
            <label class="form-label">User ID</label>
            <input type="text" class="form-input" id="create-user-id" required>
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
                    <option value="OTHER">Other (Needs Dispatcher Review)</option>
                </optgroup>
            </select>
            <small style="color: #666; display: block; margin-top: 4px;">Team will be assigned automatically based on ticket type</small>
        </div>
        <div class="form-group" id="ai-fields" style="display: none;">
            <label class="form-label">AI Confidence (0-1)</label>
            <input type="number" class="form-input" id="create-ai-confidence" min="0" max="1" step="0.01">
        </div>
        <div class="form-group">
            <label class="form-label">
                <input type="checkbox" id="create-urgency"> Urgent <span style="font-weight: normal; color: #666;">(Urgency Score: 1-10)</span>
            </label>
        </div>
        <div class="form-group">
            <label class="form-label">Urgency Score (optional, 1-10)</label>
            <input type="number" class="form-input" id="create-urgency-score" min="1" max="10" step="1">
        </div>
        <div class="ticket-actions">
            <button type="submit" class="btn btn-primary">Create Ticket</button>
        </div>
    `;

    // Show/hide AI fields based on source
    const sourceSelect = createForm.querySelector('#create-source');
    const aiFields = createForm.querySelector('#ai-fields');
    sourceSelect.addEventListener('change', () => {
        aiFields.style.display = sourceSelect.value === 'AI' ? 'block' : 'none';
    });

    createForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const source = document.getElementById('create-source').value;

        if (source === 'AI') {
            const aiData = {
                userId: document.getElementById('create-user-id').value,
                originalRequest: document.getElementById('create-original-request').value,
                ticketType: document.getElementById('create-ticket-type').value,
                // assignedTeam is derived automatically from ticketType - not sent
                urgencyFlag: document.getElementById('create-urgency').checked,
                urgencyScore: document.getElementById('create-urgency-score').value ?
                    parseFloat(document.getElementById('create-urgency-score').value) : null,
                aiConfidence: parseFloat(document.getElementById('create-ai-confidence').value) || 0.8,
            };

            try {
                await createTicketFromAi(aiData);
                alert('AI ticket created successfully!');
                switchTab('inbox');
            } catch (error) {
                alert('Error creating ticket: ' + error.message);
            }
        } else {
            const manualData = {
                userId: document.getElementById('create-user-id').value,
                originalRequest: document.getElementById('create-original-request').value,
                ticketType: document.getElementById('create-ticket-type').value,
                // assignedTeam is derived automatically from ticketType - not sent
                status: 'FROM_DISPATCH',
                urgencyFlag: document.getElementById('create-urgency').checked,
                urgencyScore: document.getElementById('create-urgency-score').value ?
                    parseFloat(document.getElementById('create-urgency-score').value) : null,
            };

            try {
                await createTicketManual(manualData);
                alert('Ticket created successfully!');
                switchTab('inbox');
            } catch (error) {
                alert('Error creating ticket: ' + error.message);
            }
        }
    });

    form.appendChild(createForm);
    detailContainer.appendChild(form);
}
