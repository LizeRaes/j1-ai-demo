package com.example.appointment.resource;

import com.example.appointment.model.Appointment;
import com.example.appointment.services.SchedulingService;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;

@Path("/appointments")
public class AppointmentResource {
    @Inject
    Template appointment;

    @Inject
    Template appointments;

    @Inject
    SchedulingService schedulingService;


    @GET
    @Path("/book")
    @Produces(MediaType.TEXT_HTML)
    public String bookAppointment() {
        return appointment.render();
    }

    @POST
    @Path("/book")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createAppointment(
            @FormParam("doctor") String doctor,
            @FormParam("date") String date,
            @FormParam("time") String time) {
        
        String userId = schedulingService.getDefaultUserId();
        
        if (date == null || time == null || doctor == null || doctor.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Invalid appointment data")
                    .build();
        }

        Appointment appointment = schedulingService.createAppointment(doctor, convert(date), LocalTime.parse(time), userId);
        
        // Redirect to payment
        return Response.seeOther(java.net.URI.create("/billing/pay?appointmentId=" + appointment.id()))
                .build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String fetchAppointments() {
        String userId = schedulingService.getDefaultUserId();
        return appointments.data("appointments",
                schedulingService.getAllAppointments(userId)).render();
    }

    @POST
    @Path("/{id}/cancel")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response cancelAppointment(@PathParam("id") String appointmentId) {
        String userId = schedulingService.getDefaultUserId();
        schedulingService.cancelAppointment(appointmentId, userId);
        return Response.seeOther(URI.create("/appointments"))
                .build();
    }

    @POST
    @Path("/{id}/reschedule")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response rescheduleAppointment(
            @PathParam("id") String appointmentId,
            @FormParam("date") String date,
            @FormParam("time") String time) {

        String userId = schedulingService.getDefaultUserId();

        if (time != null) {
            schedulingService.rescheduleAppointment(appointmentId, convert(date), LocalTime.parse(time), userId);
        }
        
        return Response.seeOther(URI.create("/appointments"))
                .build();
    }

    private LocalDate convert(String date) {
        LocalDate today = LocalDate.now();

        return switch (date.toLowerCase()) {
            case "today" -> today;
            case "tomorrow" ->  today.plusDays(1);
            case "next week" -> today.plusWeeks(1);
            case String _ -> today;
        };
    }

}
