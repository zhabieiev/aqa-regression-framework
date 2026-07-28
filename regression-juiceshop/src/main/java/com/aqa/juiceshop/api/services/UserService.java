package com.aqa.juiceshop.api.services;

import com.aqa.juiceshop.api.models.generated.UserRegistrationRequest;
import com.aqa.juiceshop.api.models.generated.UserResponse;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.net.HttpURLConnection.HTTP_CREATED;

public final class UserService extends ApiServices {

    private static final String API = "/api";
    private static final String API_USERS = API + "/Users";

    public UserResponse create(UserRegistrationRequest body) {
        return getResponse(
                request().method(POST).path(API_USERS).body(body).statusCode(HTTP_CREATED).build()).readEntity(
                UserResponse.class);
    }
}
