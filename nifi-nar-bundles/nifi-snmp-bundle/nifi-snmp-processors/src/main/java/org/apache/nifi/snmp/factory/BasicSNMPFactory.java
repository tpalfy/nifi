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
package org.apache.nifi.snmp.factory;

import org.apache.nifi.snmp.configuration.SNMPConfiguration;
import org.apache.nifi.snmp.exception.CreateSNMPClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;

public class BasicSNMPFactory {

    private static final Logger logger = LoggerFactory.getLogger(BasicSNMPFactory.class);
    private static final String LOCALHOST = "127.0.0.1";

    public BasicSNMPFactory() {
    }

    public Snmp createSnmpManagerInstance(SNMPConfiguration configuration) {
//        final String managerAddress = LOCALHOST + "/" + configuration.getManagerPort();
        final Snmp snmpManager;
        try {
            snmpManager = new Snmp(new DefaultUdpTransportMapping(new UdpAddress(configuration.getManagerPort())));
            snmpManager.listen();
        } catch (IOException e) {
            final String errorMessage = "Creating SNMP manager failed.";
            logger.error(errorMessage, e);
            throw new CreateSNMPClientException(errorMessage);
        }
        return snmpManager;
    }

    protected void setupTargetBasicProperties(final Target target, final SNMPConfiguration configuration) {
        final int snmpVersion = configuration.getVersion();
        final String host = configuration.getTargetHost();
        final String port = configuration.getTargetPort();
        final int retries = configuration.getRetries();
        final int timeout = configuration.getTimeout();

        target.setVersion(snmpVersion);
        target.setAddress(new UdpAddress(host + "/" + port));
        target.setRetries(retries);
        target.setTimeout(timeout);
    }
}
