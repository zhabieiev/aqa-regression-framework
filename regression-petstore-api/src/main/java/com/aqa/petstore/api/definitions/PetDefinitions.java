package com.aqa.petstore.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.petstore.api.models.generated.Pet;
import com.aqa.petstore.api.steps.PetSteps;
import io.cucumber.java.en.Given;

import java.util.Map;

import static com.aqa.core.Populator.populate;

public record PetDefinitions(VariablesController variablesController, PetSteps petSteps) {

    @Given("api user creates pet and saves to {string}:")
    public void createPet(String var, Map<String, String> map) {
        variablesController.setVar(var, petSteps().petService().create(populate(map, Pet.class)));
    }
}
