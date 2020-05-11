package org.apache.nifi.idbroker.service;

import org.apache.nifi.idbroker.domain.RetryableCommunicationException;

import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public interface Tenacious {
    default <R> R tryAction(Supplier<R> supplier, int maxTries, long waitBeforeRetryMs) {
        int counter = 1;

        while (true) {
            try {
                R result = supplier.get();

                return result;
            } catch (RetryableCommunicationException e) {
                if (++counter > maxTries) {
                    throw e;
                } else {
                    getErrorLogging().accept("Retryable action threw " + e.getClass().getSimpleName(), e);
                    try {
                        TimeUnit.MILLISECONDS.sleep(waitBeforeRetryMs);
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }
                }
            }
        }
    }

    BiConsumer<String, Throwable> getErrorLogging();
}
