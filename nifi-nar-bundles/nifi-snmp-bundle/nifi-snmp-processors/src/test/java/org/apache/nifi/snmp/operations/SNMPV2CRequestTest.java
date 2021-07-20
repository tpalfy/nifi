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
package org.apache.nifi.snmp.operations;

import org.apache.nifi.snmp.configuration.SNMPConfiguration;
import org.apache.nifi.snmp.dto.SNMPSingleResponse;
import org.apache.nifi.snmp.dto.SNMPTreeResponse;
import org.apache.nifi.snmp.exception.RequestTimeoutException;
import org.apache.nifi.snmp.helper.configurations.SNMPConfigurationFactory;
import org.apache.nifi.snmp.helper.configurations.SNMPConfigurations;
import org.apache.nifi.snmp.testagents.TestAgent;
import org.apache.nifi.snmp.testagents.TestSNMPV2cAgent;
import org.apache.nifi.util.MockFlowFile;
import org.junit.Test;
import org.snmp4j.mp.SnmpConstants;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class SNMPV2CRequestTest extends SNMPRequestTest {

    private static final SNMPConfigurations snmpConfigurations = SNMPConfigurationFactory.getConfigurations(SnmpConstants.version2c);

    @Override
    protected TestAgent getAgentInstance() {
        return new TestSNMPV2cAgent(LOCALHOST);
    }

    @Test
    public void testSuccessfulSnmpV1Get() throws IOException {
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        final SNMPSingleResponse response = snmpRequestHandler.get(READ_ONLY_OID_1);

        assertEquals(READ_ONLY_OID_VALUE_1, response.getVariableBindings().get(0).getVariable());
        assertEquals(SUCCESS, response.getErrorStatusText());
        snmpRequestHandler.close();
    }

    @Test
    public void testSuccessfulSnmpV1Walk() {
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        final SNMPTreeResponse response = snmpRequestHandler.walk(WALK_OID);
        assertSubTreeContainsOids(response);
        snmpRequestHandler.close();
    }

    @Test(expected = RequestTimeoutException.class)
    public void testSnmpV1GetTimeoutReturnsNull() throws IOException {
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfigWithCustomHost(INVALID_HOST, agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        snmpRequestHandler.get(READ_ONLY_OID_1);
        snmpRequestHandler.close();
    }

    @Test
    public void testSuccessfulSnmpV1Set() throws IOException {
        final MockFlowFile flowFile = getFlowFile(WRITE_ONLY_OID);
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        final SNMPSingleResponse response = snmpRequestHandler.set(flowFile);

        assertEquals(TEST_OID_VALUE, response.getVariableBindings().get(0).getVariable());
        assertEquals(SUCCESS, response.getErrorStatusText());
        snmpRequestHandler.close();
    }

    @Test
    public void testCannotSetReadOnlyObject() throws IOException {
        final MockFlowFile flowFile = getFlowFile(READ_ONLY_OID_1);
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        final SNMPSingleResponse response = snmpRequestHandler.set(flowFile);

        assertEquals(NOT_WRITABLE, response.getErrorStatusText());
        snmpRequestHandler.close();
    }

    @Test
    public void testCannotGetWriteOnlyObject() throws IOException {
        SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        final SNMPSingleResponse response = snmpRequestHandler.get(WRITE_ONLY_OID);

        assertEquals(NO_ACCESS, response.getErrorStatusText());
        snmpRequestHandler.close();
    }

    @Test
    public void testCannotGetInvalidOid() throws IOException {
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        final SNMPSingleResponse response = snmpRequestHandler.get(INVALID_OID);

        assertEquals(NO_SUCH_OBJECT, response.getVariableBindings().get(0).getVariable());
        assertEquals(SUCCESS, response.getErrorStatusText());
        snmpRequestHandler.close();
    }

    @Test
    public void testCannotSetInvalidOid() throws IOException {
        final MockFlowFile flowFile = getFlowFile(INVALID_OID);
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agent.getPort());
        final SNMPRequestHandler snmpRequestHandler = SNMPRequestHandlerFactory.createStandardRequestHandler(snmpConfiguration);
        final SNMPSingleResponse response = snmpRequestHandler.set(flowFile);

        assertEquals(UNABLE_TO_CREATE_OBJECT, response.getErrorStatusText());
        snmpRequestHandler.close();
    }
}
