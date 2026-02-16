package com.medicalappointment.ticketing.dto;

import com.medicalappointment.ticketing.domain.enums.RequestStatus;
import java.time.OffsetDateTime;

public class IncomingRequestDto {
    public Long id;
    public String userId;
    public String channel;
    public String rawText;
    public RequestStatus status;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
