package com.example.appointment.services;

import com.example.appointment.model.PaymentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class BillingService {
    private final Map<String, PaymentStatus> paymentStatuses = new HashMap<>();
    private static final String DEFAULT_USER_ID = "u-charlie";

    public PaymentStatus getPaymentStatus(String userId) {
        return paymentStatuses.getOrDefault(userId, PaymentStatus.PENDING);
    }

    public void markPaymentAsPaid(String userId) {
        paymentStatuses.put(userId, PaymentStatus.PAID);
    }

    public String getDefaultUserId() {
        return DEFAULT_USER_ID;
    }
}
