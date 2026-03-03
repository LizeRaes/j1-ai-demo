package com.example.appointment.mapper;

import com.example.appointment.domain.constants.RequestStatus;
import com.example.appointment.domain.constants.TicketSource;
import com.example.appointment.domain.constants.TicketStatus;
import com.example.appointment.domain.constants.TicketType;
import com.example.appointment.domain.model.Ticket;
import com.example.appointment.dto.AITicketDto;
import com.example.appointment.dto.DispatchedTicketDto;
import com.example.appointment.dto.IncomingRequestDto;
import com.example.appointment.dto.ManualTicketDto;
import com.example.appointment.dto.TicketDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketMapperTest {

    private final TicketMapper mapper = new TicketMapper();

    @Test
    void toDispatchTicketMapsFieldsAndDerivesRouting() {
        DispatchedTicketDto dispatched = new DispatchedTicketDto(99L, TicketType.BILLING_REFUND,
                null, 7.5, "dispatching-user1", "notes");
        IncomingRequestDto request = new IncomingRequestDto(99L, "u-1", "web", "Need refund",
                RequestStatus.NEW, LocalDateTime.now(), LocalDateTime.now());

        Ticket ticket = mapper.toDispatchTicket(dispatched, request);
        assertNull(ticket.getId());
        assertEquals("u-1", ticket.getUserId());
        assertEquals("Need refund", ticket.getOriginalRequest());
        assertEquals(TicketType.BILLING_REFUND, ticket.getTicketType());
        assertEquals(TicketStatus.FROM_DISPATCH, ticket.getStatus());
        assertEquals(TicketSource.MANUAL, ticket.getSource());
        assertEquals("billing", ticket.getAssignedTeam().toLowerCase());
        assertEquals("billing-user1", ticket.getAssignedTo());
        assertFalse(ticket.getUrgencyFlag());
        assertEquals(7.5, ticket.getUrgencyScore());
        assertFalse(ticket.getRollbackAllowed());
        assertEquals(99L, ticket.getIncomingRequestId());
    }

    @Test
    void toManualTicketUsesProvidedAssignedToAndStatus() {
        ManualTicketDto dto = new ManualTicketDto("u-2", "Cannot login", TicketType.ACCOUNT_ACCESS,
                TicketStatus.IN_PROGRESS, "custom-user", true, 8.0);

        Ticket ticket = mapper.toManualTicket(dto);
        assertNull(ticket.getId());
        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertEquals("custom-user", ticket.getAssignedTo());
        assertEquals("dispatching", ticket.getAssignedTeam().toLowerCase());
        assertTrue(ticket.getUrgencyFlag());
        assertEquals(8.0, ticket.getUrgencyScore());
    }

    @Test
    void toAiTicketMapsAiSpecificFields() {
        AITicketDto dto = new AITicketDto("u-3", "App crashes", TicketType.BUG_APP,
                6.0, false, 0.85, "{\"k\":\"v\"}", 77L);

        Ticket ticket = mapper.toAiTicket(dto);
        assertNull(ticket.getId());
        assertEquals(TicketStatus.FROM_AI, ticket.getStatus());
        assertEquals(TicketSource.AI, ticket.getSource());
        assertEquals("engineering", ticket.getAssignedTeam().toLowerCase());
        assertEquals("engineering-user1", ticket.getAssignedTo());
        assertTrue(ticket.getRollbackAllowed());
        assertEquals(0.85, ticket.getAiConfidence());
        assertEquals("{\"k\":\"v\"}", ticket.getAiPayloadJson());
        assertEquals(77L, ticket.getIncomingRequestId());
    }

    @Test
    void toInitialTriageTicketSetsFallbackDefaults() {
        Ticket ticket = mapper.toInitialTriageTicket("u-4", "Unknown issue", 45L);

        assertEquals(TicketType.OTHER, ticket.getTicketType());
        assertEquals(TicketStatus.AI_TRIAGE_PENDING, ticket.getStatus());
        assertEquals(TicketSource.AI, ticket.getSource());
        assertEquals("dispatching", ticket.getAssignedTeam());
        assertFalse(ticket.getUrgencyFlag());
        assertTrue(ticket.getRollbackAllowed());
        assertEquals("{}", ticket.getAiPayloadJson());
        assertEquals(45L, ticket.getIncomingRequestId());
    }

    @Test
    void mapUserIdForTeamMapsKnownAndUnknownTeams() {
        assertEquals("dispatching-user1", mapper.mapUserIdForTeam("dispatching"));
        assertEquals("billing-user1", mapper.mapUserIdForTeam("billing"));
        assertEquals("scheduling-user1", mapper.mapUserIdForTeam("scheduling"));
        assertEquals("engineering-user1", mapper.mapUserIdForTeam("engineering"));
        assertEquals("ops-user1", mapper.mapUserIdForTeam("ops"));
        assertEquals("demo-user", mapper.mapUserIdForTeam(null));
    }

    @Test
    void toTicketDtoAndBackPreserveMainFields() {
        Ticket source = new Ticket();
        source.setId(500L);
        source.setUserId("u-5");
        source.setOriginalRequest("Need scheduling help");
        source.setTicketType(TicketType.SCHEDULING_OTHER);
        source.setStatus(TicketStatus.WAITING_ON_USER);
        source.setSource(TicketSource.MANUAL);
        source.setAssignedTeam("scheduling");
        source.setAssignedTo("scheduling-user1");
        source.setUrgencyFlag(false);
        source.setUrgencyScore(4.2);
        source.setAiConfidence(0.3);
        source.setRollbackAllowed(false);
        source.setAiPayloadJson("{}");
        source.setIncomingRequestId(12L);

        TicketDto dto = mapper.toTicketDto(source);
        Ticket back = mapper.toTicket(dto);

        assertEquals(500L, dto.id());
        assertEquals(List.of(), dto.comments());
        assertEquals(source.getId(), back.getId());
        assertEquals(source.getUserId(), back.getUserId());
        assertEquals(source.getOriginalRequest(), back.getOriginalRequest());
        assertEquals(source.getTicketType(), back.getTicketType());
        assertEquals(source.getStatus(), back.getStatus());
        assertEquals(source.getSource(), back.getSource());
        assertEquals("scheduling-user1", back.getAssignedTo());
        assertEquals(source.getUrgencyFlag(), back.getUrgencyFlag());
        assertEquals(source.getUrgencyScore(), back.getUrgencyScore());
        assertEquals(source.getAiConfidence(), back.getAiConfidence());
        assertEquals(source.getAiPayloadJson(), back.getAiPayloadJson());
        assertNull(back.getIncomingRequestId());
    }
}