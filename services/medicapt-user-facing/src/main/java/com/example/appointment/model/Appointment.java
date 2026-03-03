package com.example.appointment.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Appointment(String id, String doctor, LocalDate date, LocalTime time, AppointmentStatus status, String userId) {}