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

import org.apache.nifi.idbroker.domain.CloudProviders;
import org.apache.nifi.idbroker.domain.IDBrokerToken;
import org.junit.Before;
import org.junit.Test;

import java.security.PrivilegedAction;

public class TokenServiceTest extends AbstractIDBrokerServiceTest<IDBrokerToken> {
    private TokenService testSubject;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        this.testSubject = new TokenService(
            httpClient,
            "notTestingAuthentication",
            "notTestingAuthentication",
            new ConfigService("src/test/resources/core-site-local.xml")
        ) {
            @Override
            protected <A> A runKerberized(PrivilegedAction<A> privilegedAction) {
                return privilegedAction.run();
            }
        };
        expectedUrl = "https://localhost:8444/gateway/dt/knoxtoken/api/v1/token";
    }

    @Override
    protected GetResourceTestRunner getTestRunner() {
        return new GetTokenTestRunner();
    }

    @Test
    public void testGetTokenReturnsCredentials() throws Exception {
        String accessTokenValue = "access_token_value";
        long expiresIn = System.currentTimeMillis() + 60_000_000;

        String responseContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":" + expiresIn +
            "}";

        IDBrokerToken expected = new IDBrokerToken();
        expected.setAccessToken(accessTokenValue);
        expected.setExpiresIn(expiresIn);

        testGetResourceReturnsValidContent(expected, responseContent);
    }

    @Test
    public void testGetTokenReturnsRefreshedCredentials() throws Exception {
        String accessTokenValue = "access_token_value";
        long expiresIn = System.currentTimeMillis() + 60_000_000;

        String expiredResponseContent = "{" +
            "\"access_token\":\"expired\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":0" +
            "}";

        String responseContent = "{" +
            "\"access_token\":\"" + accessTokenValue + "\"," +
            "\"endpoint_public_cert\":\"unimportant\"," +
            "\"token_type\":\"Bearer\"," +
            "\"expires_in\":" + expiresIn +
            "}";

        IDBrokerToken expected = new IDBrokerToken();
        expected.setAccessToken(accessTokenValue);
        expected.setExpiresIn(expiresIn);

        testGetResourceReturnsValidContent(expected, expiredResponseContent, responseContent);
    }

    private class GetTokenTestRunner extends GetResourceTestRunner {
        @Override
        protected IDBrokerToken callTestSubject() {
            return testSubject.getCachedResource(CloudProviders.AWS);
        }
    }
}
