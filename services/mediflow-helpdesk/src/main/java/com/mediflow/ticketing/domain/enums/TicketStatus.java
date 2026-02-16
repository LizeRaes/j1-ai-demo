package com.mediflow.ticketing.domain.enums;

public enum TicketStatus {
    FROM_DISPATCH,
    FROM_AI,
    TRIAGED,
    IN_PROGRESS,
    WAITING_ON_USER,
    COMPLETED,
    RETURNED_TO_DISPATCH,
    ROLLED_BACK
}
