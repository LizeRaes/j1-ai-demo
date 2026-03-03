package com.example.appointment.mapper;

import com.example.appointment.domain.constants.Team;
import com.example.appointment.domain.constants.TicketType;

/**
 * Maps TicketType to Team deterministically.
 * Rule: TicketType defines intent; AssignedTeam is a derived consequence.
 * <p>
 * Mapping:
 * - BILLING_* → billing
 * - SCHEDULING_* → scheduling
 * - ACCOUNT_* → dispatching
 * - SUPPORT_OTHER → dispatching
 * - BUG_* → engineering
 * - ENGINEERING_* → engineering
 * - OTHER → dispatching
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
            case String type when type.startsWith("SCHEDULING_") -> Team.SCHEDULING;
            case String type when type.startsWith("ACCOUNT_") -> Team.DISPATCHING;
            case String type when type.startsWith("SUPPORT_OTHER") -> Team.DISPATCHING;
            case String type when type.startsWith("BUG_") -> Team.ENGINEERING;
            case String type when type.startsWith("ENGINEERING_") -> Team.ENGINEERING;
            case String type when type.equals("OTHER") -> Team.DISPATCHING;
            case String t -> throw new IllegalArgumentException("Unknown TicketType: " + t);
        };

    }

}
