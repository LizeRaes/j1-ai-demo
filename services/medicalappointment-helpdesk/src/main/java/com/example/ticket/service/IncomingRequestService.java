package com.example.ticket.service;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;
import com.example.ticket.domain.constants.RequestStatus;
import com.example.ticket.dto.CreateIncomingRequestDto;
import com.example.ticket.dto.IncomingRequestDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class IncomingRequestService {

    private static final Logger LOGGER = Logger.getLogger(IncomingRequestService.class.getName());

    @Inject
    EventService eventService;

    @Inject
    IncomingRequestStateService incomingRequestStateService;

    @Inject
    TriageWorkerService triageWorkerService;

    public IncomingRequestDto createIncomingRequest(CreateIncomingRequestDto dto) {
        IncomingRequestDto request = incomingRequestStateService.createIncomingRequest(dto);

        String eventSource = dto.channel() != null && !dto.channel().isEmpty()
                ? dto.channel()
                : "ticketing-api";

        eventService.logEvent(
                EventType.INCOMING_REQUEST_RECEIVED,
                EventSeverity.INFO,
                eventSource,
                "Incoming request #" + request.id() + " received from user " + dto.userId(),
                null,
                request.id(),
                null
        );

        try {
            triageWorkerService.processRequest(request);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in triage worker: ", e);
        }

        return request;
    }

    public List<IncomingRequestDto> getIncomingRequests(RequestStatus status) {
        return incomingRequestStateService.getIncomingRequests(status);
    }

    public List<IncomingRequestDto> getDispatcherInboxRequests() {
        return incomingRequestStateService.getDispatcherInboxRequests();
    }

    public IncomingRequestDto getIncomingRequest(Long id) {
        return incomingRequestStateService.getIncomingRequest(id);
    }

    public void markAsConvertedToTicket(Long id) {
        incomingRequestStateService.markAsConvertedToTicket(id);
    }

    public void markAsReturnedFromAi(Long id) {
        incomingRequestStateService.markAsReturnedFromAi(id);
    }

    public void deleteAll() {
        incomingRequestStateService.deleteAll();
    }

    public long count() {
        return incomingRequestStateService.count();
    }
}