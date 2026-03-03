package com.example.appointment.services;

import com.example.appointment.model.PaymentStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BillingServiceTest {

    private static BillingService billingService;

    @BeforeAll
    static void setUp() {
        billingService = new BillingService();
    }

    @Test
    void shouldReturnPendingStatusByDefault() {
        PaymentStatus status = billingService.getPaymentStatus("u-charlie");

        assertEquals(PaymentStatus.PENDING, status);
    }

    @Test
    void shouldMarkPaymentAsPaid() {
        String userId = "u-charlie";

        billingService.markPaymentAsPaid(userId);

        assertEquals(PaymentStatus.PAID, billingService.getPaymentStatus(userId));
    }

    @Test
    void shouldReturnDefaultUserId() {
        assertEquals("u-charlie", billingService.getDefaultUserId());
    }

    @AfterAll
    static void tearDown() {
        billingService =null;
    }
}
