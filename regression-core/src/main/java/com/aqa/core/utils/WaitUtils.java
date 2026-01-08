package com.aqa.core.utils;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

public class WaitUtils {

    public static <T, U> long waitFor(final BiPredicate<T, U> condition, final T arg1, final U arg2,
                                      final long waitTimeout, final long waitInterval) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + waitTimeout;
        boolean result = false;
        while (System.currentTimeMillis() < endTime) {
            if (condition.test(arg1, arg2)) {
                result = true;
                break;
            }
            try {
                Thread.sleep(waitInterval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (result) {
            return System.currentTimeMillis() - startTime;
        } else {
            throw new RuntimeException("Timeout occurred while waiting for condition.");
        }
    }

    public static long waitFor(final Supplier<Boolean> condition, final long waitTimeout, final long waitInterval) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + waitTimeout;
        boolean result = false;
        while (System.currentTimeMillis() < endTime) {
            if (condition.get().equals(true)) {
                result = true;
                break;
            }
            try {
                Thread.sleep(waitInterval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (result) {
            return System.currentTimeMillis() - startTime;
        } else {
            throw new RuntimeException("Timeout occurred while waiting for condition.");
        }
    }
}
