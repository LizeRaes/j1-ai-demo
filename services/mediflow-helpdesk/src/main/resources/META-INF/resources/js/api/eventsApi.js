import { get } from './http.js';

export async function getRecentEvents(since, limit) {
    const params = new URLSearchParams();
    if (since) params.append('since', since);
    if (limit) params.append('limit', limit);
    const query = params.toString();
    return get(`/events/recent${query ? '?' + query : ''}`);
}
