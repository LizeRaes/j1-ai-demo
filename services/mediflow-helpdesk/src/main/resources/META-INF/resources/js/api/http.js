import { getActorHeaders } from '../ui/actorContext.js';

const API_BASE = '/api';

function getHeaders() {
    const actorHeaders = getActorHeaders();
    return {
        'Content-Type': 'application/json',
        ...actorHeaders,
    };
}

export async function get(url) {
    try {
        const fullUrl = API_BASE + url;
        console.log('Fetching:', fullUrl);
        const response = await fetch(fullUrl, {
            headers: getActorHeaders(),
        });
        if (!response.ok) {
            const errorText = await response.text();
            console.error('HTTP error:', response.status, errorText);
            throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
        }
        const data = await response.json();
        console.log('Response:', data);
        return data;
    } catch (error) {
        console.error('Fetch error:', error);
        throw error;
    }
}

export async function post(url, data) {
    const response = await fetch(API_BASE + url, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify(data),
    });
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP error! status: ${response.status}, message: ${errorText}`);
    }
    return response.json();
}
