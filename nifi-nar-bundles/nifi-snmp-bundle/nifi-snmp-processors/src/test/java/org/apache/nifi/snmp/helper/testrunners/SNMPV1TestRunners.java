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
package org.apache.nifi.snmp.helper.testrunners;

import org.apache.nifi.snmp.configuration.SNMPConfiguration;
import org.apache.nifi.snmp.configuration.TrapConfiguration;
import org.apache.nifi.snmp.helper.SNMPTestUtils;
import org.apache.nifi.snmp.helper.TrapConfigurations;
import org.apache.nifi.snmp.helper.configurations.SNMPConfigurationFactory;
import org.apache.nifi.snmp.helper.configurations.SNMPConfigurations;
import org.apache.nifi.snmp.processors.GetSNMP;
import org.apache.nifi.snmp.processors.ListenTrapSNMP;
import org.apache.nifi.snmp.processors.SendTrapSNMP;
import org.apache.nifi.snmp.processors.SetSNMP;
import org.apache.nifi.util.MockFlowFile;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.snmp4j.mp.SnmpConstants;

public class SNMPV1TestRunners implements SNMPTestRunners {


    private static final SNMPConfigurations snmpConfigurations = SNMPConfigurationFactory.getConfigurations(SnmpConstants.version1);

    @Override
    public TestRunner createSnmpGetTestRunner(final int agentPort, final String oid, final String strategy) {
        final TestRunner runner = TestRunners.newTestRunner(GetSNMP.class);
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agentPort);
        runner.setProperty(GetSNMP.AGENT_HOST, snmpConfiguration.getTargetHost());
        runner.setProperty(GetSNMP.AGENT_PORT, snmpConfiguration.getTargetPort());
        runner.setProperty(GetSNMP.SNMP_COMMUNITY, snmpConfiguration.getCommunityString());
        runner.setProperty(GetSNMP.SNMP_VERSION, SNMPTestUtils.getVersionByInt(snmpConfiguration.getVersion()));
        runner.setProperty(GetSNMP.SNMP_STRATEGY, strategy);
        runner.setProperty(GetSNMP.OID, oid);
        return runner;
    }

    @Override
    public TestRunner createSnmpSetTestRunner(final int agentPort, final String oid, final String oidValue) {
        final TestRunner runner = TestRunners.newTestRunner(SetSNMP.class);
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(agentPort);
        runner.setProperty(SetSNMP.AGENT_HOST, snmpConfiguration.getTargetHost());
        runner.setProperty(SetSNMP.AGENT_PORT, snmpConfiguration.getTargetPort());
        runner.setProperty(SetSNMP.SNMP_COMMUNITY, snmpConfiguration.getCommunityString());
        runner.setProperty(SetSNMP.SNMP_VERSION, SNMPTestUtils.getVersionByInt(snmpConfiguration.getVersion()));
        final MockFlowFile flowFile = getFlowFile(oid, oidValue);
        runner.enqueue(flowFile);
        return runner;
    }

    @Override
    public TestRunner createSnmpSendTrapTestRunner(final int managerPort, final String oid, final String oidValue) {
        final TestRunner runner = TestRunners.newTestRunner(SendTrapSNMP.class);
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpGetSetConfiguration(managerPort);
        final TrapConfiguration trapConfiguration = TrapConfigurations.getTrapV1Configuration();
        runner.setProperty(SendTrapSNMP.SNMP_MANAGER_HOST, snmpConfiguration.getTargetHost());
        runner.setProperty(SendTrapSNMP.SNMP_MANAGER_PORT, snmpConfiguration.getTargetPort());
        runner.setProperty(SendTrapSNMP.SNMP_COMMUNITY, snmpConfiguration.getCommunityString());
        runner.setProperty(SendTrapSNMP.SNMP_VERSION, SNMPTestUtils.getVersionByInt(snmpConfiguration.getVersion()));
        runner.setProperty(SendTrapSNMP.ENTERPRISE_OID, trapConfiguration.getEnterpriseOid());
        runner.setProperty(SendTrapSNMP.AGENT_ADDRESS, trapConfiguration.getAgentAddress());
        runner.setProperty(SendTrapSNMP.GENERIC_TRAP_TYPE, String.valueOf(trapConfiguration.getGenericTrapType()));
        runner.setProperty(SendTrapSNMP.SPECIFIC_TRAP_TYPE, String.valueOf(trapConfiguration.getSpecificTrapType()));
        runner.setProperty(SendTrapSNMP.TIME_STAMP, String.valueOf(trapConfiguration.getTimeStamp()));
        final MockFlowFile flowFile = getFlowFile(oid, oidValue);
        runner.enqueue(flowFile);
        return runner;
    }

    @Override
    public TestRunner createSnmpListenTrapTestRunner(final int managerPort) {
        final TestRunner runner = TestRunners.newTestRunner(ListenTrapSNMP.class);
        final SNMPConfiguration snmpConfiguration = snmpConfigurations.createSnmpListenTrapConfig(managerPort);
        runner.setProperty(ListenTrapSNMP.SNMP_MANAGER_PORT, snmpConfiguration.getManagerPort());
        runner.setProperty(ListenTrapSNMP.SNMP_COMMUNITY, snmpConfiguration.getCommunityString());
        runner.setProperty(ListenTrapSNMP.SNMP_VERSION, SNMPTestUtils.getVersionByInt(snmpConfiguration.getVersion()));
        return runner;
    }
}
