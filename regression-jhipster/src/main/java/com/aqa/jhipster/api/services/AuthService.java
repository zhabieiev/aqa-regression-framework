package com.aqa.jhipster.api.services;

import com.aqa.jhipster.api.models.generated.JWTToken;
import com.aqa.jhipster.api.models.generated.LoginVM;
import jakarta.ws.rs.core.GenericType;

import java.util.Map;

import static com.aqa.core.models.Request.request;
import static com.aqa.jhipster.api.enumeration.Property.*;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Objects.requireNonNull;

public class AuthService extends ApiService {

    private static final String LOGIN_PATH = "/api/authenticate";
    private static final String BEARER_PREFIX = "Bearer ";

    private JWTToken adminToken;
    private JWTToken userToken;

    public Map<String, String> getAdminHeaders() {
        if (adminToken == null) {
            adminToken = login(ADMIN.read(), ADMIN_PASSWORD.read());
        }
        return authorizationHeaders(adminToken);
    }

    public Map<String, String> getUserHeaders() {
        if (userToken == null) {
            userToken = login(USER.read(), USER_PASSWORD.read());
        }
        return authorizationHeaders(userToken);
    }

    public Map<String, String> getHeaders(final String username, final String password) {
        return authorizationHeaders(login(username, password));
    }

    public JWTToken login(final String username, final String password) {
        LoginVM body = new LoginVM();
        body.setUsername(username);
        body.setPassword(password);
        body.setRememberMe(false);
        return login(body);
    }

    public JWTToken login(final LoginVM body) {
        return getResponse(request().method(POST).path(LOGIN_PATH).body(body).statusCode(HTTP_OK).build()).readEntity(
                JWTToken.class);
    }

    public Map<String, Object> login(final LoginVM body, int statusCode) {
        return getResponse(request().method(POST).path(LOGIN_PATH).body(body).statusCode(statusCode).build()).readEntity(
                new GenericType<>() {});
    }

    private Map<String, String> authorizationHeaders(final JWTToken token) {
        String idToken = requireNonNull(token.getIdToken(), "JWT token is missing in authentication response");
        return Map.of(AUTHORIZATION, BEARER_PREFIX + idToken);
    }
}