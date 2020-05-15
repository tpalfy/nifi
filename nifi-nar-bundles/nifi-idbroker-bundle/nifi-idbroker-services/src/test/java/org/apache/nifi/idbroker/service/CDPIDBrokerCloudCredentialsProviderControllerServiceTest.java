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
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.CloudProviders;
import org.apache.nifi.idbroker.domain.aws.Credentials;
import org.apache.nifi.idbroker.domain.aws.IDBrokerAWSCredentials;
import org.apache.nifi.processor.exception.ProcessException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.apache.nifi.idbroker.service.CDPIDBrokerCloudCredentialsProviderControllerService.CONFIGURATION_RESOURCES;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CDPIDBrokerCloudCredentialsProviderControllerServiceTest {
    private CDPIDBrokerCloudCredentialsProviderControllerService testSubject;

    @Mock
    private IDBrokerClient idBrokerClient;

    private ConfigurationContext context;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        this.testSubject = new CDPIDBrokerCloudCredentialsProviderControllerService() {
            @Override
            IDBrokerClient createIDBrokerClient(String[] configLocations, String userName, String password) {
                return idBrokerClient;
            }
        };

        this.context = mock(ConfigurationContext.class, RETURNS_DEEP_STUBS);

        when(context.getProperty(CONFIGURATION_RESOURCES).evaluateAttributeExpressions().getValue()).thenReturn("unimportant");

        this.testSubject.init(context);
    }

    @Test
    public void testGetCredentialsThrowsExceptionWhenCloudProviderHandlerNotSupported() throws Exception {
        // GIVEN
        String credentials = "credentials";

        CloudProviderHandler<Object, String> unsupportedCloudProviderHandler = mock(CloudProviderHandler.class);

        when(idBrokerClient.getCredentials(unsupportedCloudProviderHandler)).thenReturn(credentials);

        // WHEN
        try {
            testGetCredentials(
                String.class,
                "N/A",
                Collections.emptyList()
            );
        } catch (ProcessException e) {
            // THEN
            assertEquals("Unsupported credentials type: " + String.class.getName(), e.getMessage());
        }
    }

    @Test
    public void testGetAWSCredentials() throws Exception {
        // GIVEN
        final String accessKeyId = "access_key_id";
        final String secretKey = "secret_key";

        Credentials credentials = new Credentials();
        credentials.setAccessKeyId(accessKeyId);
        credentials.setSecretAccessKey(secretKey);

        IDBrokerAWSCredentials idBrokerAWSCredentials = new IDBrokerAWSCredentials();
        idBrokerAWSCredentials.setCredentials(credentials);

        AWSCredentials expected = new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return accessKeyId;
            }

            @Override
            public String getAWSSecretKey() {
                return secretKey;
            }
        };
        when(idBrokerClient.getCredentials(CloudProviders.AWS)).thenReturn(idBrokerAWSCredentials);

        // WHEN
        // THEN
        testGetCredentials(
            AWSCredentials.class,
            expected,
            Arrays.asList(
                AWSCredentials::getAWSAccessKeyId,
                AWSCredentials::getAWSSecretKey
            )
        );
    }

    public <C> void testGetCredentials(Class<C> nativeCredentialsType, C expected, List<Function<C, Object>> equalsPropertyProviders) {
        // GIVEN

        // WHEN
        C actual = testSubject.getCredentials(nativeCredentialsType);

        // THEN

        assertEquals(
            new Equalizer<>(expected, equalsPropertyProviders),
            new Equalizer<>(actual, equalsPropertyProviders)
        );
    }
}
