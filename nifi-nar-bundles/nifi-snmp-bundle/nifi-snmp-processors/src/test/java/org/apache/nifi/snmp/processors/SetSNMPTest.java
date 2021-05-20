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
package org.apache.nifi.snmp.processors;

import org.apache.nifi.snmp.helper.testrunners.SNMPTestRunnerFactory;
import org.apache.nifi.snmp.helper.testrunners.SNMPTestRunners;
import org.apache.nifi.snmp.testagents.TestSNMPV1Agent;
import org.apache.nifi.snmp.utils.SNMPUtils;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.agent.mo.DefaultMOFactory;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SetSNMPTest {

    private static TestSNMPV1Agent snmpV1Agent;
    private static final String TEST_OID = "1.3.6.1.4.1.32437.1.5.1.4.2.0";
    private static final String TEST_OID_VALUE = "TestOID";
    private static final SNMPTestRunners testRunner = SNMPTestRunnerFactory.getTestRunners(SnmpConstants.version1);

    @BeforeClass
    public static void setUp() throws IOException {
        snmpV1Agent = new TestSNMPV1Agent("127.0.0.1");
        snmpV1Agent.start();
        snmpV1Agent.registerManagedObjects(
                DefaultMOFactory.getInstance().createScalar(new OID(TEST_OID), MOAccessImpl.ACCESS_READ_WRITE, new OctetString(TEST_OID_VALUE))
        );
    }

    @AfterClass
    public static void tearDown() {
        snmpV1Agent.stop();
    }

    @Test
    public void testSnmpV1Set() {
        final TestRunner runner = testRunner.createSnmpSetTestRunner(snmpV1Agent.getPort(), TEST_OID, TEST_OID_VALUE);
        runner.run();
        final MockFlowFile successFF = runner.getFlowFilesForRelationship(SetSNMP.REL_SUCCESS).get(0);

        assertNotNull(successFF);
        assertEquals(TEST_OID_VALUE, successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + TEST_OID + SNMPUtils.SNMP_PROP_DELIMITER + "4"));
    }
}
