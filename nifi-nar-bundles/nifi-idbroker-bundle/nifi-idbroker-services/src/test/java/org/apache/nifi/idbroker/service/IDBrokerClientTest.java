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

import com.amazonaws.auth.AWSCredentials;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Lookup;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.CloudProviders;
import org.apache.nifi.idbroker.domain.RetryableCommunicationException;
import org.apache.nifi.idbroker.domain.aws.IDBrokerAWSCredentials;
import org.apache.nifi.idbroker.domain.aws.Credentials;
import org.apache.nifi.processor.exception.ProcessException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class IDBrokerClientTest {
    private IDBrokerClient testSubject;

    @Mock
    private CloseableHttpClient httpClient;
    @Mock
    private CloseableHttpResponse idBrokerTokenResponse;
    @Mock
    private StatusLine idBrokerTokenResponseStatusLine;
    @Mock
    private CloseableHttpResponse cloudCredentialsResponse;
    @Mock
    private StatusLine cloudCredentialsResponseStatusLine;
    @Mock
    private HttpEntity idBrokerTokenEntity;
    @Mock
    private HttpEntity cloudCredentialsEntity;

    private boolean testExecuted;

    @Before
    public void setUp() throws Exception {
        this.testExecuted = false;

        initMocks(this);
        this.testSubject = new IDBrokerClient(null, null, "src/test/resources/core-site-local.xml") {
            @Override
            <A> A runKerberized(PrivilegedAction<A> privilegedAction) {
                return privilegedAction.run();
            }

            @Override
            CloseableHttpClient createHttpClient(BasicCredentialsProvider credentialsProvider, Lookup<AuthSchemeProvider> authSchemeRegistry) {
                return httpClient;
            }
        };

        when(idBrokerTokenResponse.getEntity()).thenReturn(idBrokerTokenEntity);
        when(cloudCredentialsResponse.getEntity()).thenReturn(cloudCredentialsEntity);

        when(idBrokerTokenResponse.getStatusLine()).thenReturn(idBrokerTokenResponseStatusLine);
        when(cloudCredentialsResponse.getStatusLine()).thenReturn(cloudCredentialsResponseStatusLine);
    }

    @After
    public void tearDown() throws Exception {
        if (!testExecuted) {
            fail("Test has not been really executed");
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenGetTokenThrowsIOException() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrlToCauseException = "Get token exception";
        Exception expectedException = new RetryableCommunicationException("Got exception while sending request to url 'https://localhost:8444/gateway/dt/knoxtoken/api/v1/token'");

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrlToCauseException;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(IOException.class, e.getCause().getClass());
            assertEquals(expectedIDBrokerTokenUrlToCauseException, e.getCause().getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenTokenNotAvailable() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 404;
        String idBrokerTokenContent = "Resource ot available";

        Exception expectedException = new ProcessException("Request to '" + expectedIDBrokerTokenUrl + "' returned status code '" + requestIdBrokerTokenStatusCode + "', response was:\n" +
            idBrokerTokenContent);

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenTokenServerNotAvailable() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 503;
        String idBrokerTokenContent = "Server not available";

        Exception expectedException = new RetryableCommunicationException("Request to '" + expectedIDBrokerTokenUrl + "' returned status code '" + requestIdBrokerTokenStatusCode + "', response was:\n" +
            idBrokerTokenContent);

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenTokenContentThrowsIOException() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        InputStream idBrokerTokenContent = mock(InputStream.class);
        when(idBrokerTokenContent.read()).thenThrow(new IOException());

        Exception expectedException = new RetryableCommunicationException("Couldn't get response from IDBroker via '" + expectedIDBrokerTokenUrl + "'");

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.idBrokerTokenContent = __ -> idBrokerTokenContent;
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            e.printStackTrace();
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
            assertEquals(IOException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenTokenIsInvalid() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        String idBrokerTokenContent = "InvalidContent";

        Exception expectedException = new ProcessException("Didn't get valid response from IDBroker via '" + expectedIDBrokerTokenUrl + "', response was:\n" +
            idBrokerTokenContent);

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenGetCredentialsThrowsIOException() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        String accessTokenValue = "access_token_value";
        String idBrokerTokenContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":1211" +
            "}";

        String expectedCredentialsUrlToCauseException = "Get credentials exception";
        Exception expectedException = new RetryableCommunicationException("Got exception while sending request to url 'https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials'");

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.accessTokenValue = accessTokenValue;
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;
            testRunner.expectedCredentialsUrl = expectedCredentialsUrlToCauseException;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(IOException.class, e.getCause().getClass());
            assertEquals(expectedCredentialsUrlToCauseException, e.getCause().getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenCredentialsNotAvailable() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        String accessTokenValue = "access_token_value";
        String idBrokerTokenContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":1211" +
            "}";

        String expectedCredentialsUrl = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";
        int requestCloudCredentialsStatusCode = 404;
        String cloudCredentialsContent = "Resource not available";

        Exception expectedException = new ProcessException("Request to '" + expectedCredentialsUrl + "' returned status code '" + requestCloudCredentialsStatusCode + "', response was:\n" +
            cloudCredentialsContent);

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.setRequestCloudCredentialsStatusCode(requestCloudCredentialsStatusCode);
            testRunner.accessTokenValue = accessTokenValue;
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.setCloudCredentialsContent(cloudCredentialsContent);
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;
            testRunner.expectedCredentialsUrl = expectedCredentialsUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenCredentialsServerNotAvailable() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        String accessTokenValue = "access_token_value";
        String idBrokerTokenContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":1211" +
            "}";

        String expectedCredentialsUrl = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";
        int requestCloudCredentialsStatusCode = 503;
        String cloudCredentialsContent = "Server not available";

        Exception expectedException = new RetryableCommunicationException("Request to '" + expectedCredentialsUrl + "' returned status code '" + requestCloudCredentialsStatusCode + "', response was:\n" +
            cloudCredentialsContent);

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.setRequestCloudCredentialsStatusCode(requestCloudCredentialsStatusCode);
            testRunner.accessTokenValue = accessTokenValue;
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.setCloudCredentialsContent(cloudCredentialsContent);
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;
            testRunner.expectedCredentialsUrl = expectedCredentialsUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenCredentialsContentThrowsIOException() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        String accessTokenValue = "access_token_value";
        String idBrokerTokenContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":1211" +
            "}";

        String expectedCredentialsUrl = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";
        int requestCloudCredentialsStatusCode = 200;
        InputStream cloudCredentialsContent = mock(InputStream.class);
        when(cloudCredentialsContent.read()).thenThrow(new IOException());

        Exception expectedException = new RetryableCommunicationException("Couldn't get response from IDBroker via '" + expectedCredentialsUrl + "'");

        // WHEN
        try {

            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.setRequestCloudCredentialsStatusCode(requestCloudCredentialsStatusCode);
            testRunner.accessTokenValue = accessTokenValue;
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.cloudCredentialsContent = __ -> cloudCredentialsContent;
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;
            testRunner.expectedCredentialsUrl = expectedCredentialsUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            e.printStackTrace();
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
            assertEquals(IOException.class, e.getCause().getClass());
        }
    }

    @Test
    public void testGetAWSCredentialsThrowsExceptionWhenCredentialsIsInvalid() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        String accessTokenValue = "access_token_value";
        String idBrokerTokenContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":1211" +
            "}";

        String expectedCredentialsUrl = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";
        int requestCloudCredentialsStatusCode = 200;
        String cloudCredentialsContent = "InvalidContent";

        Exception expectedException = new ProcessException("Didn't get valid response from IDBroker via '" + expectedCredentialsUrl + "', response was:\n" +
            cloudCredentialsContent);

        // WHEN
        try {
            GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
            testRunner.cloudProviderHandler = cloudProviderHandler;
            testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
            testRunner.setRequestCloudCredentialsStatusCode(requestCloudCredentialsStatusCode);
            testRunner.accessTokenValue = accessTokenValue;
            testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
            testRunner.setCloudCredentialsContent(cloudCredentialsContent);
            testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;
            testRunner.expectedCredentialsUrl = expectedCredentialsUrl;

            testRunner.test();

            fail();
        } catch (Exception e) {
            // THEN
            assertEquals(expectedException.getClass(), e.getClass());
            assertEquals(expectedException.getMessage(), e.getMessage());
        }
    }

    @Test
    public void testGetAWSCredentialsReturnsCredentials() throws Exception {
        // GIVEN
        CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> cloudProviderHandler = CloudProviders.AWS;

        String expectedIDBrokerTokenUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
        int requestIdBrokerTokenStatusCode = 200;
        String accessTokenValue = "access_token_value";
        String idBrokerTokenContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":1211" +
            "}";

        String expectedCredentialsUrl = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";
        int requestCloudCredentialsStatusCode = 200;
        String cloudCredentialsContent = "{" +
            "\"AssumedRoleUser\":{" +
            "\"AssumedRole\":\"unimportant\"," +
            "\"Arn\":\"unimportant\"" +
            "}," +
            "\"Credentials\":{" +
            "\"SessionToken\":\"session_token_value\"," +
            "\"AccessKeyId\":\"access_key_id_value\"," +
            "\"SecretAccessKey\":\"secret_access_key_value\"," +
            "\"Expiration\":1211" +
            "}}";

        Credentials credentials = new Credentials();
        credentials.setSessionToken("session_token_value");
        credentials.setAccessKeyId("access_key_id_value");
        credentials.setSecretAccessKey("secret_access_key_value");
        credentials.setExpiration(1211L);

        IDBrokerAWSCredentials expected = new IDBrokerAWSCredentials();
        expected.setCredentials(credentials);

        // WHEN
        // THEN
        GetCredentialsTestRunner<IDBrokerAWSCredentials, AWSCredentials> testRunner = new GetCredentialsTestRunner<>();
        testRunner.cloudProviderHandler = cloudProviderHandler;
        testRunner.setRequestIdBrokerTokenStatusCode(requestIdBrokerTokenStatusCode);
        testRunner.setRequestCloudCredentialsStatusCode(requestCloudCredentialsStatusCode);
        testRunner.accessTokenValue = accessTokenValue;
        testRunner.setIdBrokerTokenContent(idBrokerTokenContent);
        testRunner.setCloudCredentialsContent(cloudCredentialsContent);
        testRunner.expectedIDBrokerTokenUrl = expectedIDBrokerTokenUrl;
        testRunner.expectedCredentialsUrl = expectedCredentialsUrl;
        testRunner.expected = expected;

        testRunner.test();
    }

    private class GetCredentialsTestRunner<I, C> {
        CloudProviderHandler<I, C> cloudProviderHandler;
        Answer<Integer> requestIdBrokerTokenStatusCode = __ -> null;
        Answer<Integer> requestCloudCredentialsStatusCode = __ -> null;
        String accessTokenValue;
        Answer<InputStream> idBrokerTokenContent = __ -> null;
        Answer<InputStream> cloudCredentialsContent = __ -> null;
        String expectedIDBrokerTokenUrl;
        String expectedCredentialsUrl;
        IDBrokerAWSCredentials expected;

        public void setRequestIdBrokerTokenStatusCode(int requestIdBrokerTokenStatusCode) {
            this.requestIdBrokerTokenStatusCode = __ -> requestIdBrokerTokenStatusCode;
        }

        public void setRequestCloudCredentialsStatusCode(int requestCloudCredentialsStatusCode) {
            this.requestCloudCredentialsStatusCode = __ -> requestCloudCredentialsStatusCode;
        }

        public void setIdBrokerTokenContent(String idBrokerTokenContent) {
            this.idBrokerTokenContent = __ -> IOUtils.toInputStream(idBrokerTokenContent, StandardCharsets.UTF_8);
        }

        public void setCloudCredentialsContent(String cloudCredentialsContent) {
            this.cloudCredentialsContent = __ -> IOUtils.toInputStream(cloudCredentialsContent, StandardCharsets.UTF_8);
        }

        public void test() throws Exception {
            testExecuted = true;

            // GIVEN
            when(httpClient.execute(any(HttpUriRequest.class))).thenAnswer((Answer<HttpResponse>) invocation -> {
                HttpGet httpGet = invocation.getArgument(0, HttpGet.class);

                String url = httpGet.getURI().toString();
                Header[] headers = httpGet.getAllHeaders();

                if (!expectedIDBrokerTokenUrl.startsWith("http")) {
                    throw new IOException(expectedIDBrokerTokenUrl);
                } else if (
                    expectedIDBrokerTokenUrl.equals(url)
                        && headers.length == 0
                ) {
                    return idBrokerTokenResponse;
                } else if (!expectedCredentialsUrl.startsWith("http")) {
                    throw new IOException(expectedCredentialsUrl);
                } else if (
                    expectedCredentialsUrl.equals(url)
                        && headers.length == 1
                        && "Authorization".equals(headers[0].getName())
                        && ("Bearer " + accessTokenValue).equals(headers[0].getValue())

                ) {
                    return cloudCredentialsResponse;
                }

                throw new RuntimeException();
            });

            when(idBrokerTokenResponseStatusLine.getStatusCode()).thenAnswer(requestIdBrokerTokenStatusCode);
            when(cloudCredentialsResponseStatusLine.getStatusCode()).thenAnswer(requestCloudCredentialsStatusCode);

            when(idBrokerTokenEntity.getContent()).thenAnswer(idBrokerTokenContent);
            when(cloudCredentialsEntity.getContent()).thenAnswer(cloudCredentialsContent);

            // WHEN
            Object actual = testSubject.getCredentials(cloudProviderHandler);

            // THEN
            assertEquals(expected, actual);
        }
    }

//    @Test
//    public void testAWSGetIDBrokerTokenUrl() throws Exception {
//        // GIVEN
//        String expected = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
//
//        // WHEN
//        String actual = testSubject.getIDBrokerTokenUrl(CloudProviders.AWS);
//
//        // THEN
//        assertEquals(expected, actual);
//    }
//
//    @Test
//    public void testAWSGetCredentialsUrl() throws Exception {
//        // GIVEN
//        String expected = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";
//
//        // WHEN
//        String actual = testSubject.getCredentialsUrl(CloudProviders.AWS);
//
//        // THEN
//        assertEquals(expected, actual);
//    }
//
//    @Test
//    public void testMapIDBrokerToken() throws Exception {
//        // GIVEN
//        String json = "{" +
//            "\"access_token\":\"access_token_value\"," +
//            "\"endpoint_public_cert\":\"unimportant\"," +
//            "\"token_type\":\"Bearer\"," +
//            "\"expires_in\":1211" +
//            "}";
//
//        IDBrokerToken expected = new IDBrokerToken();
//        expected.setAccessToken("access_token_value");
//        expected.setExpiresIn(1211L);
//
//        // WHEN
//        IDBrokerToken actual = testSubject.mapContent(IOUtils.toInputStream(json, StandardCharsets.UTF_8), IDBrokerToken.class, PropertyNamingStrategy.SNAKE_CASE);
//
//        // THEN
//        assertEquals(expected, actual);
//    }
//
//    @Test
//    public void testMapAWSCredentials() throws Exception {
//        // GIVEN
//        String json = "{" +
//            "\"AssumedRoleUser\":{" +
//            "\"AssumedRole\":\"unimportant\"," +
//            "\"Arn\":\"unimportant\"" +
//            "}," +
//            "\"Credentials\":{" +
//            "\"SessionToken\":\"session_token_value\"," +
//            "\"AccessKeyId\":\"access_key_id_value\"," +
//            "\"SecretAccessKey\":\"secret_access_key_value\"," +
//            "\"Expiration\":1211" +
//            "}}";
//
//        Credentials expected = new Credentials();
//        expected.setSessionToken("session_token_value");
//        expected.setAccessKeyId("access_key_id_value");
//        expected.setSecretAccessKey("secret_access_key_value");
//        expected.setExpiration(1211L);
//
//
//        // WHEN
//        IDBrokerAWSCredentials actual = testSubject.mapContent(IOUtils.toInputStream(json, Charset.forName("UTF8")), IDBrokerAWSCredentials.class, PropertyNamingStrategy.UPPER_CAMEL_CASE);
//
//        // THEN
//        assertEquals(expected, actual.getCredentials());
//    }
}
