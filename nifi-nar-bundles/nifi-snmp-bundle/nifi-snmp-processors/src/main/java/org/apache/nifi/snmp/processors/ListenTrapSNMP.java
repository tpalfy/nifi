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
import org.apache.nifi.annotation.behavior.RequiresInstanceClassLoading;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.lifecycle.OnStopped;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractSessionFactoryProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.apache.nifi.snmp.configuration.SNMPConfiguration;
import org.apache.nifi.snmp.operations.SNMPTrapReceiverHandler;
import org.apache.nifi.snmp.utils.SNMPUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Receiving data from configured SNMP agent which, upon each invocation of
 * {@link #onTrigger(ProcessContext, ProcessSessionFactory)} method, will construct a
 * {@link FlowFile} containing in its properties the information retrieved.
 * The output {@link FlowFile} won't have any content.
 */
@Tags({"snmp", "listen", "trap"})
@InputRequirement(InputRequirement.Requirement.INPUT_FORBIDDEN)
@CapabilityDescription("Receives information from SNMP Agent and outputs a FlowFile with information in attributes and without any content")
@WritesAttribute(attribute = SNMPUtils.SNMP_PROP_PREFIX + "*", description = "Attributes retrieved from the SNMP response. It may include:"
        + " snmp$errorIndex, snmp$errorStatus, snmp$errorStatusText, snmp$nonRepeaters, snmp$requestID, snmp$type, snmp$variableBindings")
@RequiresInstanceClassLoading
public class ListenTrapSNMP extends AbstractSessionFactoryProcessor {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final String DEFAULT_PORT = "0";

    // SNMP versions
    public static final AllowableValue SNMP_V1 = new AllowableValue("SNMPv1", "v1", "SNMP version 1");
    public static final AllowableValue SNMP_V2C = new AllowableValue("SNMPv2c", "v2c", "SNMP version 2c");
    public static final AllowableValue SNMP_V3 = new AllowableValue("SNMPv3", "v3", "SNMP version 3 with improved security");

    // SNMPv3 security levels
    public static final AllowableValue NO_AUTH_NO_PRIV = new AllowableValue("noAuthNoPriv", "No authentication or encryption",
            "No authentication or encryption.");
    public static final AllowableValue AUTH_NO_PRIV = new AllowableValue("authNoPriv", "Authentication without encryption",
            "Authentication without encryption.");
    public static final AllowableValue AUTH_PRIV = new AllowableValue("authPriv", "Authentication and encryption",
            "Authentication and encryption.");

    public static final PropertyDescriptor SNMP_MANAGER_PORT = new PropertyDescriptor.Builder()
            .name("snmp-manager-port")
            .displayName("SNMP Manager Port")
            .description("The port where the SNMP Manager listens to the incoming traps.")
            .required(true)
            .defaultValue("0")
            .addValidator(StandardValidators.PORT_VALIDATOR)
            .build();

    public static final PropertyDescriptor SNMP_VERSION = new PropertyDescriptor.Builder()
            .name("snmp-version")
            .displayName("SNMP Version")
            .description("Three significant versions of SNMP have been developed and deployed. " +
                    "SNMPv1 is the original version of the protocol. More recent versions, " +
                    "SNMPv2c and SNMPv3, feature improvements in performance, flexibility and security.")
            .required(true)
            .allowableValues("SNMPv1", "SNMPv2c", "SNMPv3")
            .defaultValue("SNMPv1")
            .build();

    public static final PropertyDescriptor SNMP_COMMUNITY = new PropertyDescriptor.Builder()
            .name("snmp-community")
            .displayName("SNMP Community")
            .description("SNMPv1 and SNMPv2 use communities to establish trust between managers and agents." +
                    " Most agents support three community names, one each for read-only, read-write and trap." +
                    " These three community strings control different types of activities. The read-only community" +
                    " applies to get requests. The read-write community string applies to set requests. The trap" +
                    " community string applies to receipt of traps.")
            .required(true)
            .sensitive(true)
            .defaultValue("public")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .dependsOn(SNMP_VERSION, SNMP_V1, SNMP_V2C)
            .build();

    public static final PropertyDescriptor SNMP_SECURITY_LEVEL = new PropertyDescriptor.Builder()
            .name("snmp-security-level")
            .displayName("SNMP Security Level")
            .description("SNMP version 3 provides extra security with User Based Security Model (USM). The three levels of security is " +
                    "1. Communication without authentication and encryption (NoAuthNoPriv). " +
                    "2. Communication with authentication and without encryption (AuthNoPriv). " +
                    "3. Communication with authentication and encryption (AuthPriv).")
            .required(true)
            .allowableValues(NO_AUTH_NO_PRIV, AUTH_NO_PRIV, AUTH_PRIV)
            .defaultValue(NO_AUTH_NO_PRIV.getValue())
            .dependsOn(SNMP_VERSION, SNMP_V3)
            .build();

    public static final PropertyDescriptor SNMP_USM_USERS_FILE_PATH = new PropertyDescriptor.Builder()
            .name("snmp-usm-users-file-path")
            .displayName("SNMP Users File Path")
            .description("The path of the json file containing the user credentials for SNMPv3. Check Usage for more details.")
            .required(true)
            .defaultValue("")
            .dependsOn(SNMP_VERSION, SNMP_V3)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
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
            SNMP_MANAGER_PORT,
            SNMP_VERSION,
            SNMP_COMMUNITY,
            SNMP_SECURITY_LEVEL,
            SNMP_USM_USERS_FILE_PATH
    ));

    private static final Set<Relationship> RELATIONSHIPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            REL_SUCCESS,
            REL_FAILURE
    )));

    private volatile boolean isStarted = false;
    private volatile SNMPTrapReceiverHandler snmpTrapReceiverHandler;

    @OnScheduled
    public void initSnmpManager(ProcessContext context) throws InitializationException {
        final int version = context.getProperty(SNMP_VERSION).asInteger();
        final int managerPort = context.getProperty(SNMP_MANAGER_PORT).asInteger();
        final String usmUsersFilePath = context.getProperty(SNMP_USM_USERS_FILE_PATH).getValue();
        SNMPConfiguration configuration;
        try {
            configuration = SNMPConfiguration.builder()
                    .setManagerPort(managerPort)
                    .setVersion(version)
                    .setSecurityLevel(context.getProperty(SNMP_SECURITY_LEVEL).getValue())
                    .setCommunityString(context.getProperty(SNMP_COMMUNITY).getValue())
                    .build();
        } catch (IllegalStateException e) {
            throw new InitializationException(e);
        }
        snmpTrapReceiverHandler = new SNMPTrapReceiverHandler(configuration, usmUsersFilePath);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSessionFactory processSessionFactory) {
        if (!isStarted) {
            snmpTrapReceiverHandler.createTrapReceiver(context, processSessionFactory, getLogger());
            isStarted = true;
        }
    }

    @OnStopped
    public void close() {
        if (snmpTrapReceiverHandler != null) {
            snmpTrapReceiverHandler.close();
            isStarted = false;
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
}
