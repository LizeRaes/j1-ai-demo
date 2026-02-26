import {get, post} from './http.js';

export async function getTickets(view, team, user) {
    const params = new URLSearchParams();
    if (view) params.append('view', view);
    if (team) params.append('team', team);
    if (user) params.append('user', user);
    const query = params.toString();
    return get(`/tickets${query ? '?' + query : ''}`);
}

export async function getTicket(id) {
    return get(`/tickets/${id}`);
}

export async function createTicketManual(data) {
    return post('/tickets/manual', data);
}

export async function createTicketFromAi(data) {
    return post('/tickets/from-ai', data);
}

export async function acceptTicket(id, userId) {
    return post(`/tickets/${id}/accept?userId=${encodeURIComponent(userId)}`, {});
}

export async function rejectAndReturnToDispatch(id) {
    return post(`/tickets/${id}/reject-and-return-to-dispatch`, {});
}

export async function rollbackToRequest(id) {
    return post(`/tickets/${id}/rollback-to-request`, {});
}

export async function updateTicketStatus(id, status) {
    return post(`/tickets/${id}/status`, {status});
}

export async function updateTicketType(id, data) {
    return post(`/tickets/${id}/ticket-type`, data);
}

export async function addComment(id, authorId, body) {
    return post(`/tickets/${id}/comments`, {authorId, body});
}
