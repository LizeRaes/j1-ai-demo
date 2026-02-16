import { post } from './http.js';

export async function submitTicket(data) {
    return post('/dispatch/submit-ticket', data);
}
