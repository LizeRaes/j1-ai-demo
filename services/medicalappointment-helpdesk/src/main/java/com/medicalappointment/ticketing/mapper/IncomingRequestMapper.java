package com.medicalappointment.ticketing.mapper;

import com.medicalappointment.ticketing.domain.model.IncomingRequest;
import com.medicalappointment.ticketing.dto.IncomingRequestDto;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IncomingRequestMapper {
    public IncomingRequestDto toDto(IncomingRequest request) {
        IncomingRequestDto dto = new IncomingRequestDto();
        dto.id = request.id;
        dto.userId = request.userId;
        dto.channel = request.channel;
        dto.rawText = request.rawText;
        dto.status = request.status;
        dto.createdAt = request.createdAt;
        dto.updatedAt = request.updatedAt;
        return dto;
    }
}
