package com.mediflow.ticketing.domain.enums;

public enum RequestStatus {
    NEW,                    // Just received, waiting for AI triage
    AI_TRIAGE_IN_PROGRESS,  // Currently being processed by AI triage (prevents duplicate processing)
    AI_TRIAGE_FAILED,       // AI triage failed/timed out, needs human dispatcher
    DISPATCHED,             // Legacy status (deprecated)
    CONVERTED_TO_TICKET,    // Successfully converted to ticket by AI triage
    RETURNED_FROM_AI         // Ticket was rolled back to request
}
