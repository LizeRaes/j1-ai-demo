import { switchTab } from './ui/router.js';
import { startEventPolling } from './ui/renderLogs.js';
import { renderActorContext, getActorContext } from './ui/actorContext.js';
import { state } from './ui/state.js';
import { loadTicketDetail } from './ui/renderTicketDetail.js';

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

        // Start event polling
        startEventPolling();
    } catch (error) {
        console.error('Error initializing app:', error);
        document.body.innerHTML = '<div style="padding: 2rem; color: red;">Error loading application. Check console for details.</div>';
    }
});

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
                        listItem.scrollIntoView({ behavior: 'smooth', block: 'center' });
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
                        listItem.scrollIntoView({ behavior: 'smooth', block: 'center' });
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

// Make available globally
window.updateNavButtons = updateNavButtons;
