package com.medicalappointment.services;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountService {
    private static final String DEFAULT_USER_ID = "u-charlie";
    private static final String DEFAULT_EMAIL = "charlie@example.com";

    public String getUserId() {
        return DEFAULT_USER_ID;
    }

    public String getEmail() {
        return DEFAULT_EMAIL;
    }

    public boolean resetPassword(String userId) {
        // Always fails or shows success randomly - intentional for demo
        return Math.random() > 0.5;
    }
}
