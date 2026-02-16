import { get, post } from './http.js';

export async function getIncomingRequests(status) {
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    return get(`/incoming-requests${query}`);
}

export async function getIncomingRequest(id) {
    return get(`/incoming-requests/${id}`);
}

export async function createIncomingRequest(data) {
    return post('/incoming-requests', data);
}
