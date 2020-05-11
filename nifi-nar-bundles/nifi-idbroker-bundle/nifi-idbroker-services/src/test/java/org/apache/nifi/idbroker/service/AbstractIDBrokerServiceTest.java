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

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.nifi.idbroker.domain.RetryableCommunicationException;
import org.apache.nifi.processor.exception.ProcessException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public abstract class AbstractIDBrokerServiceTest<R> {
    protected String expectedUrl;

    @Mock
    protected CloseableHttpClient httpClient;
    @Mock
    protected CloseableHttpResponse response;
    @Mock
    private StatusLine statusLine;
    @Mock
    private HttpEntity responseEntity;

    private boolean testExecuted;
    private GetResourceTestRunner testRunner;

    protected abstract GetResourceTestRunner getTestRunner();

    @Before
    public void setUp() throws Exception {
        this.testRunner = getTestRunner();
        this.testExecuted = false;
        initMocks(this);

        when(response.getStatusLine()).thenReturn(statusLine);
        when(response.getEntity()).thenReturn(responseEntity);
    }

    @After
    public void tearDown() throws Exception {
        if (!testExecuted) {
            fail("Test has not been really executed");
        }
    }

    @Test
    public void testGetResourceThrowsExceptionWhenRequestThrowsIOException() throws Exception {
        // GIVEN
        String expectedUrlToCauseException = "Get credentials exception";

        Exception expectedException = new RetryableCommunicationException(
            "Got exception while sending request to url 'https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials'"
        );

        // WHEN
        try {
            expectedUrl = expectedUrlToCauseException;

            testRunner.test();

            fail();
        } catch (RetryableCommunicationException e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(IOException.class, e.getCause().getClass());
            assertEquals(expectedUrlToCauseException, e.getCause().getMessage());
        }
    }

    @Test
    public void testGetResourceThrowsExceptionWhenResourceNotAvailable() throws Exception {
        // GIVEN
        int statusCode = 404;
        String responseContent = "Resource not available";

        Exception expectedException = new ProcessException(
            "Request to '" + expectedUrl + "' returned status code '" + statusCode + "', response was:\n" + responseContent
        );

        // WHEN
        try {
            testRunner.statusCode = statusCode;
            testRunner.setResponseContent(responseContent);

            testRunner.test();

            fail();
        } catch (ProcessException e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetResourceThrowsExceptionWhenServerNotAvailable() throws Exception {
        // GIVEN
        int statusCode = 503;
        String responseContent = "Server not available";

        Exception expectedException = new RetryableCommunicationException("Request to '" + expectedUrl + "' returned status code '" + statusCode + "', response was:\n" +
            responseContent);

        // WHEN
        try {
            testRunner.statusCode = statusCode;
            testRunner.setResponseContent(responseContent);

            testRunner.test();

            fail();
        } catch (RetryableCommunicationException e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetResourceThrowsExceptionWhenContentThrowsIOException() throws Exception {
        // GIVEN
        int statusCode = 200;
        InputStream responseContent = mock(InputStream.class);
        when(responseContent.read()).thenThrow(new IOException());

        Exception expectedException = new RetryableCommunicationException("Couldn't get response from IDBroker via '" + expectedUrl + "'");

        // WHEN
        try {
            testRunner.statusCode = statusCode;
            testRunner.responseContent = responseContent;

            testRunner.test();
            fail();
        } catch (RetryableCommunicationException e) {
            // THEN
            e.printStackTrace();
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
            assertEquals(IOException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testResourceThrowsExceptionWhenResponseIsInvalid() throws Exception {
        // GIVEN
        int statusCode = 200;
        String responseContent = "InvalidContent";

        Exception expectedException = new ProcessException("Didn't get valid response from IDBroker via '" + expectedUrl + "', response was:\n" +
            responseContent);

        // WHEN
        try {
            testRunner.statusCode = statusCode;
            testRunner.setResponseContent(responseContent, responseContent);

            testRunner.test();

            fail();
        } catch (ProcessException e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    protected void testGetResourceReturnsValidContent(R expected, String responseContent, String... additionalResponseContents) throws Exception {
        // GIVEN
        int statusCode = 200;

        // WHEN
        // THEN
        testRunner.statusCode = statusCode;
        testRunner.setResponseContent(responseContent, additionalResponseContents);
        testRunner.expected = expected;

        testRunner.test();
    }

    protected abstract class GetResourceTestRunner {
        int statusCode;
        InputStream responseContent;
        private InputStream[] additionalResponseContents = new InputStream[0];
        R expected;

        public void setResponseContent(String responseContent, String... additionalResponseContents) {
            this.responseContent = IOUtils.toInputStream(responseContent, StandardCharsets.UTF_8);
            this.additionalResponseContents = Arrays.stream(additionalResponseContents)
                .map(additionalResponseContent -> IOUtils.toInputStream(additionalResponseContent, StandardCharsets.UTF_8))
                .toArray(InputStream[]::new);
        }

        protected abstract R callTestSubject();

        public void test() throws Exception {
            testExecuted = true;

            // GIVEN
            when(httpClient.execute(any(HttpUriRequest.class))).thenAnswer((Answer<HttpResponse>) invocation -> {
                HttpGet httpGet = invocation.getArgument(0, HttpGet.class);

                if (!expectedUrl.startsWith("http")) {
                    throw new IOException(expectedUrl);
                }

                String url = httpGet.getURI().toString();

                assertEquals(expectedUrl, url);
                additionalAssertions(httpGet);

                return response;
            });

            when(statusLine.getStatusCode()).thenReturn(statusCode);
            when(responseEntity.getContent()).thenReturn(responseContent, additionalResponseContents);

            // WHEN
            R actual = callTestSubject();

            // THEN
            assertEquals(expected, actual);
            verify(responseEntity, times(1 + additionalResponseContents.length)).getContent();
        }

        protected void additionalAssertions(HttpGet httpGet) {
        }
    }
}
