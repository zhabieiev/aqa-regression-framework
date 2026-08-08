package com.aqa.jhipster.api.steps;

import com.aqa.jhipster.api.models.generated.JWTToken;
import com.aqa.jhipster.api.models.generated.LoginVM;
import com.aqa.jhipster.api.services.AuthService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static java.util.Objects.requireNonNull;

@Slf4j
public record AuthSteps(AuthService authService) {

    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public Map<String, String> getAdminHeaders() {
        final Map<String, String> headers = authService.getAdminHeaders();
        log.info("Authorization headers for administrator are obtained");
        return headers;
    }

    public Map<String, String> getUserHeaders() {
        final Map<String, String> headers = authService.getUserHeaders();
        log.info("Authorization headers for regular user are obtained");
        return headers;
    }

    public Map<String, String> getHeaders(final Map<String, String> credentials) {
        final String username =
                requireNonNull(credentials.get(USERNAME), "Authentication parameter 'username' is required");

        final String password =
                requireNonNull(credentials.get(PASSWORD), "Authentication parameter 'password' is required");

        final Map<String, String> headers = authService.getHeaders(username, password);
        log.info("Authorization headers for user {} are obtained", username);
        return headers;
    }

    public JWTToken login(final LoginVM body) {
        final JWTToken response = authService.login(body);
        log.info("User with username {} is authenticated", body.getUsername());
        return response;
    }

    public Map<String, Object> login(final LoginVM body, final int statusCode) {
        final Map<String, Object> response = authService.login(body, statusCode);
        log.info("Authentication request for username {} is completed with status {}", body.getUsername(), statusCode);
        return response;
    }
}