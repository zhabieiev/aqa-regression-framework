package com.aqa.toolshop.api.services;

import com.aqa.toolshop.api.models.generated.AccountRequest;
import com.aqa.toolshop.api.models.generated.PaginatedUserResponse;
import com.aqa.toolshop.api.models.generated.TokenResponse;
import com.aqa.toolshop.api.models.generated.UserResponse;
import jakarta.ws.rs.core.GenericType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aqa.core.enumerations.Property.USER_ADMINISTRATOR_EMAIL;
import static com.aqa.core.enumerations.Property.USER_ADMINISTRATOR_PASSWORD;
import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Objects.requireNonNull;

public class AuthService extends ApiService {

    private static final String USERS = "/users";
    private static final String USERS_LOGIN = USERS + "/login";
    private static final String USERS_ID = USERS + "/%s";
    private static final String USERS_SEARCH = USERS + "/search";

    private final Map<String, TokenResponse> tokens = new HashMap<>();

    public Map<String, String> getAdminHeaders() {
        return getAuthorizationHeaders(USER_ADMINISTRATOR_EMAIL.read(), USER_ADMINISTRATOR_PASSWORD.read());
    }

    public Map<String, String> getAuthorizationHeaders(final String email, final String password) {
        TokenResponse tokenResponse = tokens.computeIfAbsent(email, key -> login(email, password));
        String accessToken =
                requireNonNull(tokenResponse.getAccessToken(), "Access token is missing for user: " + email);
        return Map.of(AUTHORIZATION, "Bearer " + accessToken);
    }

    public TokenResponse login(final String email, final String password) {
        AccountRequest body = new AccountRequest();
        body.setEmail(email);
        body.setPassword(password);
        return login(body);
    }

    public TokenResponse login(final AccountRequest body) {
        return getResponse(request().method(POST).path(USERS_LOGIN).body(body).statusCode(HTTP_OK).build()).readEntity(
                TokenResponse.class);
    }

    public PaginatedUserResponse searchUsers(final Map<String, String> params, final Map<String, String> headers) {
        return getResponse(request().method(GET)
                .path(USERS_SEARCH)
                .queryParams(params)
                .headers(headers)
                .statusCode(HTTP_OK)
                .build()).readEntity(PaginatedUserResponse.class);
    }
}