package com.example.appointment.domain.constants;

public enum Team {
    DISPATCHING,
    BILLING,
    SCHEDULING,
    ENGINEERING;

    public String value() {
        return name().toLowerCase();
    }
}
