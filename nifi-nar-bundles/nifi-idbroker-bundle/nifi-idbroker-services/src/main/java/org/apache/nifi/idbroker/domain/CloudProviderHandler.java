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

public interface CloudProviderHandler<IDBROKER_CLOUD_CREDENTIALS, NATIVE_CLOUD_CREDENTIALS> {
    String getFsName();

    Class<IDBROKER_CLOUD_CREDENTIALS> getIDBrokerCloudCredentialsType();

    long getExpirationTimestamp(IDBROKER_CLOUD_CREDENTIALS credentials);

    NATIVE_CLOUD_CREDENTIALS mapCredentials(IDBROKER_CLOUD_CREDENTIALS idBrokerCloudCredentials);
}
