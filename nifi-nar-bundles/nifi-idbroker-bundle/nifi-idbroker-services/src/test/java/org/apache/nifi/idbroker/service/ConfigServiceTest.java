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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigServiceTest {
    private ConfigService testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new ConfigService("src/test/resources/core-site-local.xml");
    }

    @Test
    public void testAWSRootAddress() {
        // GIVEN
        String expected = "https://localhost:8444/gateway";

        // WHEN
        String actual = testSubject.getRootAddress(CloudProviders.AWS);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    public void testAWSDtPath() {
        // GIVEN
        String expected = "dt";

        // WHEN
        String actual = testSubject.getDtPath(CloudProviders.AWS);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    public void testAWSCabPath() {
        // GIVEN
        String expected = "aws-cab";

        // WHEN
        String actual = testSubject.getCabPath(CloudProviders.AWS);

        // THEN
        assertEquals(expected, actual);
    }
}
