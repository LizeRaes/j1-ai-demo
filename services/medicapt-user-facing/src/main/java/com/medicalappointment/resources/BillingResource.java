package com.medicalappointment.resources;

import com.medicalappointment.services.BillingService;
import com.medicalappointment.services.SchedulingService;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/billing")
public class BillingResource {
    @Inject
    Template payment;

    @Inject
    Template paymentSuccess;

    @Inject
    BillingService billingService;

    @Inject
    SchedulingService schedulingService;

    @GET
    @Path("/pay")
    @Produces(MediaType.TEXT_HTML)
    public String payment(@QueryParam("appointmentId") String appointmentId) {
        return payment.data("appointmentId", appointmentId).render();
    }

    @POST
    @Path("/pay")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response processPayment(@FormParam("appointmentId") String appointmentId) {
        String userId = billingService.getDefaultUserId();
        
        // Mark payment as paid
        billingService.markPaymentAsPaid(userId);
        
        // Confirm appointment if provided
        if (appointmentId != null && !appointmentId.isEmpty()) {
            schedulingService.confirmAppointment(appointmentId);
        }
        
        return Response.seeOther(java.net.URI.create("/billing/pay/success"))
                .build();
    }

    @GET
    @Path("/pay/success")
    @Produces(MediaType.TEXT_HTML)
    public String paymentSuccess() {
        return paymentSuccess.render();
    }
}
