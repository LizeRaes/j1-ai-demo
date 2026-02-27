package com.example.ticket.service.adapter;

import com.example.ticket.domain.model.Ticket;
import com.example.ticket.persistence.TicketRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class TicketStateService {

    @Inject
    TicketRepository ticketRepository;

    @Transactional
    public void persist(Ticket ticket) {
        ticketRepository.persist(ticket);
    }

    @Transactional
    public Ticket findById(Long id) {
        return ticketRepository.findById(id);
    }

    @Transactional
    public List<Ticket> findByAssignedTeam(String team) {
        return ticketRepository.findByAssignedTeam(team);
    }

    @Transactional
    public List<Ticket> findByAssignedTo(String userId) {
        return ticketRepository.findByAssignedTo(userId);
    }

    @Transactional
    public List<Ticket> listAll() {
        return ticketRepository.listAll();
    }

    @Transactional
    public Long findMaxId() {
        return ticketRepository.findMaxId();
    }
}