package com.medicalappointment.resources;

import com.medicalappointment.model.Appointment;
import com.medicalappointment.services.SchedulingService;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Path("/appointments")
public class AppointmentResource {
    @Inject
    Template bookAppointment;

    @Inject
    Template myAppointments;

    @Inject
    SchedulingService schedulingService;

    @GET
    @Path("/book")
    @Produces(MediaType.TEXT_HTML)
    public String bookAppointment() {
        return bookAppointment.render();
    }

    @POST
    @Path("/book")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createAppointment(
            @FormParam("doctor") String doctor,
            @FormParam("date") String dateStr,
            @FormParam("time") String timeStr) {
        
        String userId = schedulingService.getDefaultUserId();
        
        // Parse date and time - very simple parsing
        LocalDate date = parseDate(dateStr);
        LocalTime time = parseTime(timeStr);
        
        if (date == null || time == null || doctor == null || doctor.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid appointment data")
                    .build();
        }
        
        Appointment appointment = schedulingService.createAppointment(doctor, date, time, userId);
        
        // Redirect to payment
        return Response.seeOther(java.net.URI.create("/billing/pay?appointmentId=" + appointment.getId()))
                .build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String myAppointments() {
        String userId = schedulingService.getDefaultUserId();
        List<Appointment> appointments = schedulingService.getAllAppointments(userId);
        return myAppointments.data("appointments", appointments).render();
    }

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response cancelAppointment(@PathParam("id") String appointmentId) {
        String userId = schedulingService.getDefaultUserId();
        schedulingService.cancelAppointment(appointmentId, userId);
        return Response.seeOther(java.net.URI.create("/appointments"))
                .build();
    }

    @POST
    @Path("/{id}/reschedule")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response rescheduleAppointment(
            @PathParam("id") String appointmentId,
            @FormParam("date") String dateStr,
            @FormParam("time") String timeStr) {
        
        String userId = schedulingService.getDefaultUserId();
        LocalDate date = parseDate(dateStr);
        LocalTime time = parseTime(timeStr);
        
        if (date != null && time != null) {
            schedulingService.rescheduleAppointment(appointmentId, date, time, userId);
        }
        
        return Response.seeOther(java.net.URI.create("/appointments"))
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        // Simple parsing for demo dates
        LocalDate today = LocalDate.now();
        switch (dateStr.toLowerCase()) {
            case "today":
                return today;
            case "tomorrow":
                return today.plusDays(1);
            case "next week":
                return today.plusWeeks(1);
            default:
                try {
                    return LocalDate.parse(dateStr);
                } catch (Exception e) {
                    return null;
                }
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr);
        } catch (Exception e) {
            return null;
        }
    }
}
