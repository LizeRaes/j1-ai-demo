package com.example.ticket.service;

import com.example.ticket.domain.model.Ticket;
import com.example.ticket.domain.model.TicketPullRequest;
import com.example.ticket.domain.constants.TicketStatus;
import com.example.ticket.domain.constants.TicketType;
import com.example.ticket.dto.AddPullRequestDto;
import com.example.ticket.dto.CodingAssistantSubmitJobRequestDto;
import com.example.ticket.dto.CodingAssistantSubmitJobResponseDto;
import com.example.ticket.dto.IncomingRequestDto;
import com.example.ticket.dto.TicketPullRequestDto;
import com.example.ticket.external.CodingAssistantClient;
import com.example.ticket.service.adapter.CommentService;
import com.example.ticket.service.adapter.EventService;
import com.example.ticket.service.adapter.IncomingRequestService;
import com.example.ticket.service.adapter.TicketPullRequestService;
import com.example.ticket.service.adapter.TicketStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    TicketStateService ticketStateService;
    @Mock
    CommentService commentService;
    @Mock
    EventService eventService;
    @Mock
    CodingAssistantClient codingAssistantClient;
    @Mock
    IncomingRequestService incomingRequestService;
    @Mock
    TicketPullRequestService ticketPullRequestService;

    @Test
    void findTicketsDefaultsToAllWhenViewIsNull() {
        TicketService service = newService();

        Ticket open = new Ticket();
        open.setId(10L);
        open.setUserId("u-open");
        open.setOriginalRequest("Need help");
        open.setTicketType(TicketType.OTHER);
        open.setStatus(TicketStatus.FROM_DISPATCH);
        open.setSource(com.example.ticket.domain.constants.TicketSource.MANUAL);
        open.setAssignedTeam("dispatching");
        open.setUrgencyFlag(false);
        open.setRollbackAllowed(false);

        Ticket returnedToDispatch = new Ticket();
        returnedToDispatch.setId(11L);
        returnedToDispatch.setUserId("u-rb");
        returnedToDispatch.setOriginalRequest("Old ticket");
        returnedToDispatch.setTicketType(TicketType.OTHER);
        returnedToDispatch.setStatus(TicketStatus.RETURNED_TO_DISPATCH);
        returnedToDispatch.setSource(com.example.ticket.domain.constants.TicketSource.AI);
        returnedToDispatch.setAssignedTeam("dispatching");
        returnedToDispatch.setUrgencyFlag(false);
        returnedToDispatch.setRollbackAllowed(true);

        Ticket pending = new Ticket();
        pending.setId(12L);
        pending.setUserId("u-pending");
        pending.setOriginalRequest("Awaiting triage");
        pending.setTicketType(TicketType.OTHER);
        pending.setStatus(TicketStatus.AI_TRIAGE_PENDING);
        pending.setSource(com.example.ticket.domain.constants.TicketSource.AI);
        pending.setAssignedTeam("dispatching");
        pending.setUrgencyFlag(false);
        pending.setRollbackAllowed(true);

        when(ticketStateService.listAll()).thenReturn(List.of(open, returnedToDispatch, pending));

        List<?> result = service.findTickets(null, null, null);

        assertEquals(1, result.size());
        verify(ticketStateService).listAll();
    }

    @Test
    void updateTicketWithTriageResultsBugTriggersCodingAssistantWhenEnabled() {
        TicketService service = newService();
        service.codingAssistantEnabled = true;
        service.codingAssistantRepoUrl = Optional.of("https://github.com/example/repo");
        service.codingAssistantConfidenceThreshold = 0.75;

        Ticket ticket = new Ticket();
        ticket.setId(101L);
        ticket.setOriginalRequest("App crashes on save");
        ticket.setIncomingRequestId(55L);
        when(ticketStateService.findById(101L)).thenReturn(ticket);
        when(codingAssistantClient.submitJob(any())).thenReturn(new CodingAssistantSubmitJobResponseDto("ACCEPTED", "queued"));

        service.updateTicketWithTriageResults(101L, "BUG_APP", 6.0, 80.0, List.of(), List.of());

        ArgumentCaptor<CodingAssistantSubmitJobRequestDto> requestCaptor =
                ArgumentCaptor.forClass(CodingAssistantSubmitJobRequestDto.class);
        verify(codingAssistantClient).submitJob(requestCaptor.capture());
        CodingAssistantSubmitJobRequestDto request = requestCaptor.getValue();
        assertEquals(101L, request.ticketId());
        assertEquals("App crashes on save", request.originalRequest());
        assertEquals("https://github.com/example/repo", request.repoUrl());
        assertEquals(0.75, request.confidenceThreshold());
        assertEquals("BUG_APP", ticket.getTicketType().name());
        assertEquals("engineering", ticket.getAssignedTeam());
    }

    @Test
    void updateTicketWithTriageResultsBugDoesNotTriggerWhenDisabled() {
        TicketService service = newService();
        service.codingAssistantEnabled = false;
        service.codingAssistantRepoUrl = Optional.of("https://github.com/example/repo");
        service.codingAssistantConfidenceThreshold = 0.6;

        Ticket ticket = new Ticket();
        ticket.setId(102L);
        ticket.setOriginalRequest("App bug");
        when(ticketStateService.findById(102L)).thenReturn(ticket);

        service.updateTicketWithTriageResults(102L, "BUG_APP", 5.0, 60.0, List.of(), List.of());

        verify(codingAssistantClient, never()).submitJob(any());
    }

    @Test
    void updateTicketWithTriageResultsBugDoesNotTriggerWhenRepoMissing() {
        TicketService service = newService();
        service.codingAssistantEnabled = true;
        service.codingAssistantRepoUrl = Optional.of("   ");
        service.codingAssistantConfidenceThreshold = 0.6;

        Ticket ticket = new Ticket();
        ticket.setId(103L);
        ticket.setOriginalRequest("Backend bug");
        when(ticketStateService.findById(103L)).thenReturn(ticket);

        service.updateTicketWithTriageResults(103L, "BUG_BACKEND", 5.0, 60.0, List.of(), List.of());

        verify(codingAssistantClient, never()).submitJob(any());
    }

    @Test
    void createInitialTicketForTriagePersistsWithoutManualIdAssignment() {
        TicketService service = newService();

        Ticket created = service.createInitialTicketForTriage("u-charlie", "App crashes", 10L);

        assertNull(created.getId());
        verify(ticketStateService).persist(created);
    }

    @Test
    void findTicketHidesAiTriagePendingTicket() {
        TicketService service = newService();
        Ticket pending = new Ticket();
        pending.setId(220L);
        pending.setStatus(TicketStatus.AI_TRIAGE_PENDING);
        when(ticketStateService.findById(220L)).thenReturn(pending);

        assertNull(service.findTicket(220L));
        verify(commentService, never()).findByTicketId(any());
    }

    @Test
    void updateTicketAsFallbackKeepsIncomingRequestForDispatcherAndReturnsTicketToDispatch() {
        TicketService service = newService();

        Ticket ticket = new Ticket();
        ticket.setId(150L);
        ticket.setStatus(TicketStatus.FROM_AI);
        ticket.setTicketType(TicketType.BUG_APP);
        ticket.setAssignedTeam("engineering");
        ticket.setAssignedTo("engineering-user1");
        when(ticketStateService.findById(150L)).thenReturn(ticket);

        IncomingRequestDto request = new IncomingRequestDto(77L, "u-test", "intake-ui", "broken flow",
                com.example.ticket.domain.constants.RequestStatus.AI_TRIAGE_IN_PROGRESS, null, null);

        service.updateTicketAsFallback(150L, request, "triage timeout");

        assertEquals(TicketStatus.RETURNED_TO_DISPATCH, ticket.getStatus());
        assertEquals(TicketType.OTHER, ticket.getTicketType());
        assertEquals("dispatching", ticket.getAssignedTeam());
        assertNull(ticket.getAssignedTo());
        verify(ticketStateService).persist(ticket);
        verify(incomingRequestService).markAsAiTriageFailed(77L);
    }

    @Test
    void upsertAiPullRequestOverwritesExistingAiEntry() {
        TicketService service = newService();

        Ticket ticket = new Ticket();
        ticket.setId(200L);
        when(ticketStateService.findById(200L)).thenReturn(ticket);

        TicketPullRequest existing = new TicketPullRequest();
        existing.setId(1L);
        existing.setTicket(ticket);
        existing.setPrUrl("https://github.com/org/repo/pull/1");
        existing.setAiGenerated(true);
        when(ticketPullRequestService.findAiPullRequestByTicketId(200L)).thenReturn(existing);

        service.upsertAiPullRequest(200L, "https://github.com/org/repo/pull/2");

        assertEquals("https://github.com/org/repo/pull/2", existing.getPrUrl());
        verify(ticketPullRequestService, times(1)).persist(existing);
    }

    @Test
    void upsertAiPullRequestCreatesEntryWhenMissing() {
        TicketService service = newService();

        Ticket ticket = new Ticket();
        ticket.setId(201L);
        when(ticketStateService.findById(201L)).thenReturn(ticket);
        when(ticketPullRequestService.findAiPullRequestByTicketId(201L)).thenReturn(null);

        service.upsertAiPullRequest(201L, "https://github.com/org/repo/pull/99");

        ArgumentCaptor<TicketPullRequest> prCaptor = ArgumentCaptor.forClass(TicketPullRequest.class);
        verify(ticketPullRequestService).persist(prCaptor.capture());
        TicketPullRequest created = prCaptor.getValue();
        assertEquals(ticket, created.getTicket());
        assertEquals("https://github.com/org/repo/pull/99", created.getPrUrl());
        assertTrue(created.getAiGenerated());
    }

    @Test
    void addManualPullRequestPersistsNonAiPr() {
        TicketService service = newService();

        Ticket ticket = new Ticket();
        ticket.setId(300L);
        when(ticketStateService.findById(300L)).thenReturn(ticket);

        TicketPullRequestDto dto = service.addManualPullRequest(300L, new AddPullRequestDto("https://github.com/org/repo/pull/10"));

        ArgumentCaptor<TicketPullRequest> prCaptor = ArgumentCaptor.forClass(TicketPullRequest.class);
        verify(ticketPullRequestService).persist(prCaptor.capture());
        TicketPullRequest created = prCaptor.getValue();
        assertEquals(ticket, created.getTicket());
        assertEquals("https://github.com/org/repo/pull/10", created.getPrUrl());
        assertFalse(created.getAiGenerated());

        assertNull(dto.id());
        assertEquals("https://github.com/org/repo/pull/10", dto.prUrl());
        assertFalse(dto.aiGenerated());
    }

    @Test
    void addManualPullRequestRejectsBlankUrl() {
        TicketService service = newService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addManualPullRequest(1L, new AddPullRequestDto("  ")));

        assertEquals("prUrl is required.", ex.getMessage());
        verify(ticketStateService, never()).findById(any());
        verify(ticketPullRequestService, never()).persist(any());
    }

    private TicketService newService() {
        TicketService service = new TicketService();
        service.ticketStateService = ticketStateService;
        service.commentService = commentService;
        service.eventService = eventService;
        service.codingAssistantClient = codingAssistantClient;
        service.incomingRequestService = incomingRequestService;
        service.ticketPullRequestService = ticketPullRequestService;
        return service;
    }
}
