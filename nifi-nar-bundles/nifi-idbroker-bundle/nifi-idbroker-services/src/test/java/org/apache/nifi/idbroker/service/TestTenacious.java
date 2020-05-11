package org.apache.nifi.idbroker.service;

import org.apache.nifi.idbroker.domain.RetryableCommunicationException;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.MockitoAnnotations.initMocks;

public class TestTenacious {
    private Tenacious testSubject;

    private List<String> actualErrorMessages;
    private List<Throwable> actualSkippedExceptions;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        this.testSubject = new Tenacious() {
            @Override
            public BiConsumer<String, Throwable> getErrorLogging() {
                return (errorMessage, throwable) -> {
                    actualErrorMessages.add(errorMessage);
                    actualSkippedExceptions.add(throwable);
                };
            }
        };

        this.actualErrorMessages = new ArrayList<>();
        this.actualSkippedExceptions = new ArrayList<>();
    }

    @Test
    public void testTryActionThrowsOriginalExceptionAfterTooManyRetries() throws Exception {
        // GIVEN
        int maxTries = 3;
        RetryableCommunicationException skippedException1 = new RetryableCommunicationException("exception1");
        RetryableCommunicationException skippedException2 = new RetryableCommunicationException("exception2");
        RetryableCommunicationException expectedException = new RetryableCommunicationException("exception3");

        Queue<Object> results = new LinkedList<>();
        results.add(skippedException1);
        results.add(skippedException2);
        results.add(expectedException);

        List<String> expectedErrorMessages = Arrays.asList(
            "Retryable action threw " + RetryableCommunicationException.class.getSimpleName(),
            "Retryable action threw " + RetryableCommunicationException.class.getSimpleName()
        );
        List<Throwable> expectedSkippedExceptions = Arrays.asList(skippedException1, skippedException2);

        // WHEN
        try {
            runTryAction(results, maxTries);

            fail();
        } catch (RetryableCommunicationException actualException) {
            // THEN
            assertEquals(expectedException, actualException);
        }

        assertEquals(expectedErrorMessages, actualErrorMessages);
        assertEquals(expectedSkippedExceptions, actualSkippedExceptions);
    }

    @Test
    public void testTryActionReturnsResultAfterSomeRetries() throws Exception {
        // GIVEN
        int maxTries = 5;
        RetryableCommunicationException skippedException1 = new RetryableCommunicationException("exception1");
        RetryableCommunicationException skippedException2 = new RetryableCommunicationException("exception2");
        RetryableCommunicationException skippedException3 = new RetryableCommunicationException("exception3");
        String expected = "expected";

        List<String> expectedErrorMessages = Arrays.asList(
            "Retryable action threw " + RetryableCommunicationException.class.getSimpleName(),
            "Retryable action threw " + RetryableCommunicationException.class.getSimpleName(),
            "Retryable action threw " + RetryableCommunicationException.class.getSimpleName()
        );
        List<Throwable> expectedSkippedExceptions = Arrays.asList(skippedException1, skippedException2, skippedException3);

        Queue<Object> results = new LinkedList<>();
        results.add(skippedException1);
        results.add(skippedException2);
        results.add(skippedException3);
        results.add(expected);

        // WHEN
        Object actual = runTryAction(results, maxTries);

        // THEN
        assertEquals(expected, actual);

        assertEquals(expectedErrorMessages, actualErrorMessages);
        assertEquals(expectedSkippedExceptions, actualSkippedExceptions);
    }

    private Object runTryAction(Queue<Object> results, int maxTries) {
        return testSubject.tryAction(() -> {
            Object result = results.poll();

            if (result instanceof RuntimeException) {
                throw (RuntimeException) result;
            }

            return result;
        }, maxTries, 1);
    }
}
