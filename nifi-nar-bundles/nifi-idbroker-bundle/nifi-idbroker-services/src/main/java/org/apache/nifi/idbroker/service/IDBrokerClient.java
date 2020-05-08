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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
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
import org.apache.nifi.idbroker.domain.RetryableCommunicationException;
import org.apache.nifi.processor.exception.ProcessException;
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
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.StringJoiner;
import java.util.function.Function;

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
    private final CloseableHttpClient httpClient;

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

    public <I, C> I getCredentials(CloudProviderHandler<I, C> cloudProvider) {
//        IDBrokerToken idBrokerToken = getCachedIdBrokerToken(cloudProvider);
        IDBrokerToken idBrokerToken = new TokenService(httpClient, userName, password, configService){
            @Override
            protected <A> A runKerberized(PrivilegedAction<A> privilegedAction) {
                return privilegedAction.run();
            }
        }.getCachedResource(cloudProvider);

//        I credentials = getCachedCloudCredentials(cloudProvider, idBrokerToken);
        I credentials = new CredentialService(httpClient, configService)
            .getCachedCloudCredentials(cloudProvider, idBrokerToken);

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

    <I, C> I getCachedCloudCredentials(CloudProviderHandler<I, C> cloudProvider, IDBrokerToken idBrokerToken) {
        I credentials = (I) cloudProviderToCredentials.computeIfAbsent(cloudProvider, _cloudProvider -> getCredentials(cloudProvider, cloudProvider.getIDBrokerCloudCredentialsType(), idBrokerToken));

        if (expired(cloudProvider, credentials)) {
            cloudProviderToCredentials.remove(cloudProvider);
            credentials = (I) cloudProviderToCredentials.computeIfAbsent(cloudProvider, _cloudProvider -> getCredentials(cloudProvider, cloudProvider.getIDBrokerCloudCredentialsType(), idBrokerToken));
        }

        return credentials;
    }

    IDBrokerToken getIdBrokerToken(CloudProviderHandler<?, ?> cloudProvider) {
        return getFromIDBroker(
            getIDBrokerTokenUrl(cloudProvider),
            url -> requestIDBrokerToken(url),
            content -> mapContent(content, IDBrokerToken.class, PropertyNamingStrategy.SNAKE_CASE)
        );
    }

    <I, C> I getCredentials(CloudProviderHandler<I, C> cloudProvider, Class<I> credentialsType, IDBrokerToken idBrokerToken) {
        return getFromIDBroker(
            getCredentialsUrl(cloudProvider),
            url -> requestCloudCredentials(url, idBrokerToken),
            content -> mapContent(content, credentialsType, PropertyNamingStrategy.UPPER_CAMEL_CASE)
        );
    }

    <T> T getFromIDBroker(String url, Function<String, HttpResponse> requester, ContentMapper<T> contentMapper) throws RetryableCommunicationException {
        HttpResponse response = requester.apply(url);

        try (InputStream content = response.getEntity().getContent()) {
            try {
                T mappedContent = contentMapper.mapContent(content);

                return mappedContent;
            } catch (JsonParseException | JsonMappingException e) {
                HttpResponse errorHttpResponse = requester.apply(url);

                String errorResponse = IOUtils.toString(errorHttpResponse.getEntity().getContent(), StandardCharsets.UTF_8);

                throw new ProcessException("Didn't get valid response from IDBroker via '" + url + "', response was:\n" + errorResponse, e);
            }
        } catch (IOException e) {
            throw new RetryableCommunicationException("Couldn't get response from IDBroker via '" + url + "'", e);
        }
    }

    String getIDBrokerTokenUrl(CloudProviderHandler<?, ?> cloudProvider) {
        // https://HOST:8444/gateway/dt/knoxtoken/api/v1/token
        // (The result looks provider agnostic)

        StringJoiner urlBuilder = new StringJoiner("/")
            .add(configService.getRootAddress(cloudProvider))
            .add(configService.getDtPath(cloudProvider))
            .add(IDBROKER_TOKEN_ENDPOINT);

        return urlBuilder.toString();
    }

    String getCredentialsUrl(CloudProviderHandler<?, ?> cloudProvider) {
        // e.g. for AWS
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
        } catch (LoginException e) {
            throw new ProcessException("Kerberos authentication error for user '" + userName + "'", e);
        }
    }

    HttpResponse executeGetRequest(String url, Header... headers) {
        try {
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeaders(headers);

            HttpResponse response = httpClient.execute(httpGet);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
                String errorResponse = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                throw new RetryableCommunicationException("Request to '" + url + "' returned status code '" + statusCode + "', response was:\n" + errorResponse);
            } else if (statusCode != HttpServletResponse.SC_OK) {
                String errorResponse = IOUtils.toString(response.getEntity().getContent(), StandardCharsets.UTF_8);
                throw new ProcessException("Request to '" + url + "' returned status code '" + statusCode + "', response was:\n" + errorResponse);
            }

            return response;
        } catch (IOException e) {
            throw new RetryableCommunicationException("Got exception while sending request to url '" + url + "'", e);
        }
    }

    <T> T mapContent(InputStream content, Class<T> type, PropertyNamingStrategy propertyNamingStrategy) throws IOException, JsonParseException, JsonMappingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(propertyNamingStrategy);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        T contentAsObject = objectMapper.readValue(content, type);

        return contentAsObject;
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

    @FunctionalInterface
    private interface ContentMapper<T> {
        T mapContent(InputStream content) throws IOException, JsonParseException, JsonMappingException;
    }
}
