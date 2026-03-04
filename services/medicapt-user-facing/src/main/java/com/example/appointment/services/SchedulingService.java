package com.example.appointment.services;

import com.example.appointment.model.Appointment;
import com.example.appointment.model.AppointmentStatus;
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
                .filter(a -> a.userId().equals(userId))
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

    public void cancelAppointment(String appointmentId, String userId) {
        Appointment appointment = appointments.stream()
                .filter(        a -> a.id().equals(appointmentId) && a.userId().equals(userId))
                .findFirst()
                .orElse(null);
        
        if (appointment != null) {
            appointments.remove(appointment);
            appointments.add(new Appointment(appointmentId, appointment.doctor(), appointment.date(),
                    appointment.time(), AppointmentStatus.CANCELLED, appointment.userId()));
        }
    }

    public void rescheduleAppointment(String appointmentId, LocalDate newDate, LocalTime newTime, String userId) {
        Appointment appointment = appointments.stream()
                .filter(a -> a.id().equals(appointmentId) && a.userId().equals(userId))
                .findFirst()
                .orElse(null);
        
        if (appointment != null) {
            appointments.remove(appointment);

            appointment = new Appointment(appointmentId, appointment.doctor(), newDate,
                    newTime, AppointmentStatus.CONFIRMED, appointment.userId());
            appointments.add(appointment);
        }
    }

    public void confirmAppointment(String appointmentId) {
        Appointment appointment = appointments.stream()
                .filter(a -> a.id().equals(appointmentId))
                .findFirst()
                .orElse(null);
        
        if (appointment != null) {
            appointments.remove(appointment);
            appointments.add(new Appointment(appointmentId, appointment.doctor(), appointment.date(),
                    appointment.time(), AppointmentStatus.CONFIRMED, appointment.userId()));
        }
    }

    public String getDefaultUserId() {
        return DEFAULT_USER_ID;
    }
}
