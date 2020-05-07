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

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.apache.commons.io.IOUtils;
import org.apache.nifi.idbroker.domain.CloudProviders;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.apache.nifi.idbroker.domain.aws.IDBrokerAWSCredentials;
import org.apache.nifi.idbroker.domain.aws.Credentials;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class IDBrokerClientTest {
    private IDBrokerClient testSubject;

    @Before
    public void setUp() throws Exception {
        this.testSubject = new IDBrokerClient(null, null, "src/test/resources/core-site-local.xml");
    }

    @Test
    public void testAWSGetIDBrokerTokenUrl() throws Exception {
        // GIVEN
        String expected = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";

        // WHEN
        String actual = testSubject.getIDBrokerTokenUrl(CloudProviders.AWS);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    public void testAWSGetCredentialsUrl() throws Exception {
        // GIVEN
        String expected = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";

        // WHEN
        String actual = testSubject.getCredentialsUrl(CloudProviders.AWS);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    public void testMapIDBrokerToken() throws Exception {
        // GIVEN
        String json = "{" +
            "\"access_token\":\"access_token_value\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":1211" +
            "}";

        IDBrokerToken expected = new IDBrokerToken();
        expected.setAccessToken("access_token_value");
        expected.setExpiresIn(1211L);

        // WHEN
        IDBrokerToken actual = testSubject.mapContent(IOUtils.toInputStream(json, StandardCharsets.UTF_8), IDBrokerToken.class, PropertyNamingStrategy.SNAKE_CASE);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    public void testMapAWSCredentials() throws Exception {
        // GIVEN
        String json = "{" +
            "\"AssumedRoleUser\":{" +
            "\"AssumedRole\":\"unimportant\"," +
            "\"Arn\":\"unimportant\"" +
            "}," +
            "\"Credentials\":{" +
            "\"SessionToken\":\"session_token_value\"," +
            "\"AccessKeyId\":\"access_key_id_value\"," +
            "\"SecretAccessKey\":\"secret_access_key_value\"," +
            "\"Expiration\":1211" +
            "}}";

        Credentials expected = new Credentials();
        expected.setSessionToken("session_token_value");
        expected.setAccessKeyId("access_key_id_value");
        expected.setSecretAccessKey("secret_access_key_value");
        expected.setExpiration(1211L);


        // WHEN
        IDBrokerAWSCredentials actual = testSubject.mapContent(IOUtils.toInputStream(json, Charset.forName("UTF8")), IDBrokerAWSCredentials.class, PropertyNamingStrategy.UPPER_CAMEL_CASE);

        // THEN
        assertEquals(expected, actual.getCredentials());
    }
}
