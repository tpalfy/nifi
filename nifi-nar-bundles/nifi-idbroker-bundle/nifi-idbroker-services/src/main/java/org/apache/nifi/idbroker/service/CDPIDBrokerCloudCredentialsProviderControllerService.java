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
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.cloudcredential.service.CloudCredentialsProviderControllerService;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;
import org.apache.nifi.idbroker.domain.CloudProviders;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.hadoop.HadoopValidators;
import org.apache.nifi.reporting.InitializationException;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Retrieves cloud credentials from an IDBroker server
 *
 * @see CloudCredentialsProviderControllerService
 */
@CapabilityDescription("Retrieves cloud credentials from an IDBroker server based on a provided configuration file" +
    " that contains the IDBroker-relates settings (urls) and using a kerberos username/password.")
@Tags({ "cloud", "credentials", "provider" })
@RequiresInstanceClassLoading
public class CDPIDBrokerCloudCredentialsProviderControllerService extends AbstractControllerService implements CloudCredentialsProviderControllerService {
    private static final List<PropertyDescriptor> PROPERTIES;

    public static final PropertyDescriptor CONFIGURATION_RESOURCES = new PropertyDescriptor.Builder()
        .name("config-resources")
        .displayName("Configuration Resources")
        .description("A file or comma separated list of files which contain IDBroker-related configurations in a Hadoop configuration format." )
        .required(true)
        .addValidator(HadoopValidators.ONE_OR_MORE_FILE_EXISTS_VALIDATOR)
        .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
        .build();

    public static final PropertyDescriptor USER_NAME = new PropertyDescriptor.Builder()
        .name("user-name")
        .displayName("User name")
        .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
        .required(true)
        .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
        .description("The Kerberos user name to use for accessing the IDBroker server")
        .build();

    public static final PropertyDescriptor PASSWORD = new PropertyDescriptor.Builder()
        .name("password")
        .displayName("Password")
        .sensitive(true)
        .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
        .required(true)
        .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
        .description("Password for the Kerberos user name to use for accessing the IDBroker server")
        .build();


    private static final Map<Object, CloudProviderHandler> SUPPORTED_CLOUD_PROVIDERS;
    private static <I, C> void addSupportedCredentials(Map<Object, CloudProviderHandler> tmpSupportedCloudProviders, Class<C> nativeCredentialsType, CloudProviderHandler<I, C> cloudProviderHandler) {
        tmpSupportedCloudProviders.put(nativeCredentialsType, cloudProviderHandler);
    }

    static {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(CONFIGURATION_RESOURCES);
        properties.add(USER_NAME);
        properties.add(PASSWORD);
        PROPERTIES = Collections.unmodifiableList(properties);

        Map<Object, CloudProviderHandler> tmpSupportedCloudProviders = new HashMap<>();
        addSupportedCredentials(tmpSupportedCloudProviders, AWSCredentials.class, CloudProviders.AWS);
        SUPPORTED_CLOUD_PROVIDERS = Collections.unmodifiableMap(tmpSupportedCloudProviders);
    }

    private IDBrokerClient idBrokerClient;
    private RetryService retryService;

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return PROPERTIES;
    }

    @OnEnabled
    public void init(final ConfigurationContext context) throws InitializationException {
        String[] configLocations = context.getProperty(CONFIGURATION_RESOURCES).evaluateAttributeExpressions().getValue().split("\\s*,\\s*");
        String userName = context.getProperty(USER_NAME).evaluateAttributeExpressions().getValue();
        String password = context.getProperty(PASSWORD).evaluateAttributeExpressions().getValue();

        try {
            this.idBrokerClient = createIDBrokerClient(configLocations, userName, password);
        } catch (LoginException e) {
            throw new InitializationException("Couldn't create IDBrokerClient", e);
        }

        this.retryService = new RetryService(getLogger());
    }

    @OnDisabled
    public void cleanUp() throws IOException {
        if (idBrokerClient != null) {
            idBrokerClient.cleanUp();
        }
    }

    @Override
    public <C> C getCredentials(Class<C> nativeCredentialsType) {
        C credentials = retryService.tryAction(() -> getCredentialsFromIDBroker(nativeCredentialsType), 3, 10_000);

        return credentials;
    }

    private <I, C> C getCredentialsFromIDBroker(Class<C> nativeCredentialsType) {
        C credentials;

        CloudProviderHandler<I, C> cloudProviderHandler = (CloudProviderHandler<I, C>) SUPPORTED_CLOUD_PROVIDERS.get(nativeCredentialsType);
        if (cloudProviderHandler != null) {
            credentials = cloudProviderHandler.mapCredentials(idBrokerClient.getCredentials(cloudProviderHandler));
        } else {
            throw new ProcessException("Unsupported credentials type: " + nativeCredentialsType.getName());
        }

        return credentials;
    }

    IDBrokerClient createIDBrokerClient(String[] configLocations, String userName, String password) throws LoginException {
        return new IDBrokerClient(userName, password, getLogger(), configLocations);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", CDPIDBrokerCloudCredentialsProviderControllerService.class.getSimpleName() + "[", "]")
            .add("idBrokerClient=" + Optional.ofNullable(idBrokerClient).map(Object::toString).orElse("N/A"))
            .toString();
    }
}
