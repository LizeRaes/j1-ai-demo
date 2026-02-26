package com.example.ticket.mapper;

import com.example.ticket.domain.constants.Team;
import com.example.ticket.domain.constants.TicketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketTypeTeamMapperTest {

    private final TicketTypeTeamMapper mapper = new TicketTypeTeamMapper();

    @Test
    void deriveTeamFromTicketTypeBilling() {
        assertEquals(Team.billing, mapper.deriveTeamFromTicketType(TicketType.BILLING_REFUND));
        assertEquals(Team.billing, mapper.deriveTeamFromTicketType(TicketType.BILLING_OTHER));
    }

    @Test
    void deriveTeamFromTicketTypeScheduling() {
        assertEquals(Team.reschedule, mapper.deriveTeamFromTicketType(TicketType.SCHEDULING_CANCELLATION));
        assertEquals(Team.reschedule, mapper.deriveTeamFromTicketType(TicketType.SCHEDULING_OTHER));
    }

    @Test
    void deriveTeamFromTicketType() {
        assertEquals(Team.dispatch, mapper.deriveTeamFromTicketType(TicketType.ACCOUNT_ACCESS));
        assertEquals(Team.dispatch, mapper.deriveTeamFromTicketType(TicketType.SUPPORT_OTHER));
        assertEquals(Team.dispatch, mapper.deriveTeamFromTicketType(TicketType.OTHER));
    }

    @Test
    void deriveTeamFromTicketTypeToEngineeringTeam() {
        assertEquals(Team.engineering, mapper.deriveTeamFromTicketType(TicketType.BUG_APP));
        assertEquals(Team.engineering, mapper.deriveTeamFromTicketType(TicketType.BUG_BACKEND));
        assertEquals(Team.engineering, mapper.deriveTeamFromTicketType(TicketType.ENGINEERING_OTHER));
    }

    @Test
    void deriveTeamFromTicketTypeWithTicketTypeIsNull() {
        assertThrows(IllegalArgumentException.class, () -> mapper.deriveTeamFromTicketType(null));
    }

    @Test
    void requiresDispatcherReviewForOtherTypesAndNull() {
        assertTrue(mapper.requiresDispatcherReview(TicketType.OTHER));
        assertTrue(mapper.requiresDispatcherReview(TicketType.SUPPORT_OTHER));
        assertTrue(mapper.requiresDispatcherReview(TicketType.BILLING_OTHER));
        assertTrue(mapper.requiresDispatcherReview(TicketType.SCHEDULING_OTHER));
        assertTrue(mapper.requiresDispatcherReview(TicketType.ENGINEERING_OTHER));
        assertTrue(mapper.requiresDispatcherReview(null));
    }

    @Test
    void requiresDispatcherReviewForSpecificNonOtherTypes() {
        assertFalse(mapper.requiresDispatcherReview(TicketType.BILLING_REFUND));
        assertFalse(mapper.requiresDispatcherReview(TicketType.SCHEDULING_CANCELLATION));
        assertFalse(mapper.requiresDispatcherReview(TicketType.ACCOUNT_ACCESS));
        assertFalse(mapper.requiresDispatcherReview(TicketType.BUG_APP));
        assertFalse(mapper.requiresDispatcherReview(TicketType.BUG_BACKEND));
    }
}