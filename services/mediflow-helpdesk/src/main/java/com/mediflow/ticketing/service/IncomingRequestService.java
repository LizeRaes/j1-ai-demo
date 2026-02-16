package com.mediflow.ticketing.service;

import com.mediflow.ticketing.domain.enums.EventSeverity;
import com.mediflow.ticketing.domain.enums.EventType;
import com.mediflow.ticketing.domain.enums.RequestStatus;
import com.mediflow.ticketing.domain.model.IncomingRequest;
import com.mediflow.ticketing.dto.CreateIncomingRequestDto;
import com.mediflow.ticketing.dto.IncomingRequestDto;
import com.mediflow.ticketing.mapper.IncomingRequestMapper;
import com.mediflow.ticketing.persistence.IncomingRequestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class IncomingRequestService {
    @Inject
    IncomingRequestRepository incomingRequestRepository;

    @Inject
    IncomingRequestMapper mapper;

    @Inject
    EventService eventService;

    @Inject
    TriageWorkerService triageWorkerService;

    @Transactional
    public IncomingRequestDto createIncomingRequest(CreateIncomingRequestDto dto) {
        IncomingRequest request = new IncomingRequest();
        request.userId = dto.userId;
        request.channel = dto.channel;
        request.rawText = dto.rawText;
        // All new requests start as NEW - AI triage worker processes them immediately
        request.status = RequestStatus.NEW;
        incomingRequestRepository.persist(request);

        // Use channel as source for event, default to "ticketing-api"
        String eventSource = dto.channel != null && !dto.channel.isEmpty() 
            ? dto.channel 
            : "ticketing-api";
        
        eventService.logEvent(
            EventType.INCOMING_REQUEST_RECEIVED,
            EventSeverity.INFO,
            eventSource,
            "Incoming request #" + request.id + " received from user " + dto.userId,
            null,
            request.id,
            null
        );

        // Immediately trigger AI triage worker to process this new request
        // This runs synchronously for now, will become async later
        IncomingRequestDto requestDto = mapper.toDto(request);
        try {
            triageWorkerService.processRequest(requestDto);
        } catch (Exception e) {
            // Log error but don't fail the request creation
            // Request will remain as NEW and can be processed manually or appear in dispatcher inbox
            System.err.println("Error in triage worker: " + e.getMessage());
            e.printStackTrace();
        }

        return mapper.toDto(request);
    }

    public List<IncomingRequestDto> getIncomingRequests(RequestStatus status) {
        List<IncomingRequest> requests;
        if (status != null) {
            requests = incomingRequestRepository.findByStatus(status);
        } else {
            requests = incomingRequestRepository.listAll();
        }
        return requests.stream()
            .map(mapper::toDto)
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
            .map(mapper::toDto)
            .collect(Collectors.toList());
    }

    public IncomingRequestDto getIncomingRequest(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request == null) {
            return null;
        }
        return mapper.toDto(request);
    }

    @Transactional
    public void markAsConvertedToTicket(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request != null) {
            request.status = RequestStatus.CONVERTED_TO_TICKET;
            incomingRequestRepository.persist(request);
        }
    }

    @Transactional
    public void markAsReturnedFromAi(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request != null) {
            request.status = RequestStatus.RETURNED_FROM_AI;
            incomingRequestRepository.persist(request);
        }
    }

    @Transactional
    public void markAsAiTriageInProgress(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request != null) {
            request.status = RequestStatus.AI_TRIAGE_IN_PROGRESS;
            incomingRequestRepository.persist(request);
        }
    }

    @Transactional
    public void markAsAiTriageFailed(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request != null) {
            request.status = RequestStatus.AI_TRIAGE_FAILED;
            incomingRequestRepository.persist(request);
        }
    }

}
