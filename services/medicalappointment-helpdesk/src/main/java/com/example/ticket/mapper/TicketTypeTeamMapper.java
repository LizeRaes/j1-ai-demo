package com.example.ticket.mapper;

import com.example.ticket.domain.constants.Team;
import com.example.ticket.domain.constants.TicketType;

/**
 * Maps TicketType to Team deterministically.
 * Rule: TicketType defines intent; AssignedTeam is a derived consequence.
 * <p>
 * Mapping:
 * - BILLING_* → billing
 * - SCHEDULING_* → reschedule
 * - ACCOUNT_* → dispatch
 * - SUPPORT_OTHER → dispatch
 * - BUG_* → engineering
 * - ENGINEERING_* → engineering
 * - OTHER → dispatch (AI couldn't classify, needs human dispatcher)
 */
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

        return switch (ticketType.name()) {
            case String type when type.startsWith("BILLING_") -> Team.BILLING;
            case String type when type.startsWith("SCHEDULING_") -> Team.RESCHEDULE;
            case String type when type.startsWith("ACCOUNT_") -> Team.DISPATCH;
            case String type when type.startsWith("SUPPORT_OTHER") -> Team.DISPATCH;
            case String type when type.startsWith("BUG_") -> Team.ENGINEERING;
            case String type when type.startsWith("ENGINEERING_") -> Team.ENGINEERING;
            case String type when type.equals("OTHER") -> Team.DISPATCH;
            case String t -> throw new IllegalArgumentException("Unknown TicketType: " + t);
        };

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
