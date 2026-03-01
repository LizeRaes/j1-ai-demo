import {switchTab} from './ui/router.js';
import {startEventPolling} from './ui/renderLogs.js';
import {getActorContext, renderActorContext} from './ui/actorContext.js';
import {state} from './ui/state.js';
import {loadTicketDetail} from './ui/renderTicketDetail.js';
import {renderTickets} from './ui/renderTickets.js';

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    try {
        // Initialize actor context
        renderActorContext();
        const actorContext = getActorContext();
        state.currentUserId = actorContext.actorId;

        // Set up tab switching
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                try {
                    switchTab(btn.dataset.tab);
                } catch (error) {
                    console.error('Error switching tab:', error);
                }
            });
        });

        // Filter buttons are now handled in renderLogs.js

        // Setup ticket navigation buttons
        setupTicketNavigation();

        // Start with dispatcher inbox
        switchTab('dispatcher');

        // Apply ticket deep-link if present (e.g. ?tab=inbox&ticketId=123).
        openTicketFromUrl().catch((error) => {
            console.error('Error opening ticket from URL:', error);
        });

        // Start event polling
        startEventPolling();

        // Refresh selected ticket detail when persona changes so RBAC-driven docs update immediately.
        window.addEventListener('actor-context-changed', handleActorContextChanged);
    } catch (error) {
        console.error('Error initializing app:', error);
        document.body.innerHTML = '<div style="padding: 2rem; color: red;">Error loading application. Check console for details.</div>';
    }
});

async function handleActorContextChanged() {
    try {
        const selectedTicketId = state.selectedTicketId;
        if (selectedTicketId) {
            await loadTicketDetail(selectedTicketId);
        }
    } catch (error) {
        console.error('Error refreshing after actor context change:', error);
    }
}

function getDeepLinkState() {
    const url = new URL(window.location.href);
    let tab = url.searchParams.get('tab');
    let ticketId = url.searchParams.get('ticketId');

    // Backward-compatible support for hash links like #inbox?ticketId=123
    if ((!tab || !ticketId) && window.location.hash) {
        const rawHash = window.location.hash.substring(1);
        const [hashTab, hashQuery] = rawHash.split('?');
        if (!tab && hashTab) {
            tab = hashTab;
        }
        if (!ticketId && hashQuery) {
            const params = new URLSearchParams(hashQuery);
            ticketId = params.get('ticketId');
        }
    }

    const parsedTicketId = ticketId ? Number(ticketId) : null;
    return {
        tab: tab || 'inbox',
        ticketId: Number.isFinite(parsedTicketId) && parsedTicketId > 0 ? parsedTicketId : null,
    };
}

async function openTicketFromUrl() {
    const {tab, ticketId} = getDeepLinkState();
    if (!ticketId) {
        return;
    }

    const supportedTabs = new Set(['inbox', 'team', 'mine']);
    const selectedTab = supportedTabs.has(tab) ? tab : 'inbox';
    switchTab(selectedTab);
    const viewMap = {
        inbox: 'inbox',
        team: 'team',
        mine: 'mine',
    };
    await renderTickets(viewMap[selectedTab] || 'inbox');

    const listItem = document.querySelector(`.list-item[data-ticket-id="${ticketId}"]`);
    if (listItem) {
        document.querySelectorAll('.list-item').forEach(el => {
            el.classList.remove('selected');
        });
        listItem.classList.add('selected');
        listItem.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    state.selectedTicketId = ticketId;
    state.addTicketToHistory(ticketId);
    updateTicketDeepLink(selectedTab, ticketId);
    await loadTicketDetail(ticketId);
    updateNavButtons();
}

function setupTicketNavigation() {
    const backBtn = document.getElementById('ticket-nav-back');
    const forwardBtn = document.getElementById('ticket-nav-forward');

    if (backBtn) {
        backBtn.addEventListener('click', async () => {
            const ticketId = state.navigateBack();
            if (ticketId) {
                // Switch to inbox tab
                switchTab('inbox');
                // Wait for list to render, then select and load
                setTimeout(async () => {
                    const listItem = document.querySelector(`.list-item[data-ticket-id="${ticketId}"]`);
                    if (listItem) {
                        document.querySelectorAll('.list-item').forEach(el => {
                            el.classList.remove('selected');
                        });
                        listItem.classList.add('selected');
                        state.selectedTicketId = ticketId;
                        updateTicketDeepLink('inbox', ticketId);
                        listItem.scrollIntoView({behavior: 'smooth', block: 'center'});
                    }
                    await loadTicketDetail(ticketId);
                    updateNavButtons();
                }, 100);
            }
        });
    }

    if (forwardBtn) {
        forwardBtn.addEventListener('click', async () => {
            const ticketId = state.navigateForward();
            if (ticketId) {
                // Switch to inbox tab
                switchTab('inbox');
                // Wait for list to render, then select and load
                setTimeout(async () => {
                    const listItem = document.querySelector(`.list-item[data-ticket-id="${ticketId}"]`);
                    if (listItem) {
                        document.querySelectorAll('.list-item').forEach(el => {
                            el.classList.remove('selected');
                        });
                        listItem.classList.add('selected');
                        state.selectedTicketId = ticketId;
                        updateTicketDeepLink('inbox', ticketId);
                        listItem.scrollIntoView({behavior: 'smooth', block: 'center'});
                    }
                    await loadTicketDetail(ticketId);
                    updateNavButtons();
                }, 100);
            }
        });
    }

    // Initial button state
    updateNavButtons();
}

export function updateNavButtons() {
    const backBtn = document.getElementById('ticket-nav-back');
    const forwardBtn = document.getElementById('ticket-nav-forward');

    if (backBtn) {
        backBtn.disabled = !state.canNavigateBack();
    }
    if (forwardBtn) {
        forwardBtn.disabled = !state.canNavigateForward();
    }
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

// Make available globally
window.updateNavButtons = updateNavButtons;
