package com.medicalappointment.ticketing.dto;

import java.time.OffsetDateTime;

public class CommentDto {
    public Long id;
    public Long ticketId;
    public String authorId;
    public String body;
    public OffsetDateTime createdAt;
}
