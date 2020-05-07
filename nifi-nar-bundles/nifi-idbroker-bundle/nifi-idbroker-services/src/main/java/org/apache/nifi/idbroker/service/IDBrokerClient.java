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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.annotations.VisibleForTesting;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.SPNegoScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.StringJoiner;

import static org.apache.http.auth.AuthScope.ANY_HOST;
import static org.apache.http.auth.AuthScope.ANY_PORT;
import static org.apache.nifi.idbroker.service.ConfigService.CAB_API_CREDENTIALS_ENDPOINT;
import static org.apache.nifi.idbroker.service.ConfigService.IDBROKER_TOKEN_ENDPOINT;

public class IDBrokerClient {
    private static final long CACHE_RENEW_TIME_THRESHOLD_MS = 15 * 60 * 1000;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String userName;
    private final String password;

    private final ConfigService configService;
    private final HttpClient httpClient;

    private final HashMap<CloudProviderHandler<?, ?>, IDBrokerToken> cloudProviderToIDBrokerToken = new HashMap<>();
    private final HashMap<CloudProviderHandler<?, ?>, Object> cloudProviderToCredentials = new HashMap<>();

    public IDBrokerClient(String userName, String password, String... configLocations) {
        this(userName, password, new ConfigService(configLocations));
    }

    @VisibleForTesting
    IDBrokerClient(String userName, String password, ConfigService configService) {
        this.userName = userName;
        this.password = password;
        this.configService = configService;
        this.httpClient = createHttpClient();
    }

    <I, C> I getCredentials(CloudProviderHandler<I, C> cloudProvider) {
        IDBrokerToken idBrokerToken = getCachedIdBrokerToken(cloudProvider);

        I credentials = getCachedCloudCredentials(cloudProvider, idBrokerToken);

        return credentials;
    }

    IDBrokerToken getCachedIdBrokerToken(CloudProviderHandler<?, ?> cloudProvider) {
        IDBrokerToken idBrokerToken = cloudProviderToIDBrokerToken.computeIfAbsent(cloudProvider, _cloudProvider -> getIdBrokerToken(_cloudProvider));

        if (expired(idBrokerToken)) {
            cloudProviderToIDBrokerToken.remove(cloudProvider);
            idBrokerToken = cloudProviderToIDBrokerToken.computeIfAbsent(cloudProvider, _cloudProvider -> getIdBrokerToken(_cloudProvider));
        }

        return idBrokerToken;
    }

    IDBrokerToken getIdBrokerToken(CloudProviderHandler<?, ?> cloudProvider) {
        String idBrokerTokenUrl = getIDBrokerTokenUrl(cloudProvider);
        HttpResponse idBrokerTokenResponse = requestIDBrokerToken(idBrokerTokenUrl);

        try (InputStream idBrokerTokenResponseContent = idBrokerTokenResponse.getEntity().getContent()) {
            IDBrokerToken idBrokerToken = mapContent(idBrokerTokenResponseContent, IDBrokerToken.class, PropertyNamingStrategy.SNAKE_CASE);
            return idBrokerToken;
        } catch (IOException e) {
            // TODO Exception during reading id broker token response
            throw new RuntimeException(e);
        }
    }

    <I, C> I getCachedCloudCredentials(CloudProviderHandler<I, C> cloudProvider, IDBrokerToken idBrokerToken) {
        I credentials = (I) cloudProviderToCredentials.computeIfAbsent(cloudProvider, _cloudProvider -> getCredentials(cloudProvider, cloudProvider.getIDBrokerCloudCredentialsType(), idBrokerToken));

        if (expired(cloudProvider, credentials)) {
            cloudProviderToCredentials.remove(cloudProvider);
            credentials = (I) cloudProviderToCredentials.computeIfAbsent(cloudProvider, _cloudProvider -> getCredentials(cloudProvider, cloudProvider.getIDBrokerCloudCredentialsType(), idBrokerToken));
        }

        return credentials;
    }

    <I, C> I getCredentials(CloudProviderHandler<I, C> cloudProvider, Class<I> credentialsType, IDBrokerToken idBrokerToken) {
        String cloudCredentialsUrl = getCredentialsUrl(cloudProvider);
        HttpResponse credentialsResponse = requestCloudCredentials(cloudCredentialsUrl, idBrokerToken);

        try (InputStream credentialsResponseContent = credentialsResponse.getEntity().getContent()) {
            I credentials = mapContent(credentialsResponseContent, credentialsType, PropertyNamingStrategy.UPPER_CAMEL_CASE);

            return credentials;
        } catch (IOException e) {
            // TODO Exception during reading cloud credentials response
            throw new RuntimeException(e);
        }
    }

    String getIDBrokerTokenUrl(CloudProviderHandler<?, ?> cloudProvider) {
        // https://HOST:8444/gateway/dt/knoxtoken/api/v1/token

        StringJoiner urlBuilder = new StringJoiner("/")
            .add(configService.getRootAddress(cloudProvider))
            .add(configService.getDtPath(cloudProvider))
            .add(IDBROKER_TOKEN_ENDPOINT);

        return urlBuilder.toString();
    }

    String getCredentialsUrl(CloudProviderHandler<?, ?> cloudProvider) {
        // https://HOST:8444/gateway/aws-cab/cab/api/v1/credentials

        StringJoiner urlBuilder = new StringJoiner("/")
            .add(configService.getRootAddress(cloudProvider))
            .add(configService.getCabPath(cloudProvider))
            .add(CAB_API_CREDENTIALS_ENDPOINT);

        return urlBuilder.toString();
    }

    HttpResponse requestIDBrokerToken(String idBrokerTokenUrl) {
        HttpResponse idBrokerTokenResponse = runKerberized(() -> executeGetRequest(idBrokerTokenUrl));

        return idBrokerTokenResponse;
    }

    HttpResponse requestCloudCredentials(String credentialsUrl, IDBrokerToken idBrokerToken) {
        HttpResponse cloudCredentialsResponse = executeGetRequest(
            credentialsUrl,
            new BasicHeader("Authorization", "Bearer " + idBrokerToken.getAccessToken())
        );

        return cloudCredentialsResponse;
    }

    <A> A runKerberized(PrivilegedAction<A> privilegedAction) {
        try {
            Subject subject = new Subject();

            CallbackHandler callbackHandler = callbacks -> {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback) callback).setName(userName);
                    }
                    if (callback instanceof PasswordCallback) {
                        ((PasswordCallback) callback).setPassword(password.toCharArray());
                    }
                }
            };

            Configuration loginConfig = new Configuration() {
                @Override
                public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                    return new AppConfigurationEntry[]{
                        new AppConfigurationEntry(
                            "com.sun.security.auth.module.Krb5LoginModule",
                            AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                            new HashMap<>()
                        )
                    };
                }
            };
            LoginContext loginContext = new LoginContext("", subject, callbackHandler, loginConfig);
            loginContext.login();

            Subject serviceSubject = loginContext.getSubject();

            return Subject.doAs(serviceSubject, privilegedAction);
        } catch (Exception e) {
            // TODO Exception during kerberos authentication
            throw new RuntimeException(e);
        }
    }

    HttpResponse executeGetRequest(String url, Header... headers) {
        try {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeaders(headers);

            HttpResponse response = httpClient.execute(httpGet);

            if (response.getStatusLine().getStatusCode() != 200) {
                // TODO Http error code
            }

            return response;
        } catch (IOException e) {
            // TODO Exception during http call
            throw new RuntimeException(e);
        }
    }

    <T> T mapContent(InputStream content, Class<T> type, PropertyNamingStrategy propertyNamingStrategy) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(propertyNamingStrategy);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        T contentAsObject = objectMapper.readValue(content, type);

        return contentAsObject;
    }

    HttpClient createHttpClient() {
        BasicCredentialsProvider credentialsProvider = createCredentialsProvider();

        Lookup<AuthSchemeProvider> authSchemeRegistry = createAuthSchemeRegistry();

        CloseableHttpClient httpClient = createHttpClient(credentialsProvider, authSchemeRegistry);

        return httpClient;
    }

    Lookup<AuthSchemeProvider> createAuthSchemeRegistry() {
        return RegistryBuilder.<AuthSchemeProvider>create()
            .register(AuthSchemes.SPNEGO, __ -> new SPNegoScheme()
            ).build();
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

    private boolean expired(IDBrokerToken idBrokerToken) {
        return expired(idBrokerToken.getExpiresIn());
    }

    private <I, C> boolean expired(CloudProviderHandler<I, C> cloudProvider, I credentials) {
        return expired(cloudProvider.getExpirationTimestamp(credentials));
    }

    private boolean expired(long expirationTimestamp) {
        boolean expired = expirationTimestamp - System.currentTimeMillis() < CACHE_RENEW_TIME_THRESHOLD_MS;

        return expired;
    }
}
