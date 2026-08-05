package com.aqa.toolshop.api.steps;

import com.aqa.toolshop.api.models.generated.PaginatedUserResponse;
import com.aqa.toolshop.api.models.generated.UserResponse;
import com.aqa.toolshop.api.services.AuthService;

import java.util.List;
import java.util.Map;

public record AuthSteps(AuthService authService) {

    public PaginatedUserResponse searchUsers(final Map<String, String> params) {
        Map<String, String> adminHeaders = authService.getAdminHeaders();
        return authService.searchUsers(params, adminHeaders);
    }
}
