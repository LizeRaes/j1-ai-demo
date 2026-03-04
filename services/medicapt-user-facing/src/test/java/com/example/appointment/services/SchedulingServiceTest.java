package com.example.appointment.services;

import com.example.appointment.model.Appointment;
import com.example.appointment.model.AppointmentStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SchedulingServiceTest {

    private static SchedulingService schedulingService;

    @BeforeEach
    void setUp() {
        schedulingService = new SchedulingService();
    }

    @Test
    void shouldCreatePendingAppointmentForUser() {
        LocalDate date = LocalDate.now();
        LocalTime time = LocalTime.of(9, 0);

        Appointment created = schedulingService.createAppointment("Dr. Alice", date, time, "u-charlie");

        assertNotNull(created.id());
        assertEquals("Dr. Alice", created.doctor());
        assertEquals(date, created.date());
        assertEquals(time, created.time());
        assertEquals(AppointmentStatus.PENDING, created.status());
        assertEquals("u-charlie", created.userId());
    }

    @Test
    void shouldReturnOnlyAppointmentsForRequestedUser() {
        LocalDate date = LocalDate.now();
        schedulingService.createAppointment("Dr. Alice", date, LocalTime.of(9, 0), "u-charlie");
        schedulingService.createAppointment("Dr. Bob", date, LocalTime.of(10, 0), "u-other");

        List<Appointment> appointments = schedulingService.getAllAppointments("u-charlie");

        assertEquals(1, appointments.size());
        assertEquals("u-charlie", appointments.getFirst().userId());
    }

    @Test
    void shouldCancelAppointmentForMatchingIdAndUser() {
        Appointment created = schedulingService.createAppointment(
                "Dr. Alice",
                LocalDate.now(),
                LocalTime.of(9, 0),
                "u-charlie"
        );

        schedulingService.cancelAppointment(created.id(), "u-charlie");

        List<Appointment> appointments = schedulingService.getAllAppointments("u-charlie");
        assertEquals(1, appointments.size());
        assertEquals(AppointmentStatus.CANCELLED, appointments.getFirst().status());
    }

    @Test
    void shouldNotCancelWhenUserDoesNotMatch() {
        Appointment created = schedulingService.createAppointment(
                "Dr. Alice",
                LocalDate.now(),
                LocalTime.of(9, 0),
                "u-charlie"
        );

        schedulingService.cancelAppointment(created.id(), "u-other");

        List<Appointment> appointments = schedulingService.getAllAppointments("u-charlie");
        assertEquals(1, appointments.size());
        assertEquals(AppointmentStatus.PENDING, appointments.getFirst().status());
    }

    @Test
    void shouldRescheduleAppointmentAndSetConfirmedStatus() {
        Appointment created = schedulingService.createAppointment(
                "Dr. Alice",
                LocalDate.now(),
                LocalTime.of(9, 0),
                "u-charlie"
        );

        LocalDate newDate = LocalDate.now().plusDays(1);
        LocalTime newTime = LocalTime.of(10, 0);
        schedulingService.rescheduleAppointment(created.id(), newDate, newTime, "u-charlie");

        Appointment updated = schedulingService.getAllAppointments("u-charlie").getFirst();
        assertEquals(newDate, updated.date());
        assertEquals(newTime, updated.time());
        assertEquals(AppointmentStatus.CONFIRMED, updated.status());
    }

    @Test
    void shouldConfirmAppointmentById() {
        Appointment created = schedulingService.createAppointment(
                "Dr. Alice",
                LocalDate.now(),
                LocalTime.of(9, 0),
                "u-charlie"
        );

        schedulingService.confirmAppointment(created.id());

        Appointment updated = schedulingService.getAllAppointments("u-charlie").getFirst();
        assertEquals(AppointmentStatus.CONFIRMED, updated.status());
    }

    @AfterEach
    void tearDown() {
        schedulingService =null;
    }
}
