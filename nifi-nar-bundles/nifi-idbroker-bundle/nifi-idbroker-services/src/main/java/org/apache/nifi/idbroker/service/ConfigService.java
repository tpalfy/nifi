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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.nifi.idbroker.domain.CloudProviderHandler;

public class ConfigService {
    public static final String CAB_ADDRESS ="fs.%s.ext.cab.address";  // http://HOST:8444/gateway
    public static final String CAB_ADDRESS_DT_PATH ="fs.%s.ext.cab.dt.path"; // dt
    public static final String CAB_ADDRESS_CAB_PATH ="fs.%s.ext.cab.path"; // aws-cab
    public static final String IDBROKER_TOKEN_ENDPOINT = "knoxtoken/api/v1/token";
    public static final String CAB_API_CREDENTIALS_ENDPOINT = "cab/api/v1/credentials";

    private final Configuration hadoopConfiguration;

    public ConfigService(String... configLocations) {
        Configuration hadoopConfiguration = new Configuration();

        for (String configLocation : configLocations) {
            hadoopConfiguration.addResource(new Path(configLocation));
        }

        this.hadoopConfiguration = hadoopConfiguration;
    }

    public String getRootAddress(CloudProviderHandler<?, ?> cloudProvider) {
        return getStringPropertyValue(cloudProvider, CAB_ADDRESS);
    }

    public String getDtPath(CloudProviderHandler<?, ?> cloudProvider) {
        return getStringPropertyValue(cloudProvider, CAB_ADDRESS_DT_PATH);
    }

    public String getCabPath(CloudProviderHandler<?, ?> cloudProvider) {
        return getStringPropertyValue(cloudProvider, CAB_ADDRESS_CAB_PATH);
    }

    private String getStringPropertyValue(CloudProviderHandler<?, ?> cloudProvider, String propertyName) {
        return hadoopConfiguration.get(String.format(propertyName, cloudProvider.getFsName()));
    }
}
