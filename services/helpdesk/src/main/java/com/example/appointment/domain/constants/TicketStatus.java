package com.example.appointment.domain.constants;

public enum TicketStatus {
    AI_TRIAGE_PENDING,
    FROM_DISPATCH,
    FROM_AI,
    TRIAGED,
    IN_PROGRESS,
    WAITING_ON_USER,
    COMPLETED,
    RETURNED_TO_DISPATCH
}
