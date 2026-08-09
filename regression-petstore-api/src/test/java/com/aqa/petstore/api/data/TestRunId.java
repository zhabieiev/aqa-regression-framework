package com.aqa.petstore.api.data;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

public final class TestRunId {

    private static final long NUMERIC_RUN_ID = Instant.now().toEpochMilli() * 1_000_000L +
            Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000L);

    private static final String RUN_ID =
            "%d-%s".formatted(Instant.now().toEpochMilli(), UUID.randomUUID().toString().substring(0, 8));

    private static final AtomicLong NUMERIC_SEQUENCE = new AtomicLong(NUMERIC_RUN_ID);
    private static final AtomicLong STRING_SEQUENCE = new AtomicLong();

    private TestRunId() {
    }

    public static long nextLong() {
        return NUMERIC_SEQUENCE.incrementAndGet();
    }

    public static String unique(final String prefix) {
        final String value = requireNonNull(prefix, "Test data prefix must not be null").trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException("Test data prefix must not be blank");
        }

        return "%s-%s-%d".formatted(value, RUN_ID, STRING_SEQUENCE.incrementAndGet());
    }

    public static String value() {
        return RUN_ID;
    }
}