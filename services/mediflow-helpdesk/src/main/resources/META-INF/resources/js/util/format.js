export function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '';
    const date = new Date(dateTimeStr);
    return date.toLocaleString();
}

export function formatTime(dateTimeStr) {
    if (!dateTimeStr) return '';
    const date = new Date(dateTimeStr);
    return date.toLocaleTimeString();
}

export function formatDate(dateTimeStr) {
    if (!dateTimeStr) return '';
    const date = new Date(dateTimeStr);
    return date.toLocaleDateString();
}

export function getEventColorClass(event) {
    // Map specific event types to colors
    const eventType = event.eventType?.toUpperCase() || '';
    
    // Accepting ticket = green
    if (eventType === 'TICKET_ACCEPTED') {
        return 'event-type-accepted';
    }
    
    // Dispatcher dispatching = grey
    if (eventType === 'DISPATCH_SUBMITTED') {
        return 'event-type-dispatch';
    }
    
    // Incoming request received = green
    if (eventType === 'INCOMING_REQUEST_RECEIVED') {
        return 'event-type-incoming';
    }
    
    // Ticket created = blue
    if (eventType === 'TICKET_CREATED') {
        return 'event-type-created';
    }
    
    // Status changed = yellow/orange
    if (eventType === 'TICKET_STATUS_CHANGED') {
        return 'event-type-status-changed';
    }
    
    // Comment added = cyan/teal
    if (eventType === 'TICKET_COMMENT_ADDED') {
        return 'event-type-comment';
    }
    
    // Returned to dispatch = blue
    if (eventType === 'RETURNED_TO_DISPATCH') {
        return 'event-type-returned';
    }
    
    // System step = yellow
    if (eventType === 'SYSTEM_STEP') {
        return 'event-type-system-step';
    }
    
    // Notification = purple
    if (eventType === 'NOTIFICATION_SENT') {
        return 'event-type-notification';
    }
    
    // Error = red
    if (eventType === 'ERROR_OCCURRED') {
        return 'event-type-error';
    }
    
    // AI ticket created = cyan/teal
    if (eventType === 'AI_TICKET_CREATED') {
        return 'event-type-ai-created';
    }
    
    // AI ticket rolled back = orange
    if (eventType === 'AI_TICKET_ROLLED_BACK') {
        return 'event-type-ai-rolled-back';
    }

    // Fallback to severity
    const severity = event.severity?.toLowerCase() || '';
    if (severity === 'info') return 'severity-info';
    if (severity === 'warning') return 'severity-warning';
    if (severity === 'error') return 'severity-error';

    return 'severity-info';
}
