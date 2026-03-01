// Actor Context Management
const ACTOR_STORAGE_KEY = 'ticketing-actor-context';

// Team to default user mapping (lowercase team names)
const TEAM_DEFAULT_USERS = {
    'dispatching': 'dispatching-user1',
    'billing': 'billing-user1',
    'scheduling': 'scheduling-user1',
    'engineering': 'engineering-user1',
};

// Team to role mapping (lowercase team names)
const TEAM_ROLES = {
    'dispatching': 'dispatching',
    'billing': 'billing',
    'scheduling': 'scheduling',
    'engineering': 'engineering',
};
const VALID_TEAMS = Object.keys(TEAM_DEFAULT_USERS);

export function getActorContext() {
    const stored = localStorage.getItem(ACTOR_STORAGE_KEY);
    if (stored) {
        try {
            const parsed = JSON.parse(stored);
            if (parsed && VALID_TEAMS.includes(parsed.team)) {
                return parsed;
            }
        } catch (e) {
            console.error('Failed to parse actor context:', e);
        }
    }

    // Default context
    return {
        team: 'dispatching',
        actorId: 'dispatching-user1',
        role: 'dispatching',
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
                <option value="dispatching" ${context.team === 'dispatching' ? 'selected' : ''}>Dispatching</option>
                <option value="billing" ${context.team === 'billing' ? 'selected' : ''}>Billing</option>
                <option value="scheduling" ${context.team === 'scheduling' ? 'selected' : ''}>Scheduling</option>
                <option value="engineering" ${context.team === 'engineering' ? 'selected' : ''}>Engineering</option>
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

    window.dispatchEvent(new CustomEvent('actor-context-changed', {detail: context}));

    // NOTE: We intentionally do NOT force a tab change here.
    // The current view (list + selected ticket) should stay as-is when persona changes.
}

export function getActorHeaders() {
    const context = getActorContext();
    return {
        'X-Actor-Id': context.actorId || 'demo-user',
        'X-Actor-Role': context.role || 'dispatching',
        'X-Actor-Team': context.team || 'dispatching',
    };
}
