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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import org.apache.nifi.idbroker.domain.CloudProviders;
import org.apache.nifi.idbroker.domain.aws.IDBrokerAWSCredentials;
import org.apache.nifi.idbroker.service.CachingIDBrokerClient;
import org.apache.nifi.idbroker.service.RetryService;

public class IDBrokerAWSCredentialsProvider implements AWSCredentialsProvider {
    public static final int MAX_TRIES = 3;
    public static final int WAIT_BEFORE_RETRY_MS = 10_000;

    private CachingIDBrokerClient idBrokerClient;
    private RetryService retryService;

    public IDBrokerAWSCredentialsProvider(CachingIDBrokerClient idBrokerClient, RetryService retryService) {
        this.idBrokerClient = idBrokerClient;
        this.retryService = retryService;
    }

    @Override
    public AWSCredentials getCredentials() {
        IDBrokerAWSCredentials idBrokerAWSCredentials = retryService.tryAction(() -> idBrokerClient.getCredentials(CloudProviders.AWS), MAX_TRIES, WAIT_BEFORE_RETRY_MS);

        AWSCredentials awsCredentials = CloudProviders.AWS.mapCredentials(idBrokerAWSCredentials);

        return awsCredentials;
    }

    @Override
    public void refresh() {
        idBrokerClient.clearCache();
        getCredentials();
    }
}
