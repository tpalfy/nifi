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
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.IDBrokerToken;

import java.io.IOException;
import java.io.InputStream;
import java.util.StringJoiner;

import static org.apache.nifi.idbroker.service.ConfigService.CAB_API_CREDENTIALS_ENDPOINT;

public class CredentialService extends AbstractCachingIDBrokerService<Object> {
    private final ConfigService configService;

    private volatile IDBrokerToken idBrokerToken;

    public CredentialService(HttpClient httpClient, ConfigService configService) {
        super(httpClient);
        this.configService = configService;
    }

    @Override
    protected String getUrl(CloudProviderHandler<?, ?> cloudProvider) {
        // e.g. for AWS
        // https://HOST:8444/gateway/aws-cab/cab/api/v1/credentials

        StringJoiner urlBuilder = new StringJoiner("/")
            .add(configService.getRootAddress(cloudProvider))
            .add(configService.getCabPath(cloudProvider))
            .add(CAB_API_CREDENTIALS_ENDPOINT);

        return urlBuilder.toString();
    }

    public <I, C> I getCachedCloudCredentials(CloudProviderHandler<I, C> cloudProvider, IDBrokerToken idBrokerToken) {
        this.idBrokerToken = idBrokerToken;

        return (I) getCachedResource(cloudProvider);
    }

    @Override
    protected HttpResponse requestResource(String url) {
        HttpResponse cloudCredentialsResponse = executeGetRequest(
            url,
            new BasicHeader("Authorization", "Bearer " + idBrokerToken.getAccessToken())
        );

        return cloudCredentialsResponse;
    }

    @Override
    protected Object mapContent(InputStream content, CloudProviderHandler<?, ?> cloudProvider)  throws IOException, JsonParseException, JsonMappingException {
        return mapContent(content, cloudProvider.getIDBrokerCloudCredentialsType(), PropertyNamingStrategy.UPPER_CAMEL_CASE);
    }

    @Override
    protected <I, C> boolean expired(CloudProviderHandler<I, C> cloudProvider, Object resource) {
        return expired(cloudProvider.getExpirationTimestamp((I)resource));
    }
}
