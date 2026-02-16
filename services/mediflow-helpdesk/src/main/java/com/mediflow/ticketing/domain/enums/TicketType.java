package com.mediflow.ticketing.domain.enums;

public enum TicketType {
    // Billing
    BILLING_REFUND,
    BILLING_OTHER,
    
    // Scheduling
    SCHEDULING_CANCELLATION,
    SCHEDULING_OTHER,
    
    // Account / General Support
    ACCOUNT_ACCESS,
    SUPPORT_OTHER,
    
    // Bugs / Engineering
    BUG_APP,
    BUG_BACKEND,
    ENGINEERING_OTHER,
    
    // Generic - AI couldn't classify, needs human dispatcher
    OTHER
}
