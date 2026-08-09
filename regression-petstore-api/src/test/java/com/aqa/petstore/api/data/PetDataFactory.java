package com.aqa.petstore.api.data;

import com.aqa.petstore.api.models.generated.Category;
import com.aqa.petstore.api.models.generated.Pet;
import com.aqa.petstore.api.models.generated.Tag;
import net.datafaker.Faker;

import java.util.List;

import static com.aqa.petstore.api.models.generated.Pet.StatusEnum.AVAILABLE;

public final class PetDataFactory {

    private PetDataFactory() {
    }

    public static Pet pet() {
        final Faker faker = new Faker();

        final Category category =
                new Category().id(TestRunId.nextLong()).name(TestRunId.unique(faker.commerce().department()));

        final Tag tag = new Tag().id(TestRunId.nextLong()).name(TestRunId.unique(faker.lorem().word()));

        return new Pet().id(TestRunId.nextLong())
                .category(category)
                .name(TestRunId.unique(faker.name().firstName()))
                .photoUrls(List.of(faker.internet().url()))
                .tags(List.of(tag))
                .status(AVAILABLE);
    }
}