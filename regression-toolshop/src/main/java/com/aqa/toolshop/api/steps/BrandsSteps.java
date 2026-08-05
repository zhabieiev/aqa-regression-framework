package com.aqa.toolshop.api.steps;

import com.aqa.toolshop.api.models.generated.BrandRequest;
import com.aqa.toolshop.api.models.generated.BrandResponse;
import com.aqa.toolshop.api.services.AuthService;
import com.aqa.toolshop.api.services.BrandsService;

import java.util.Map;

public record BrandsSteps(BrandsService brandsService, AuthService authService) {

    public BrandResponse deleteAndCreate(final BrandRequest dto) {
        searchAndDelete(dto.getName());
        return brandsService.create(dto);
    }

    public void searchAndDelete(final String name) {
        Map<String, String> adminHeaders = authService.getAdminHeaders();
        brandsService.search(name).forEach(brand -> brandsService.delete(brand.getId(), adminHeaders));
    }
}
