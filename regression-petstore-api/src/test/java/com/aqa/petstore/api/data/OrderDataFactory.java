package com.aqa.petstore.api.data;

import com.aqa.petstore.api.models.generated.Order;
import net.datafaker.Faker;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aqa.petstore.api.models.generated.Order.StatusEnum.PLACED;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

public final class OrderDataFactory {

    private static final int MIN_ORDER_ID = 1;
    private static final int ORDER_ID_RANGE = 10;

    private static final AtomicInteger ORDER_ID_SEQUENCE =
            new AtomicInteger(Math.floorMod(TestRunId.nextLong(), ORDER_ID_RANGE));

    private OrderDataFactory() {
    }

    public static Order order() {
        return orderForPet(TestRunId.nextLong());
    }

    public static Order orderForPet(final Long petId) {
        final Faker faker = new Faker();

        return new Order().id(nextOrderId())
                .petId(requireNonNull(petId, "Pet id must not be null"))
                .quantity(faker.number().numberBetween(1, 10))
                .shipDate(OffsetDateTime.now(ZoneOffset.UTC)
                        .plusDays(faker.number().numberBetween(1, 30))
                        .truncatedTo(MILLIS))
                .status(PLACED)
                .complete(true);
    }

    private static long nextOrderId() {
        return Math.floorMod(ORDER_ID_SEQUENCE.getAndIncrement(), ORDER_ID_RANGE) + MIN_ORDER_ID;
    }
}