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
import org.apache.http.auth.AuthScope;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.SPNegoScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.apache.nifi.logging.ComponentLog;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.Principal;

import static org.apache.http.auth.AuthScope.ANY_HOST;
import static org.apache.http.auth.AuthScope.ANY_PORT;

public class IDBrokerClient {
    private final CloseableHttpClient httpClient;
    private final TokenService tokenService;
    private final CredentialService credentialService;

    public IDBrokerClient(String userName, String password, ComponentLog componentLog, String... configLocations) throws LoginException {
        ConfigService configService = new ConfigService(configLocations);

        this.httpClient = createHttpClient();
        this.tokenService = createTokenService(httpClient, userName, password, componentLog, configService);
        this.credentialService = createCredentialService(httpClient, configService);
    }

    <I, C> I getCredentials(CloudProviderHandler<I, C> cloudProvider) {
        IDBrokerToken idBrokerToken = tokenService.getCachedResource(cloudProvider);
        I credentials = credentialService.getCachedCloudCredentials(cloudProvider, idBrokerToken);

        return credentials;
    }

    TokenService createTokenService(HttpClient httpClient, String userName, String password, ComponentLog componentLog, ConfigService configService) throws LoginException {
        return new TokenService(httpClient, userName, password, configService, componentLog);
    }

    CredentialService createCredentialService(HttpClient httpClient, ConfigService configService) {
        return new CredentialService(httpClient, configService);
    }

    CloseableHttpClient createHttpClient() {
        BasicCredentialsProvider credentialsProvider = createCredentialsProvider();

        Lookup<AuthSchemeProvider> authSchemeRegistry = createAuthSchemeRegistry();

        CloseableHttpClient httpClient = createHttpClient(credentialsProvider, authSchemeRegistry);

        return httpClient;
    }

    Lookup<AuthSchemeProvider> createAuthSchemeRegistry() {
        return RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.SPNEGO, __ -> new SPNegoScheme())
            .build();
    }

    BasicCredentialsProvider createCredentialsProvider() {
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            new AuthScope(ANY_HOST, ANY_PORT),
            new org.apache.http.auth.Credentials() {
                @Override
                public Principal getUserPrincipal() {
                    return null;
                }

                @Override
                public String getPassword() {
                    return null;
                }
            }
        );
        return credentialsProvider;
    }

    CloseableHttpClient createHttpClient(BasicCredentialsProvider credentialsProvider, Lookup<AuthSchemeProvider> authSchemeRegistry) {
        CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credentialsProvider)
            .setDefaultAuthSchemeRegistry(authSchemeRegistry)
            .build();

        return httpClient;
    }

    public void cleanUp() throws IOException {
        httpClient.close();
    }
}
