// Actor Context Management
const ACTOR_STORAGE_KEY = 'ticketing-actor-context';

// Team to default user mapping (lowercase team names)
const TEAM_DEFAULT_USERS = {
    'dispatch': 'dispatch-user1',
    'billing': 'billing-user1',
    'reschedule': 'reschedule-user1',
    'engineering': 'engineering-user1',
};

// Team to role mapping (lowercase team names)
const TEAM_ROLES = {
    'dispatch': 'DISPATCHER',
    'billing': 'BILLING_AGENT',
    'reschedule': 'SCHEDULING_AGENT',
    'engineering': 'ENGINEER',
};

export function getActorContext() {
    const stored = localStorage.getItem(ACTOR_STORAGE_KEY);
    if (stored) {
        try {
            return JSON.parse(stored);
        } catch (e) {
            console.error('Failed to parse actor context:', e);
        }
    }

    // Default context
    return {
        team: 'dispatch',
        actorId: 'dispatch-user1',
        role: 'DISPATCHER',
    };
}

export function saveActorContext(context) {
    localStorage.setItem(ACTOR_STORAGE_KEY, JSON.stringify(context));
}

export function getDefaultUserIdForTeam(team) {
    return TEAM_DEFAULT_USERS[team] || `${team.toLowerCase()}-user1`;
}

export function getRoleForTeam(team) {
    return TEAM_ROLES[team] || 'USER';
}

export function renderActorContext() {
    const context = getActorContext();
    const container = document.getElementById('actor-context');
    if (!container) return;

    container.innerHTML = `
        <div class="actor-context-group">
            <label for="actor-team" class="actor-label">Persona:</label>
            <select id="actor-team" class="actor-select">
                <option value="dispatch" ${context.team === 'dispatch' ? 'selected' : ''}>Dispatcher</option>
                <option value="billing" ${context.team === 'billing' ? 'selected' : ''}>Billing Agent</option>
                <option value="reschedule" ${context.team === 'reschedule' ? 'selected' : ''}>Scheduling Agent</option>
                <option value="engineering" ${context.team === 'engineering' ? 'selected' : ''}>Engineer</option>
            </select>
        </div>
        <div class="actor-context-group">
            <label for="actor-id" class="actor-label">Actor ID:</label>
            <input type="text" id="actor-id" class="actor-input" value="${context.actorId}" placeholder="actor-id">
        </div>
    `;

    // Update actor ID when team changes
    const teamSelect = document.getElementById('actor-team');
    const actorIdInput = document.getElementById('actor-id');

    teamSelect.addEventListener('change', () => {
        const newTeam = teamSelect.value;
        const defaultUserId = getDefaultUserIdForTeam(newTeam);
        actorIdInput.value = defaultUserId;
        updateActorContext();
    });

    actorIdInput.addEventListener('change', updateActorContext);
    actorIdInput.addEventListener('blur', updateActorContext);
}

function updateActorContext() {
    const team = document.getElementById('actor-team').value;
    const actorId = document.getElementById('actor-id').value;
    const role = getRoleForTeam(team);

    const context = {
        team,
        actorId,
        role,
    };

    saveActorContext(context);

    // Update state
    if (window.state) {
        window.state.currentUserId = actorId;
    }

    // NOTE: We intentionally do NOT force a tab change here.
    // The current view (list + selected ticket) should stay as-is when persona changes.
}

export function getActorHeaders() {
    const context = getActorContext();
    return {
        'X-Actor-Id': context.actorId || 'demo-user',
        'X-Actor-Role': context.role || 'USER',
        'X-Actor-Team': context.team || 'dispatch',
    };
}
