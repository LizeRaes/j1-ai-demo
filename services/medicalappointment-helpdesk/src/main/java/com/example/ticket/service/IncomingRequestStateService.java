package com.example.ticket.service;

import com.example.ticket.domain.constants.RequestStatus;
import com.example.ticket.domain.model.IncomingRequest;
import com.example.ticket.dto.CreateIncomingRequestDto;
import com.example.ticket.dto.IncomingRequestDto;
import com.example.ticket.persistence.IncomingRequestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class IncomingRequestStateService {

    @Inject
    IncomingRequestRepository incomingRequestRepository;

    @Transactional
    public IncomingRequestDto createIncomingRequest(CreateIncomingRequestDto dto) {
        IncomingRequest request = new IncomingRequest();
        request.setUserId(dto.userId());
        request.setChannel(dto.channel());
        request.setRawText(dto.rawText());
        request.setStatus(RequestStatus.NEW);
        incomingRequestRepository.persist(request);
        return toDto(request);
    }

    public List<IncomingRequestDto> getIncomingRequests(RequestStatus status) {
        List<IncomingRequest> requests;
        if (status != null) {
            requests = incomingRequestRepository.findByStatus(status);
        } else {
            requests = incomingRequestRepository.listAll();
        }

        return requests.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<IncomingRequestDto> getDispatcherInboxRequests() {
        List<IncomingRequest> failedRequests = incomingRequestRepository.findByStatus(RequestStatus.AI_TRIAGE_FAILED);
        List<IncomingRequest> returnedRequests = incomingRequestRepository.findByStatus(RequestStatus.RETURNED_FROM_AI);

        List<IncomingRequest> allRequests = new java.util.ArrayList<>();
        allRequests.addAll(failedRequests);
        allRequests.addAll(returnedRequests);

        return allRequests.stream().map(this::toDto).collect(Collectors.toList());
    }

    public IncomingRequestDto getIncomingRequest(Long id) {
        IncomingRequest request = incomingRequestRepository.findById(id);
        if (request == null) {
            return null;
        }
        return toDto(request);
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

    private IncomingRequestDto toDto(IncomingRequest request) {
        return new IncomingRequestDto(
                request.getId(),
                request.getUserId(),
                request.getChannel(),
                request.getRawText(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }
}