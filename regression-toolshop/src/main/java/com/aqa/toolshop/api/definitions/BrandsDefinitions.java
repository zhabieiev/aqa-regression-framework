package com.aqa.toolshop.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.toolshop.api.models.generated.BrandRequest;
import com.aqa.toolshop.api.steps.BrandsSteps;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.aqa.core.Populator.populate;

public record BrandsDefinitions (VariablesController variablesController, BrandsSteps brandsSteps) {

    @When("api user creates new brand and saves to {string}:")
    public void apiUserCreatesNewBrandAndSavesTo(String var, Map<String, String> map) {
        variablesController.setVar(var, brandsSteps.deleteAndCreate(populate(map, BrandRequest.class)));
    }
}
