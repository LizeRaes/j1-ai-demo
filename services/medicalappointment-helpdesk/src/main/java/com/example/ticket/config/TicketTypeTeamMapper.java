package com.example.ticket.config;

import com.example.ticket.domain.enums.Team;
import com.example.ticket.domain.enums.TicketType;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Maps TicketType to Team deterministically.
 * 
 * Rule: TicketType defines intent; AssignedTeam is a derived consequence.
 * 
 * Mapping:
 * - BILLING_* → billing
 * - SCHEDULING_* → reschedule
 * - ACCOUNT_* → dispatch
 * - SUPPORT_OTHER → dispatch
 * - BUG_* → engineering
 * - ENGINEERING_* → engineering
 * - OTHER → dispatch (AI couldn't classify, needs human dispatcher)
 */
@ApplicationScoped
public class TicketTypeTeamMapper {
    
    /**
     * Derives the assigned team from ticket type.
     * 
     * @param ticketType The ticket type (must not be null)
     * @return The assigned team
     * @throws IllegalArgumentException if ticketType is null or invalid
     */
    public Team deriveTeamFromTicketType(TicketType ticketType) {
        if (ticketType == null) {
            throw new IllegalArgumentException("TicketType cannot be null");
        }
        
        String typeName = ticketType.name();
        
        // Billing domain
        if (typeName.startsWith("BILLING_")) {
            return Team.billing;
        }
        
        // Scheduling domain
        if (typeName.startsWith("SCHEDULING_")) {
            return Team.reschedule;
        }
        
        // Account / General Support domain
        if (typeName.startsWith("ACCOUNT_")) {
            return Team.dispatch;
        }
        if (typeName.equals("SUPPORT_OTHER")) {
            return Team.dispatch;
        }
        
        // Bugs / Engineering domain
        if (typeName.startsWith("BUG_")) {
            return Team.engineering;
        }
        if (typeName.startsWith("ENGINEERING_")) {
            return Team.engineering;
        }
        
        // Generic OTHER - AI couldn't classify, send to human dispatcher
        if (typeName.equals("OTHER")) {
            return Team.dispatch;
        }
        
        // Should never reach here if enum is properly maintained
        throw new IllegalArgumentException("Unknown TicketType: " + ticketType);
    }
    
    /**
     * Checks if a ticket type requires human dispatcher review.
     * Tickets ending with _OTHER or generic OTHER require review.
     * 
     * @param ticketType The ticket type
     * @return true if dispatcher review is required
     */
    public boolean requiresDispatcherReview(TicketType ticketType) {
        if (ticketType == null) {
            return true; // Invalid tickets require review
        }
        String typeName = ticketType.name();
        return typeName.equals("OTHER") || typeName.endsWith("_OTHER");
    }
}
