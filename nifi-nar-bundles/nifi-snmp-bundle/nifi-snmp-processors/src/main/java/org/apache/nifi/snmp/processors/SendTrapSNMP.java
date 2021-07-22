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

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.snmp.configuration.TrapV1Configuration;
import org.apache.nifi.snmp.configuration.TrapV2cV3Configuration;
import org.apache.nifi.snmp.factory.V1TrapPDUFactory;
import org.apache.nifi.snmp.factory.V2cV3TrapPDUFactory;
import org.apache.nifi.snmp.operations.SendTrapSNMPHandler;
import org.snmp4j.PDU;
import org.snmp4j.mp.SnmpConstants;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Receiving data from configured SNMP agent which, upon each invocation of
 * {@link #onTrigger(ProcessContext, ProcessSession)} method, will construct a
 * {@link FlowFile} containing in its properties the information retrieved.
 * The output {@link FlowFile} won't have any content.
 */
@Tags({"snmp", "send", "trap"})
@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@CapabilityDescription("Sends information to SNMP Manager.")
public class SendTrapSNMP extends AbstractSNMPProcessor {
    public static final AllowableValue GENERIC_TRAP_TYPE_0 = new AllowableValue("0", "Cold Start",
            "A coldStart trap signifies that the sending protocol entity is reinitializing itself such that" +
                    " the agent's configuration or the protocol entity implementation may be altered.");

    public static final AllowableValue GENERIC_TRAP_TYPE_1 = new AllowableValue("1", "Warm Start",
            "A warmStart trap signifies that the sending protocol entity is reinitializing itself such that" +
                    " neither the agent configuration nor the protocol entity implementation is altered.");

    public static final AllowableValue GENERIC_TRAP_TYPE_2 = new AllowableValue("2", "Link Down",
            "A linkDown trap signifies that the sending protocol entity recognizes a failure in one of " +
                    "the communication links represented in the agent's configuration.");

    public static final AllowableValue GENERIC_TRAP_TYPE_3 = new AllowableValue("3", "Link Up",
            "A linkUp trap signifies that the sending protocol entity recognizes that one of the communication " +
                    "links represented in the agent's configuration has come up.");

    public static final AllowableValue GENERIC_TRAP_TYPE_4 = new AllowableValue("4", "Authentication Failure",
            "An authenticationFailure trap signifies that the sending protocol entity is the addressee of a " +
                    "protocol message that is not properly authenticated.  While implementations of the SNMP must be " +
                    "capable of generating this trap, they must also be capable of suppressing the emission of such traps " +
                    "via an implementation- specific mechanism.");

    public static final AllowableValue GENERIC_TRAP_TYPE_5 = new AllowableValue("5", "EGP Neighbor Loss",
            "An egpNeighborLoss trap signifies that an EGP neighbor for whom the sending protocol entity was " +
                    "an EGP peer has been marked down and the peer relationship no longer obtains.");

    public static final AllowableValue GENERIC_TRAP_TYPE_6 = new AllowableValue("6", "Enterprise Specific",
            "An enterpriseSpecific trap signifies that a particular enterprise-specific trap has occurred which " +
                    "can be defined in the Specific Trap Type field.");

    public static final PropertyDescriptor SNMP_MANAGER_HOST = new PropertyDescriptor.Builder()
            .name("snmp-trap-manager-host")
            .displayName("SNMP Manager Host")
            .description("The host where the SNMP Manager sends the trap.")
            .required(true)
            .defaultValue("localhost")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor SNMP_MANAGER_PORT = new PropertyDescriptor.Builder()
            .name("snmp-trap-manager-port")
            .displayName("SNMP Manager Port")
            .description("The port where the SNMP Manager listens to the incoming traps.")
            .required(true)
            .defaultValue("0")
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .build();

    // SNMPv1 TRAP PROPERTIES

    public static final PropertyDescriptor ENTERPRISE_OID = new PropertyDescriptor.Builder()
            .name("snmp-trap-enterprise-oid")
            .displayName("Enterprise OID")
            .description("Enterprise is the vendor identification (OID) for the network management sub-system that generated the trap")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .dependsOn(SNMP_VERSION, SNMP_V1)
            .build();
    public static final PropertyDescriptor AGENT_ADDRESS = new PropertyDescriptor.Builder()
            .name("snmp-trap-agent-address")
            .displayName("SNMP Trap Agent Address")
            .description("The address where the SNMP Manager sends the trap.")
            .required(true)
            .defaultValue("0")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .dependsOn(SNMP_VERSION, SNMP_V1)
            .build();

    public static final PropertyDescriptor GENERIC_TRAP_TYPE = new PropertyDescriptor.Builder()
            .name("snmp-trap-generic-type")
            .displayName("Generic Trap Type")
            .description("Generic trap type is an integer in the range of 0 to 6. See Usage for details.")
            .required(true)
            .allowableValues(GENERIC_TRAP_TYPE_0, GENERIC_TRAP_TYPE_1, GENERIC_TRAP_TYPE_2, GENERIC_TRAP_TYPE_3,
                    GENERIC_TRAP_TYPE_4, GENERIC_TRAP_TYPE_5, GENERIC_TRAP_TYPE_6)
            .addValidator(StandardValidators.createLongValidator(0, 6, true))
            .dependsOn(SNMP_VERSION, SNMP_V1)
            .build();

    public static final PropertyDescriptor SPECIFIC_TRAP_TYPE = new PropertyDescriptor.Builder()
            .name("snmp-trap-specific-type")
            .displayName("Specific Trap Type")
            .description("Specific trap type is a number that further specifies the nature of the event that generated " +
                    "the trap in the case of traps of generic type 6 (enterpriseSpecific). The interpretation of this " +
                    "code is vendor-specific")
            .required(true)
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .dependsOn(SNMP_VERSION, SNMP_V1)
            .dependsOn(GENERIC_TRAP_TYPE, GENERIC_TRAP_TYPE_6)
            .build();

    public static final PropertyDescriptor TIME_STAMP = new PropertyDescriptor.Builder()
            .name("snmp-trap-timestamp")
            .displayName("Timestamp")
            .description("Timestamp of the trap (unit: 0.01 second).")
            .required(true)
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .dependsOn(SNMP_VERSION, SNMP_V1)
            .build();

    // SNMPv2c/v3 TRAP PROPERTIES

    public static final PropertyDescriptor TRAP_OID_VALUE = new PropertyDescriptor.Builder()
            .name("snmp-trap-oid-value")
            .displayName("Trap OID Value")
            .description("The value of the trap OID")
            .required(false)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .dependsOn(SNMP_VERSION, SNMP_V2C, SNMP_V3)
            .build();

    public static final PropertyDescriptor SYSTEM_UPTIME = new PropertyDescriptor.Builder()
            .name("snmp-trap-sysuptime")
            .displayName("System Uptime")
            .description("System uptime of the trap (unit: 0.01 second)")
            .required(true)
            .addValidator(StandardValidators.POSITIVE_INTEGER_VALIDATOR)
            .dependsOn(SNMP_VERSION, SNMP_V2C, SNMP_V3)
            .build();

    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("All FlowFiles that are received from the SNMP agent are routed to this relationship")
            .build();

    public static final Relationship REL_FAILURE = new Relationship.Builder()
            .name("failure")
            .description("All FlowFiles that cannot received from the SNMP agent are routed to this relationship")
            .build();

    protected static final List<PropertyDescriptor> PROPERTY_DESCRIPTORS = Collections.unmodifiableList(Arrays.asList(
            SNMP_MANAGER_HOST,
            SNMP_MANAGER_PORT,
            SNMP_VERSION,
            SNMP_COMMUNITY,
            SNMP_SECURITY_LEVEL,
            SNMP_SECURITY_NAME,
            SNMP_AUTH_PROTOCOL,
            SNMP_AUTH_PASSWORD,
            SNMP_PRIVACY_PROTOCOL,
            SNMP_PRIVACY_PASSWORD,
            SNMP_RETRIES,
            SNMP_TIMEOUT,
            ENTERPRISE_OID,
            AGENT_ADDRESS,
            GENERIC_TRAP_TYPE,
            SPECIFIC_TRAP_TYPE,
            TIME_STAMP,
            TRAP_OID_VALUE,
            SYSTEM_UPTIME
    ));

    private static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            REL_SUCCESS,
            REL_FAILURE
    )));

    private volatile SendTrapSNMPHandler snmpHandler;

    @OnScheduled
    public void init(ProcessContext context) throws InitializationException {
        initSnmpManager(context);

        final int snmpVersion = context.getProperty(SNMP_VERSION).asInteger();

        Supplier<PDU> pduFactory;
        if (SnmpConstants.version1 == snmpVersion) {
            TrapV1Configuration trapConfiguration = TrapV1Configuration.builder()
                    .enterpriseOid(context.getProperty(ENTERPRISE_OID).getValue())
                    .agentAddress(context.getProperty(AGENT_ADDRESS).getValue())
                    .genericTrapType(context.getProperty(GENERIC_TRAP_TYPE).asInteger())
                    .specificTrapType(context.getProperty(SPECIFIC_TRAP_TYPE).asInteger())
                    .timeStamp(context.getProperty(TIME_STAMP).asInteger())
                    .build();

            pduFactory = new V1TrapPDUFactory(snmpResourceHandler.getTarget(), trapConfiguration);
        } else {
            TrapV2cV3Configuration trapConfiguration = new TrapV2cV3Configuration(
                    context.getProperty(TRAP_OID_VALUE).getValue(),
                    context.getProperty(SYSTEM_UPTIME).asInteger()
            );

            pduFactory = new V2cV3TrapPDUFactory(snmpResourceHandler.getTarget(), trapConfiguration);
        }

        snmpHandler = new SendTrapSNMPHandler(snmpResourceHandler, pduFactory);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession processSession) {
        final FlowFile flowFile = processSession.get();
        try {
            snmpHandler.sendTrap(flowFile);
            if (flowFile != null) {
                processSession.remove(flowFile);
            }
        } catch (IOException e) {
            getLogger().error("Failed to send request to the agent. Check if the agent supports the used version.");
            processSession.transfer(processSession.penalize(flowFile), REL_FAILURE);
            processSession.rollback();
            context.yield();
        }
    }

    @Override
    public Set<Relationship> getRelationships() {
        return RELATIONSHIPS;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return PROPERTY_DESCRIPTORS;
    }

    @Override
    protected String getTargetHost(ProcessContext processContext) {
        return processContext.getProperty(SNMP_MANAGER_HOST).getValue();
    }

    @Override
    protected String getTargetPort(ProcessContext processContext) {
        return processContext.getProperty(SNMP_MANAGER_PORT).getValue();
    }
}
