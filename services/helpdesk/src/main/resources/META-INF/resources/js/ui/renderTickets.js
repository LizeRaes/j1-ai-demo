import {getTickets} from '../api/ticketsApi.js';
import {loadTicketDetail} from './renderTicketDetail.js';
import {state} from './state.js';
import {formatDateTime} from '../util/format.js';
import {$, clearElement, createElement} from '../util/dom.js';
import {getActorContext} from './actorContext.js';

export async function renderTickets(view) {
    const container = $('#list-content');
    if (!container) return;

    container.innerHTML = '<div class="loading">Loading tickets...</div>';

    try {
        const actorContext = getActorContext();
        const team = view === 'team' ? actorContext.team : null;
        const user = view === 'mine' ? actorContext.actorId : null;
        const tickets = await getTickets(view, team, user);

        clearElement(container);

        if (tickets.length === 0) {
            const emptyMsg = createElement('div', 'loading');
            emptyMsg.textContent = 'No tickets found';
            emptyMsg.style.color = '#666';
            emptyMsg.style.fontStyle = 'italic';
            container.appendChild(emptyMsg);
            return;
        }

        // Invert order: prepend items so newest appear at top
        tickets.forEach(ticket => {
            const item = createTicketListItem(ticket);
            if (state.selectedTicketId && Number(state.selectedTicketId) === Number(ticket.id)) {
                item.classList.add('selected');
            }
            container.insertBefore(item, container.firstChild);
        });
    } catch (error) {
        container.innerHTML = `<div class="loading" style="color: red;">Error: ${error.message}</div>`;
        console.error('Error loading tickets:', error);
    }
}

function createTicketListItem(ticket) {
    const item = createElement('div', 'list-item');
    item.dataset.ticketId = ticket.id;
    const urgencyScore = Number(ticket.urgencyScore);
    const hasUrgencyScore = Number.isFinite(urgencyScore);
    const isCriticalUrgency = hasUrgencyScore && urgencyScore >= 8;
    const isUrgent = hasUrgencyScore && urgencyScore >= 6 && urgencyScore < 8;
    if (isCriticalUrgency) {
        item.classList.add('list-item-critical-urgency');
    }

    const header = createElement('div', 'list-item-header');
    const title = createElement('div', 'list-item-title', `#${ticket.id} - ${ticket.ticketType}`);
    if (isCriticalUrgency) {
        title.classList.add('text-critical-urgency');
    }
    header.appendChild(title);

    const badges = createElement('div');
    if (isCriticalUrgency) {
        const criticalBadge = createElement('span', 'list-item-badge badge-urgency-critical', 'CRITICAL');
        badges.appendChild(criticalBadge);
    } else if (isUrgent) {
        const urgentBadge = createElement('span', 'list-item-badge badge-urgent', 'URGENT');
        badges.appendChild(urgentBadge);
    }
    const statusBadge = createElement('span', 'list-item-badge badge-status', ticket.status);
    badges.appendChild(statusBadge);
    header.appendChild(badges);

    const meta = createElement('div', 'list-item-meta');
    meta.textContent = `${ticket.assignedTeam} • ${formatDateTime(ticket.createdAt)}`;
    if (ticket.assignedTo) {
        meta.textContent += ` • Assigned to: ${ticket.assignedTo}`;
    }

    item.appendChild(header);
    item.appendChild(meta);

    item.addEventListener('click', async () => {
        // Update selection
        document.querySelectorAll('.list-item').forEach(el => {
            el.classList.remove('selected');
        });
        item.classList.add('selected');
        state.selectedTicketId = ticket.id;
        updateTicketDeepLink(state.currentTab, ticket.id);

        // Add to navigation history
        state.addTicketToHistory(ticket.id);
        if (window.updateNavButtons) {
            window.updateNavButtons();
        }

        // Load ticket detail
        await loadTicketDetail(ticket.id);
    });

    return item;
}

function updateTicketDeepLink(tab, ticketId) {
    const url = new URL(window.location.href);
    if (tab) {
        url.searchParams.set('tab', tab);
    }
    if (ticketId) {
        url.searchParams.set('ticketId', String(ticketId));
    } else {
        url.searchParams.delete('ticketId');
    }
    window.history.replaceState({}, '', `${url.pathname}?${url.searchParams.toString()}`);
}
