package com.example.appointment.model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Appointment {
    private String id;
    private String doctor;
    private LocalDate date;
    private LocalTime time;
    private AppointmentStatus status;
    private String userId;

    public Appointment() {
    }

    public Appointment(String id, String doctor, LocalDate date, LocalTime time, AppointmentStatus status, String userId) {
        this.id = id;
        this.doctor = doctor;
        this.date = date;
        this.time = time;
        this.status = status;
        this.userId = userId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDoctor() {
        return doctor;
    }

    public void setDoctor(String doctor) {
        this.doctor = doctor;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
