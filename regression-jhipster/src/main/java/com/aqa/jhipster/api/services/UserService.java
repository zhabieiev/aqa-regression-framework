package com.aqa.jhipster.api.services;

import com.aqa.jhipster.api.models.generated.AdminUserDTO;
import com.aqa.jhipster.api.models.generated.User;
import jakarta.ws.rs.core.GenericType;

import java.util.Map;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.*;
import static java.lang.String.format;
import static java.net.HttpURLConnection.*;

public class UserService extends ApiService {

    private static final String USERS = "/api/admin/users";
    private static final String USERS_LOGIN = USERS + "/%s";

    public User create(final AdminUserDTO body, final Map<String, String> headers) {
        return getResponse(request().method(POST)
                .path(USERS)
                .headers(headers)
                .body(body)
                .statusCode(HTTP_CREATED)
                .build()).readEntity(User.class);
    }

    public void delete(final String login, final Map<String, String> headers) {
        getResponse(request().method(DELETE)
                .path(format(USERS_LOGIN, login))
                .headers(headers)
                .statusCode(HTTP_NO_CONTENT)
                .build());
    }

    public AdminUserDTO get(final String login, final Map<String, String> headers) {
        return getResponse(request().method(GET)
                .path(format(USERS_LOGIN, login))
                .headers(headers)
                .statusCode(HTTP_OK)
                .build()).readEntity(AdminUserDTO.class);
    }

    public Map<String, Object> get(final String login, final Map<String, String> headers, int statusCode) {
        return getResponse(request().method(GET)
                .path(format(USERS_LOGIN, login))
                .headers(headers)
                .statusCode(statusCode)
                .build()).readEntity(new GenericType<>() {});
    }
}