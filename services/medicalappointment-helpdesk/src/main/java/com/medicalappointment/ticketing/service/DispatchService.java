package com.medicalappointment.ticketing.service;

import com.medicalappointment.ticketing.dto.DispatchCreateTicketDto;
import com.medicalappointment.ticketing.dto.TicketDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DispatchService {
    @Inject
    IncomingRequestService incomingRequestService;

    @Inject
    TicketService ticketService;

    @Transactional
    public TicketDto submitTicket(DispatchCreateTicketDto dto) {
        // Get the original request text
        var incomingRequest = incomingRequestService.getIncomingRequest(dto.incomingRequestId);
        if (incomingRequest == null) {
            throw new IllegalArgumentException("Incoming request not found: " + dto.incomingRequestId);
        }

        // Create ticket from dispatch
        TicketDto ticket = ticketService.createTicketFromDispatch(dto, incomingRequest.rawText, incomingRequest.userId);

        // Mark incoming request as converted
        incomingRequestService.markAsConvertedToTicket(dto.incomingRequestId);

        return ticket;
    }
}
