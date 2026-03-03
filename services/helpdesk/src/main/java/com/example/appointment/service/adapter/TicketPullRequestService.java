package com.example.appointment.service.adapter;

import com.example.appointment.domain.model.TicketPullRequest;
import com.example.appointment.persistence.TicketPullRequestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class TicketPullRequestService {

    @Inject
    TicketPullRequestRepository ticketPullRequestRepository;

    @Transactional
    public void persist(TicketPullRequest pullRequest) {
        ticketPullRequestRepository.persist(pullRequest);
    }

    @Transactional
    public List<TicketPullRequest> findByTicketId(Long ticketId) {
        return ticketPullRequestRepository.findByTicketId(ticketId);
    }

    @Transactional
    public TicketPullRequest findAiPullRequestByTicketId(Long ticketId) {
        return ticketPullRequestRepository.findAiPullRequestByTicketId(ticketId);
    }
}
