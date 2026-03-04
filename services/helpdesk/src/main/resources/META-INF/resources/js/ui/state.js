let currentTab = 'dispatcher';
let selectedTicketId = null;
let selectedRequestId = null;
let currentUserId = 'demo-user';

// Ticket navigation history for back/forward
let ticketHistory = [];
let ticketHistoryIndex = -1;

export const state = {
    get currentTab() {
        return currentTab;
    },
    set currentTab(value) {
        currentTab = value;
    },
    get selectedTicketId() {
        return selectedTicketId;
    },
    set selectedTicketId(value) {
        selectedTicketId = value;
    },
    get selectedRequestId() {
        return selectedRequestId;
    },
    set selectedRequestId(value) {
        selectedRequestId = value;
    },
    get currentUserId() {
        return currentUserId;
    },
    set currentUserId(value) {
        currentUserId = value;
    },
    // Add ticket to history
    addTicketToHistory(ticketId) {
        // Remove any tickets after current index (when navigating back then clicking new ticket)
        if (ticketHistoryIndex < ticketHistory.length - 1) {
            ticketHistory = ticketHistory.slice(0, ticketHistoryIndex + 1);
        }
        // Add new ticket (avoid duplicates if same as current)
        if (ticketHistory.length === 0 || ticketHistory[ticketHistory.length - 1] !== ticketId) {
            ticketHistory.push(ticketId);
            ticketHistoryIndex = ticketHistory.length - 1;
        } else {
            ticketHistoryIndex = ticketHistory.length - 1;
        }
    },
    // Navigate back
    navigateBack() {
        if (ticketHistoryIndex > 0) {
            ticketHistoryIndex--;
            return ticketHistory[ticketHistoryIndex];
        }
        return null;
    },
    // Navigate forward
    navigateForward() {
        if (ticketHistoryIndex < ticketHistory.length - 1) {
            ticketHistoryIndex++;
            return ticketHistory[ticketHistoryIndex];
        }
        return null;
    },
    // Check if can navigate back
    canNavigateBack() {
        return ticketHistoryIndex > 0;
    },
    // Check if can navigate forward
    canNavigateForward() {
        return ticketHistoryIndex < ticketHistory.length - 1;
    },
};
