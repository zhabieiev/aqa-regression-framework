package com.aqa.juiceshop.api.services;

import com.aqa.juiceshop.api.models.generated.LoginRequest;
import com.aqa.juiceshop.api.models.generated.LoginResponse;
import jakarta.ws.rs.core.Response;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.net.HttpURLConnection.HTTP_OK;


public class AuthenticationService extends ApiServices {

    private static final String REST_USER = "/rest/user";
    private static final String REST_USER_LOGIN = REST_USER + "/login";

    public LoginResponse authenticate(LoginRequest body) {
        try (Response response = getResponse(
                request().method(POST).path(REST_USER_LOGIN).body(body).statusCode(HTTP_OK).build())) {
            return response.readEntity(LoginResponse.class);
        }
    }
}
