package com.aqa.petstore.api.extensions;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public final class CleanupExtension implements AfterEachCallback {

    private final Deque<Runnable> cleanupActions = new ConcurrentLinkedDeque<>();

    public void register(final Runnable cleanupAction) {
        cleanupActions.addFirst(requireNonNull(cleanupAction, "Cleanup action must not be null"));
    }

    public <T> T register(final T resource, final Consumer<T> cleanupAction) {
        final T value = requireNonNull(resource, "Cleanup resource must not be null");
        final Consumer<T> action = requireNonNull(cleanupAction, "Cleanup action must not be null");

        register(() -> action.accept(value));

        return value;
    }

    @Override
    public void afterEach(final ExtensionContext context) {
        final List<Throwable> failures = new ArrayList<>();

        Runnable cleanupAction;

        while ((cleanupAction = cleanupActions.pollFirst()) != null) {
            try {
                cleanupAction.run();
            } catch (RuntimeException | AssertionError exception) {
                failures.add(exception);
            }
        }

        if (!failures.isEmpty()) {
            final IllegalStateException exception = new IllegalStateException(
                    "Failed to execute %d cleanup action(s) after test '%s'"
                            .formatted(failures.size(), context.getDisplayName())
            );

            failures.forEach(exception::addSuppressed);

            throw exception;
        }
    }
}