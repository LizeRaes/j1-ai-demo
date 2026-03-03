package com.example.appointment.mapper;

import com.example.ticket.domain.constants.Team;
import com.example.ticket.domain.constants.TicketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketTypeTeamMapperTest {

    private final TicketTypeTeamMapper mapper = new TicketTypeTeamMapper();

    @Test
    void deriveTeamFromTicketTypeBilling() {
        assertEquals(Team.BILLING, mapper.deriveTeamFromTicketType(TicketType.BILLING_REFUND));
        assertEquals(Team.BILLING, mapper.deriveTeamFromTicketType(TicketType.BILLING_OTHER));
    }

    @Test
    void deriveTeamFromTicketTypeScheduling() {
        assertEquals(Team.SCHEDULING, mapper.deriveTeamFromTicketType(TicketType.SCHEDULING_CANCELLATION));
        assertEquals(Team.SCHEDULING, mapper.deriveTeamFromTicketType(TicketType.SCHEDULING_OTHER));
    }

    @Test
    void deriveTeamFromTicketType() {
        assertEquals(Team.DISPATCHING, mapper.deriveTeamFromTicketType(TicketType.ACCOUNT_ACCESS));
        assertEquals(Team.DISPATCHING, mapper.deriveTeamFromTicketType(TicketType.SUPPORT_OTHER));
        assertEquals(Team.DISPATCHING, mapper.deriveTeamFromTicketType(TicketType.OTHER));
    }

    @Test
    void deriveTeamFromTicketTypeToEngineeringTeam() {
        assertEquals(Team.ENGINEERING, mapper.deriveTeamFromTicketType(TicketType.BUG_APP));
        assertEquals(Team.ENGINEERING, mapper.deriveTeamFromTicketType(TicketType.BUG_BACKEND));
        assertEquals(Team.ENGINEERING, mapper.deriveTeamFromTicketType(TicketType.ENGINEERING_OTHER));
    }

    @Test
    void deriveTeamFromTicketTypeWithTicketTypeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> mapper.deriveTeamFromTicketType(null));
    }
}