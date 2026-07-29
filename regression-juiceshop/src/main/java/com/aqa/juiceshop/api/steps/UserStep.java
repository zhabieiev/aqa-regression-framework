package com.aqa.juiceshop.api.steps;

import com.aqa.juiceshop.api.models.generated.LoginRequest;
import com.aqa.juiceshop.api.models.generated.UserRegistrationRequest;
import com.aqa.juiceshop.api.services.AuthenticationService;
import com.aqa.juiceshop.api.services.UserService;


public record UserStep(UserService userService, AuthenticationService authenticationService) {

    public String createAndAuthenticate(UserRegistrationRequest body) {
        userService.create(body);
        return authenticationService.authenticate(toLoginRequest(body)).getAuthentication().getToken();
    }

    private LoginRequest toLoginRequest(UserRegistrationRequest body) {
        return new LoginRequest().email(body.getEmail()).password(body.getPassword());
    }
}
