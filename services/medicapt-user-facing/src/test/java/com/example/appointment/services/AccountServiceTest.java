package com.example.appointment.services;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccountServiceTest {

    private static AccountService accountService;

    @BeforeAll
    static void setUp() {
        accountService = new AccountService();
    }


    @Test
    void shouldReturnDefaultUserId() {
        assertEquals("u-charlie", accountService.getUserId());
    }

    @Test
    void shouldReturnDefaultEmail() {
        assertEquals("charlie@example.com", accountService.getEmail());
    }

    @AfterAll
    static void tearDown() {
        accountService =null;
    }
}
