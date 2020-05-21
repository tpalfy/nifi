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
package org.apache.nifi.idbroker.service.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.idbroker.service.AbstractIDBrokerCloudCredentialsProviderControllerService;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processors.aws.credentials.provider.service.AWSCredentialsProviderService;
import org.apache.nifi.reporting.InitializationException;

/**
 * Retrieves AWS cloud credentials from an IDBroker server
 */
@CapabilityDescription("Retrieves AWS cloud credentials from an IDBroker server based on a provided configuration file" +
    " that contains the IDBroker-relates settings (urls) and using a kerberos username/password.")
@Tags({ "aws", "cloud", "credentials", "provider" })
@RequiresInstanceClassLoading
public class AWSIDBrokerCloudCredentialsProviderControllerService extends AbstractIDBrokerCloudCredentialsProviderControllerService implements AWSCredentialsProviderService {
    private volatile AWSCredentialsProvider credentialsProvider;

    @OnEnabled
    public void init(final ConfigurationContext context) throws InitializationException {
        super.init(context);

        credentialsProvider = new IDBrokerAWSCredentialsProvider(idBrokerClient, retryService);
    }

    @Override
    public AWSCredentialsProvider getCredentialsProvider() throws ProcessException {
        return credentialsProvider;
    }
}
