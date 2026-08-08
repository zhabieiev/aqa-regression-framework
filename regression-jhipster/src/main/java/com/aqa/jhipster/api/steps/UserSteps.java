package com.aqa.jhipster.api.steps;

import com.aqa.jhipster.api.models.generated.AdminUserDTO;
import com.aqa.jhipster.api.models.generated.User;
import com.aqa.jhipster.api.services.AuthService;
import com.aqa.jhipster.api.services.UserService;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public record UserSteps(UserService userService, AuthService authService) {

    public User deleteAndCreate(final AdminUserDTO body) {
        final Map<String, String> headers = authService.getAdminHeaders();
        delete(body.getLogin(), headers);

        final User response = userService.create(body, headers);
        log.info("User with login {} and id {} is created", body.getLogin(), response.getId());
        return response;
    }

    public AdminUserDTO getUser(final String login) {
        return userService.get(login, authService.getAdminHeaders());
    }

    public Map<String, Object> getUser(final String login, final int statusCode) {
        return userService.get(login, authService.getAdminHeaders(), statusCode);
    }

    public void delete(final String login) {
        delete(login, authService.getAdminHeaders());
    }

    private void delete(final String login, final Map<String, String> headers) {
        userService.delete(login, headers);
        log.info("User deletion with login {} is completed", login);
    }
}