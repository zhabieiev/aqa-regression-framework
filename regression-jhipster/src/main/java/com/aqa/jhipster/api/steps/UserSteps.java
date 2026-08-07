package com.aqa.jhipster.api.steps;

import com.aqa.jhipster.api.models.generated.AdminUserDTO;
import com.aqa.jhipster.api.models.generated.User;
import com.aqa.jhipster.api.services.AuthService;
import com.aqa.jhipster.api.services.UserService;

public record UserSteps(UserService userService, AuthService authService) {

    public User deleteAndCreate(final AdminUserDTO body) {
        userService.delete(body.getLogin(), authService.getAdminHeaders());
        return userService.create(body, authService.getAdminHeaders());
    }
}
