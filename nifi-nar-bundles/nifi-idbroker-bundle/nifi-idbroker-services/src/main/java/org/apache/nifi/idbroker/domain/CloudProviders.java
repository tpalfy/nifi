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
package org.apache.nifi.idbroker.domain;

import com.amazonaws.auth.AWSCredentials;
import org.apache.nifi.idbroker.domain.aws.IDBrokerAWSCredentials;

public class CloudProviders {
    public static CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials> AWS = new CloudProviderHandler<IDBrokerAWSCredentials, AWSCredentials>() {
        @Override
        public String getFsName() {
            return "s3a";
        }

        @Override
        public Class<IDBrokerAWSCredentials> getIDBrokerCloudCredentialsType() {
            return IDBrokerAWSCredentials.class;
        }

        @Override
        public AWSCredentials mapCredentials(IDBrokerAWSCredentials idBrokerCloudCredentials) {
            final String accessKeyId = idBrokerCloudCredentials.getCredentials().getAccessKeyId();
            final String secretAccessKey = idBrokerCloudCredentials.getCredentials().getSecretAccessKey();

            AWSCredentials awsCredentials = new AWSCredentials() {
                @Override
                public String getAWSAccessKeyId() {
                    return accessKeyId;
                }

                @Override
                public String getAWSSecretKey() {
                    return secretAccessKey;
                }
            };

            return awsCredentials;
        }

        @Override
        public long getExpirationTimestamp(IDBrokerAWSCredentials idBrokerAWSCredentials) {
            return idBrokerAWSCredentials.getCredentials().getExpiration();
        }
    };
}
