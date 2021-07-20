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

import org.apache.nifi.remote.io.socket.NetworkUtils;
import org.apache.nifi.snmp.configuration.SNMPConfiguration;
import org.apache.nifi.snmp.configuration.TrapConfiguration;
import org.apache.nifi.snmp.helper.SNMPTestUtils;
import org.apache.nifi.snmp.helper.TrapConfigurations;
import org.apache.nifi.snmp.helper.configurations.SNMPConfigurationFactory;
import org.apache.nifi.snmp.helper.configurations.SNMPConfigurations;
import org.apache.nifi.snmp.helper.testrunners.SNMPTestRunnerFactory;
import org.apache.nifi.snmp.helper.testrunners.SNMPTestRunners;
import org.apache.nifi.snmp.operations.SNMPRequestHandler;
import org.apache.nifi.snmp.operations.SNMPRequestHandlerFactory;
import org.apache.nifi.snmp.utils.SNMPUtils;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.TimeTicks;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ListenTrapSNMPTest {

    protected static final String SYSTEM_DESCRIPTION_OID = "1.3.6.1.2.1.1.1.0";
    protected static final String SYSTEM_DESCRIPTION_OID_VALUE = "optionalTrapOidTestValue";

    @Test
    public void testReceiveV1Trap() throws IOException, InterruptedException {
        trapReceiveTest(SnmpConstants.version1);
    }

    @Test
    public void testReceiveV2Trap() throws IOException, InterruptedException {
        trapReceiveTest(SnmpConstants.version2c);
    }

    @Ignore
    @Test
    public void testReceiveV3Trap() throws IOException, InterruptedException {
        // The ListenTrapSNMP and SendTrapSNMP processors use the same SecurityProtocols instance
        // and same USM (the USM is stored in a map by version), hence this case shall be manually tested.
        // Check assertByVersion() to see what the trap payload must contain.
    }

    private void trapReceiveTest(int version) throws IOException, InterruptedException {

        final int listenPort = NetworkUtils.availablePort();

        // Create ListenTrapSNMP processor runner.
        final SNMPTestRunners testRunners = SNMPTestRunnerFactory.getTestRunners(version);
        final TestRunner testRunner = testRunners.createSnmpListenTrapTestRunner(listenPort);

        testRunner.run(1, false);

        final SNMPConfigurations snmpConfigurations = SNMPConfigurationFactory.getConfigurations(version);
        final SNMPConfiguration sendTrapConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(listenPort);
        final TrapConfiguration trapConfiguration = TrapConfigurations.getTrapConfiguration(version);
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(sendTrapConfiguration);
        final MockFlowFile flowFile = SNMPTestUtils.getFlowFile(SYSTEM_DESCRIPTION_OID, SYSTEM_DESCRIPTION_OID_VALUE);

        snmpRequestHandler.sendTrap(trapConfiguration, flowFile);

        Thread.sleep(50);

        assertByVersion(version, testRunner, trapConfiguration);

        testRunner.shutdown();
        snmpRequestHandler.close();
    }

    private void assertByVersion(final int version, final TestRunner testRunner, final TrapConfiguration trapConfiguration) {
        final MockFlowFile successFF = testRunner.getFlowFilesForRelationship(GetSNMP.REL_SUCCESS).get(0);
        assertNotNull(successFF);
        assertEquals("Success", successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + "errorStatusText"));

        if (SnmpConstants.version1 == version) {
            assertEquals(trapConfiguration.getEnterpriseOid(), successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + "enterprise"));
            assertEquals(trapConfiguration.getAgentAddress(), successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + "agentAddress"));
            assertEquals(String.valueOf(trapConfiguration.getGenericTrapType()), successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + "genericTrapType"));
            assertEquals(String.valueOf(trapConfiguration.getSpecificTrapType()), successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + "specificTrapType"));
            assertEquals(String.valueOf(trapConfiguration.getTimeStamp()), successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + "timestamp"));

        } else {
            assertEquals(String.valueOf(new TimeTicks(trapConfiguration.getSysUpTime())), successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX
                    + SnmpConstants.sysUpTime + SNMPUtils.SNMP_PROP_DELIMITER + "67"));
            assertEquals(trapConfiguration.getTrapOidValue(), successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + SnmpConstants.snmpTrapOID
                    + SNMPUtils.SNMP_PROP_DELIMITER + "4"));
        }
        assertEquals(SYSTEM_DESCRIPTION_OID_VALUE, successFF.getAttribute(SNMPUtils.SNMP_PROP_PREFIX + SYSTEM_DESCRIPTION_OID
                + SNMPUtils.SNMP_PROP_DELIMITER + "4"));
    }
}
