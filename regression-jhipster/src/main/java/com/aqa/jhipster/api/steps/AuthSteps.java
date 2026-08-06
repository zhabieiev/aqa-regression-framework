package com.aqa.jhipster.api.steps;

import com.aqa.jhipster.api.services.AuthService;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record AuthSteps(AuthService authService) {

    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public Map<String, String> getAdminHeaders() {
        return authService.getAdminHeaders();
    }

    public Map<String, String> getUserHeaders() {
        return authService.getUserHeaders();
    }

    public Map<String, String> getHeaders(final Map<String, String> credentials) {
        String username = requireNonNull(
                credentials.get(USERNAME),
                "Authentication parameter 'username' is required"
        );

        String password = requireNonNull(
                credentials.get(PASSWORD),
                "Authentication parameter 'password' is required"
        );

        return authService.getHeaders(username, password);
    }
}