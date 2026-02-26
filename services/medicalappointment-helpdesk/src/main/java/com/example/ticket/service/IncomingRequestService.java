package com.example.ticket.service;

import com.example.ticket.domain.constants.EventSeverity;
import com.example.ticket.domain.constants.EventType;
import com.example.ticket.domain.constants.RequestStatus;
import com.example.ticket.domain.model.IncomingRequest;
import com.example.ticket.dto.CreateIncomingRequestDto;
import com.example.ticket.dto.IncomingRequestDto;
import com.example.ticket.persistence.IncomingRequestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class IncomingRequestService {

    private static final Logger LOGGER = Logger.getLogger(IncomingRequestService.class.getName());

    @Inject
    IncomingRequestRepository incomingRequestRepository;

    @Inject
    EventService eventService;

    @Inject
    TriageWorkerService triageWorkerService;

    @Transactional
    public IncomingRequestDto createIncomingRequest(CreateIncomingRequestDto dto) {
        IncomingRequest request = new IncomingRequest();
        request.setUserId(dto.userId());
        request.setChannel(dto.channel());
        request.setRawText(dto.rawText());
        // All new requests start as NEW - AI triage worker processes them immediately
        request.setStatus(RequestStatus.NEW);
        incomingRequestRepository.persist(request);

        // Use channel as source for event, default to "ticketing-api"
        String eventSource = dto.channel() != null && !dto.channel().isEmpty()
                ? dto.channel()
                : "ticketing-api";

        eventService.logEvent(
                EventType.INCOMING_REQUEST_RECEIVED,
                EventSeverity.INFO,
                eventSource,
                "Incoming request #" + request.getId() + " received from user " + dto.userId(),
                null,
                request.getId(),
                null
        );

        // Immediately trigger AI triage worker to process this new request
        // This runs synchronously for now, will become async later
        IncomingRequestDto requestDto = new IncomingRequestDto(request.getId(), request.getUserId()
                , request.getChannel(), request.getRawText(),
                request.getStatus(), request.getCreatedAt(), request.getUpdatedAt());
//                mapper.toDto(request);
        try {
            triageWorkerService.processRequest(requestDto);
        } catch (Exception e) {
            // Log error but don't fail the request creation
            // Request will remain as NEW and can be processed manually or appear in dispatcher inbox
            LOGGER.log(Level.SEVERE, "Error in triage worker: ", e);
        }

        return new IncomingRequestDto(request.getId(), request.getUserId(),
                request.getChannel(), request.getRawText(),
                request.getStatus(), request.getCreatedAt(),
                request.getUpdatedAt());
    }

    public List<IncomingRequestDto> getIncomingRequests(RequestStatus status) {
        List<IncomingRequest> requests;
        if (status != null) {
            requests = incomingRequestRepository.findByStatus(status);
        } else {
            requests = incomingRequestRepository.listAll();
        }
        return requests.stream()
                .map(request -> new IncomingRequestDto(request.getId(), request.getUserId(),
                        request.getChannel(), request.getRawText(),
                        request.getStatus(), request.getCreatedAt(),
                        request.getUpdatedAt()))
                .collect(Collectors.toList());
    }

    /**
     * Get requests for dispatcher inbox (AI_TRIAGE_FAILED and RETURNED_FROM_AI).
     * NEW requests are being processed by AI triage and shouldn't appear here.
     */
    public List<IncomingRequestDto> getDispatcherInboxRequests() {
        List<IncomingRequest> failedRequests = incomingRequestRepository.findByStatus(RequestStatus.AI_TRIAGE_FAILED);
        List<IncomingRequest> returnedRequests = incomingRequestRepository.findByStatus(RequestStatus.RETURNED_FROM_AI);

        List<IncomingRequest> allRequests = new java.util.ArrayList<>();
        allRequests.addAll(failedRequests);
        allRequests.addAll(returnedRequests);

        return allRequests.stream()
                .map(request -> new IncomingRequestDto(request.getId(), request.getUserId(),
                        request.getChannel(), request.getRawText(),
                        request.getStatus(), request.getCreatedAt(),
                        request.getUpdatedAt())).collect(Collectors.toList());
    }

    public IncomingRequestDto getIncomingRequest(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request == null) {
            return null;
        }

        return new IncomingRequestDto(request.getId(), request.getUserId(),
                request.getChannel(), request.getRawText(),
                request.getStatus(), request.getCreatedAt(),
                request.getUpdatedAt());
    }

    @Transactional
    public void markAsConvertedToTicket(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request != null) {
            request.setStatus(RequestStatus.CONVERTED_TO_TICKET);
            incomingRequestRepository.persist(request);
        }
    }

    @Transactional
    public void markAsReturnedFromAi(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request != null) {
            request.setStatus(RequestStatus.RETURNED_FROM_AI);
            incomingRequestRepository.persist(request);
        }
    }

    @Transactional
    public void markAsAiTriageInProgress(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request != null) {
            request.setStatus(RequestStatus.AI_TRIAGE_IN_PROGRESS);
            incomingRequestRepository.persist(request);
        }
    }

    public void deleteAll() {
        incomingRequestRepository.deleteAll();
    }

    public long count() {
        return incomingRequestRepository.count();
    }

}
