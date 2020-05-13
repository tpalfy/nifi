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

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.config.Lookup;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.nifi.idbroker.domain.aws.Credentials;
import org.apache.nifi.security.krb.KerberosUser;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.security.auth.login.LoginException;
import java.nio.charset.StandardCharsets;

import static org.apache.nifi.idbroker.domain.CloudProviders.AWS;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Ignore("Manual test")
public class IDBrokerClientManualIT {
    private static final String ACCESS_TOKEN =
        "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJjc3NvX3RwYWxmeSIsImF1ZCI6ImlkYnJva2VyIiwiaXNzIjoiS05PWFNTTyIsImV4cCI6MTU4OTMwMzg1NH0.QwVa0YxDZm0M2JseXgd" +
        "YpcLdHHcIM5swiM-1A67Uu5Fm4gM7cW_aOpCIGrT73jSKdPAE31ykBMpUXiau6j2f7KGN2NHqB7G-30v4qcluQo4th0zegNSBgM6efwRxguUfyaOBz8stTZASDHwNJC6p79vSET2" +
        "fWFrmNeeg8gWBAB0kn3mKqQcltkwD82xZDgv47PL1LH0aaHXkG3_fUUApblT1wi27rV00ZJKLXG1vStkn8f0RJhWSDPpZIJL_1EtqRFpxbeUf3aphi0EqfvArgZcTr--nua69_rE" +
        "u_tsxyXvSNWQu4YGsZQcQj1c_uPVS-Vx8Cs0FpmxLtpqKmdOi1KiO3jGudhJlkdovgQbw7h2qTILUzNLeg5PmGYYdAM0Cpk73I1iMOc1ncT8WrV8AdsY0nAZBy8DoXyQnjsFLLiL" +
        "_gHdBjnxiBi0MnqodS3_LFcLwdcoCFP-Di6k1MUnAMljLqrsToY6pnqYRcZXePsyxcXV4UATA4LMspyi_GZeu";

    private IDBrokerClient testSubject;

    @Before
    public void setUp() throws Exception {
        this.testSubject = new IDBrokerClient(null, null, null, "src/test/resources/core-site-local.xml") {
            @Override
            CloseableHttpClient createHttpClient(BasicCredentialsProvider credentialsProvider, Lookup<AuthSchemeProvider> authSchemeRegistry) {
                try {
                    SSLContext sslContext = new SSLContextBuilder()
                        .loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true)
                        .build();

                    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

                    CloseableHttpClient httpClient = HttpClients.custom()
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                        .setSSLSocketFactory(sslsf)
                        .build();

                    return httpClient;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            TokenService createTokenService(HttpClient httpClient, String userName, String password, ConfigService configService) throws LoginException {
                return new TokenService(httpClient, userName, password, configService, null) {
                    @Override
                    protected KerberosUser createAndLoginKerberosUser(String userName, String password) {
                        return null;
                    }

                    @Override
                    protected HttpResponse requestResource(String url) {
                        try {
                            String responseContent = "{" +
                                "\"access_token\":\"" + ACCESS_TOKEN + "\"," +
                                "\"endpoint_public_cert\":\"unimportant\"," +
                                "\"token_type\":\"Bearer\"," +
                                "\"expires_in\":1211" +
                                "}";

                            HttpResponse response = mock(HttpResponse.class);
                            HttpEntity entity = mock(HttpEntity.class);

                            when(response.getEntity()).thenReturn(entity);
                            when(entity.getContent()).thenReturn(IOUtils.toInputStream(responseContent, StandardCharsets.UTF_8));

                            return response;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
            }
        };
    }

    @Test
    public void testGetAWSCredentials() throws Exception {
        Credentials actual = testSubject.getCredentials(AWS).getCredentials();

        assertEquals(null, actual);
    }
}
