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

import org.apache.nifi.annotation.lifecycle.OnDisabled;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processors.hadoop.HadoopValidators;
import org.apache.nifi.reporting.InitializationException;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Abstract service for retrieving cloud credentials from an IDBroker server
 */
public abstract class AbstractIDBrokerCloudCredentialsProviderControllerService extends AbstractControllerService {
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

    static {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(CONFIGURATION_RESOURCES);
        properties.add(USER_NAME);
        properties.add(PASSWORD);
        PROPERTIES = Collections.unmodifiableList(properties);
    }

    protected CachingIDBrokerClient idBrokerClient;
    protected RetryService retryService;

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

    protected CachingIDBrokerClient createIDBrokerClient(String[] configLocations, String userName, String password) throws LoginException {
        return new CachingIDBrokerClient(userName, password, getLogger(), configLocations);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", this.getClass().getSimpleName() + "[", "]")
            .add("idBrokerClient=" + Optional.ofNullable(idBrokerClient).map(Object::toString).orElse("N/A"))
            .toString();
    }
}
