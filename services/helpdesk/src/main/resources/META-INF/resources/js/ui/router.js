import {state} from './state.js';
import {renderDispatcherInbox} from './renderDispatcherInbox.js';
import {renderTickets} from './renderTickets.js';
import {renderCreateTicket} from './renderCreateTicket.js';
import {$, clearElement} from '../util/dom.js';

// Store current tab globally for refresh
window.currentTab = 'dispatcher';
window.switchTab = switchTab;

export function switchTab(tabName) {
    window.currentTab = tabName;
    state.currentTab = tabName;

    // Clear detail pane when switching tabs
    const detailPane = $('#detail-pane');
    if (detailPane) {
        clearElement(detailPane);
        detailPane.innerHTML = '<div class="detail-placeholder"><p>Select an item to view details</p></div>';
    }

    // Clear selected state
    state.selectedTicketId = null;
    state.selectedRequestId = null;

    // Update tab buttons
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.tab === tabName);
    });

    // Clear list item selections
    document.querySelectorAll('.list-item').forEach(item => {
        item.classList.remove('selected');
    });

    // Update list title
    const titles = {
        dispatcher: 'Dispatcher Inbox',
        inbox: 'All Tickets',
        team: 'My Team',
        mine: 'My Tickets',
        create: 'Create Ticket',
    };
    const titleEl = document.getElementById('list-title');
    if (titleEl) {
        titleEl.textContent = titles[tabName] || 'Tickets';
    }

    // Render appropriate view
    if (tabName === 'dispatcher') {
        renderDispatcherInbox();
    } else if (tabName === 'create') {
        renderCreateTicket();
    } else {
        const viewMap = {
            inbox: 'inbox',
            team: 'team',
            mine: 'mine',
        };
        renderTickets(viewMap[tabName] || 'inbox');
    }
}
