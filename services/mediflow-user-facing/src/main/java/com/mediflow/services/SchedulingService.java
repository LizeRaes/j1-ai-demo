package com.mediflow.services;

import com.mediflow.model.Appointment;
import com.mediflow.model.AppointmentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SchedulingService {
    private final List<Appointment> appointments = new ArrayList<>();
    private static final String DEFAULT_USER_ID = "u-charlie";

    public List<Appointment> getAllAppointments(String userId) {
        return appointments.stream()
                .filter(a -> a.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    public Appointment createAppointment(String doctor, LocalDate date, LocalTime time, String userId) {
        Appointment appointment = new Appointment(
                UUID.randomUUID().toString(),
                doctor,
                date,
                time,
                AppointmentStatus.PENDING,
                userId
        );
        appointments.add(appointment);
        return appointment;
    }

    public Appointment cancelAppointment(String appointmentId, String userId) {
        Appointment appointment = appointments.stream()
                .filter(a -> a.getId().equals(appointmentId) && a.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
        
        if (appointment != null) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
        }
        return appointment;
    }

    public Appointment rescheduleAppointment(String appointmentId, LocalDate newDate, LocalTime newTime, String userId) {
        Appointment appointment = appointments.stream()
                .filter(a -> a.getId().equals(appointmentId) && a.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
        
        if (appointment != null) {
            appointment.setDate(newDate);
            appointment.setTime(newTime);
            appointment.setStatus(AppointmentStatus.CONFIRMED);
        }
        return appointment;
    }

    public Appointment confirmAppointment(String appointmentId) {
        Appointment appointment = appointments.stream()
                .filter(a -> a.getId().equals(appointmentId))
                .findFirst()
                .orElse(null);
        
        if (appointment != null) {
            appointment.setStatus(AppointmentStatus.CONFIRMED);
        }
        return appointment;
    }

    public String getDefaultUserId() {
        return DEFAULT_USER_ID;
    }
}
