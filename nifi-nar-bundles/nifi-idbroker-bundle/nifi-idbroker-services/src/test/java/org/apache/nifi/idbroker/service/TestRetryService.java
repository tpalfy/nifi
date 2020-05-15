/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.idbroker.service;

import org.apache.nifi.idbroker.domain.RetryableCommunicationException;
import org.apache.nifi.logging.ComponentLog;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.MockitoAnnotations.initMocks;

public class TestRetryService {
    private RetryService testSubject;

    @Mock
    private ComponentLog logger;

    private List<String> actualErrorMessages;
    private List<Throwable> actualSkippedExceptions;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        this.testSubject = new RetryService(logger);

        doAnswer(invocation -> {
            String errorMessage = invocation.getArgument(0, String.class);
            Throwable throwable = invocation.getArgument(1, Throwable.class);
            actualErrorMessages.add(errorMessage);
            actualSkippedExceptions.add(throwable);

            return null;
        }).when(logger).error(anyString(), any(Throwable.class));

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
