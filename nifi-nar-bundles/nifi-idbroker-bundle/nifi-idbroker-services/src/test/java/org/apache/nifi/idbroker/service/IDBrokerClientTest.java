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

import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Lookup;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class IDBrokerClientTest {
    private IDBrokerClient testSubject;

    @Mock
    private TokenService tokenService;
    @Mock
    private CredentialService credentialService;
    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloudProviderHandler<Object, Object> cloudProviderHandler;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        this.testSubject = new IDBrokerClient(null, null, null, "src/test/resources/core-site-local.xml") {
            @Override
            TokenService createTokenService(HttpClient httpClient, String userName, String password, ConfigService configService) {
                return tokenService;
            }

            @Override
            CredentialService createCredentialService(HttpClient httpClient, ConfigService configService) {
                return credentialService;
            }

            @Override
            CloseableHttpClient createHttpClient(BasicCredentialsProvider credentialsProvider, Lookup<AuthSchemeProvider> authSchemeRegistry) {
                return httpClient;
            }
        };
    }

    @Test
    public void testGetCredentials() throws Exception {
        // GIVEN
        IDBrokerToken token = new IDBrokerToken();
        token.setAccessToken("access_token_value");
        token.setExpiresIn(1L);

        Object expected = "expected";

        when(tokenService.getCachedResource(cloudProviderHandler)).thenReturn(token);
        when(credentialService.getCachedCloudCredentials(cloudProviderHandler, token)).thenReturn(expected);

        // WHEN
        Object actual = testSubject.getCredentials(cloudProviderHandler);

        // THEN
        assertEquals(expected, actual);
    }
}
