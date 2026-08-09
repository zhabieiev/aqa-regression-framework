package com.aqa.petstore.api.steps;

import com.aqa.petstore.api.models.generated.ModelApiResponse;
import com.aqa.petstore.api.models.generated.Pet;
import com.aqa.petstore.api.services.PetService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record PetSteps(PetService petService) {

    public Pet create(final Pet body) {
        final Pet response = petService.create(body);

        log.info("Pet with id {} is created", response.getId());

        return response;
    }

    public ModelApiResponse delete(final Long id) {
        final ModelApiResponse response = petService.delete(id);

        log.info("Pet with id {} is deleted", id);

        return response;
    }
}