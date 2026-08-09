package com.aqa.petstore.api.tests;

import com.aqa.petstore.api.extensions.CleanupExtension;
import com.aqa.petstore.api.models.generated.Pet;
import com.aqa.petstore.api.services.PetService;
import com.aqa.petstore.api.steps.PetSteps;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.aqa.petstore.api.data.PetDataFactory.pet;
import static io.qameta.allure.SeverityLevel.CRITICAL;
import static org.assertj.core.api.Assertions.assertThat;

@Epic("Petstore API")
@Feature("Pets")
@DisplayName("Pet API")
@Tag("api")
@Tag("pet")
@Tag("regression")
public class PetTest {

    @RegisterExtension
    final CleanupExtension cleanup = new CleanupExtension();

    private final PetSteps petSteps = new PetSteps(new PetService());

    @Test
    @Story("Create pet")
    @Severity(CRITICAL)
    @DisplayName("Creates a pet with valid data")
    @Tag("smoke")
    public void shouldCreatePet() {
        final Pet request = pet();

        final Pet response = cleanup.register(
                petSteps.create(request),
                createdPet -> petSteps.delete(createdPet.getId())
        );

        assertThat(response)
                .usingRecursiveComparison()
                .isEqualTo(request);
    }
}