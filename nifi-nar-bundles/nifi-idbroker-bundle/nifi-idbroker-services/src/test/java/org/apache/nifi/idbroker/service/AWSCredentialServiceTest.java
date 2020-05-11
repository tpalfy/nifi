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

import org.apache.http.Header;
import org.apache.http.client.methods.HttpGet;
import org.apache.nifi.idbroker.domain.CloudProviders;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.apache.nifi.idbroker.domain.aws.Credentials;
import org.apache.nifi.idbroker.domain.aws.IDBrokerAWSCredentials;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AWSCredentialServiceTest extends AbstractIDBrokerServiceTest<IDBrokerAWSCredentials> {
    private CredentialService testSubject;

    private final IDBrokerToken idBrokerToken = new IDBrokerToken();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.testSubject = new CredentialService(httpClient, new ConfigService("src/test/resources/core-site-local.xml"));
        expectedUrl = "https://localhost:8444/gateway/aws-cab/cab/api/v1/credentials";

        this.idBrokerToken.setAccessToken("access_token_value");
        this.idBrokerToken.setExpiresIn(0L);
    }

    @Override
    protected AbstractIDBrokerServiceTest.GetResourceTestRunner getTestRunner() {
        return new GetAWSCredentialsTestRunner();
    }

    @Test
    public void testGetAWSCredentialsReturnsCredentials() throws Exception {
        String sessionToken = "session_token_value";
        String accessKeyId = "access_key_id_value";
        String secretAccessKey = "secret_access_key_value";
        long expiration = System.currentTimeMillis() + 60_000_000;

        String responseContent = "{" +
            "\"AssumedRoleUser\":{" +
            "\"AssumedRole\":\"unimportant\"," +
            "\"Arn\":\"unimportant\"" +
            "}," +
            "\"Credentials\":{" +
            "\"SessionToken\":\"" + sessionToken + "\"," +
            "\"AccessKeyId\":\"" + accessKeyId + "\"," +
            "\"SecretAccessKey\":\"" + secretAccessKey + "\"," +
            "\"Expiration\":" + expiration +
            "}}";

        Credentials credentials = new Credentials();
        credentials.setSessionToken(sessionToken);
        credentials.setAccessKeyId(accessKeyId);
        credentials.setSecretAccessKey(secretAccessKey);
        credentials.setExpiration(expiration);

        IDBrokerAWSCredentials expected = new IDBrokerAWSCredentials();
        expected.setCredentials(credentials);

        testGetResourceReturnsValidContent(expected, responseContent);
    }

    @Test
    public void testGetAWSCredentialsReturnsRefreshedCredentials() throws Exception {
        String sessionToken = "session_token_value";
        String accessKeyId = "access_key_id_value";
        String secretAccessKey = "secret_access_key_value";
        long expiration = System.currentTimeMillis() + 60_000_000;

        String expiredResponseContent = "{" +
            "\"AssumedRoleUser\":{" +
            "\"AssumedRole\":\"unimportant\"," +
            "\"Arn\":\"unimportant\"" +
            "}," +
            "\"Credentials\":{" +
            "\"SessionToken\":\"expired\"," +
            "\"AccessKeyId\":\"expired\"," +
            "\"SecretAccessKey\":\"expired\"," +
            "\"Expiration\":0" +
            "}}";

        String responseContent = "{" +
            "\"AssumedRoleUser\":{" +
            "\"AssumedRole\":\"unimportant\"," +
            "\"Arn\":\"unimportant\"" +
            "}," +
            "\"Credentials\":{" +
            "\"SessionToken\":\"" + sessionToken + "\"," +
            "\"AccessKeyId\":\"" + accessKeyId + "\"," +
            "\"SecretAccessKey\":\"" + secretAccessKey + "\"," +
            "\"Expiration\":" + expiration +
            "}}";

        Credentials credentials = new Credentials();
        credentials.setSessionToken(sessionToken);
        credentials.setAccessKeyId(accessKeyId);
        credentials.setSecretAccessKey(secretAccessKey);
        credentials.setExpiration(expiration);

        IDBrokerAWSCredentials expected = new IDBrokerAWSCredentials();
        expected.setCredentials(credentials);

        testGetResourceReturnsValidContent(expected, expiredResponseContent, responseContent);
    }

    private class GetAWSCredentialsTestRunner extends GetResourceTestRunner {
        @Override
        protected IDBrokerAWSCredentials callTestSubject() {
            return testSubject.getCachedCloudCredentials(CloudProviders.AWS, idBrokerToken);
        }

        @Override
        protected void additionalAssertions(HttpGet httpGet) {
            Header[] headers = httpGet.getAllHeaders();

            assertEquals("Authorization", headers[0].getName());
            assertEquals("Bearer " + idBrokerToken.getAccessToken(), headers[0].getValue());
        }
    }
}
