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

import com.alibaba.fastjson.JSON;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSessionFactory;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.snmp.configuration.SNMPConfiguration;
import org.apache.nifi.snmp.dto.UserDetails;
import org.apache.nifi.snmp.exception.CloseSNMPManagerException;
import org.apache.nifi.snmp.factory.ListenTrapSNMPFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OctetString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class SNMPTrapReceiverHandler {

    private static final Logger logger = LoggerFactory.getLogger(StandardSNMPRequestHandler.class);

    private final SNMPConfiguration configuration;
    private final String usmUsersFilePath;
    private final Snmp snmpManager;

    public SNMPTrapReceiverHandler(final SNMPConfiguration configuration, final String usmUsersFilePath) {
        this.configuration = configuration;
        this.usmUsersFilePath = usmUsersFilePath;
        snmpManager = new ListenTrapSNMPFactory().createSnmpManagerInstance(configuration);
    }

    public void createTrapReceiver(final ProcessContext context, final ProcessSessionFactory processSessionFactory, final ComponentLog logger) {
        addUsmUsers();
        SNMPTrapReceiver trapReceiver = new SNMPTrapReceiver(snmpManager, context, processSessionFactory, logger);
        trapReceiver.init();
    }

    private void addUsmUsers() {
        if (configuration.getVersion() == SnmpConstants.version3) {
            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
            SecurityModels.getInstance().addSecurityModel(usm);

            try (Scanner scanner = new Scanner(new File(usmUsersFilePath))) {
                final String content = scanner.useDelimiter("\\Z").next();
                final List<UserDetails> userDetails = JSON.parseArray(content, UserDetails.class);
                userDetails.forEach(this::addUsmUserFromArray);
            } catch (FileNotFoundException e) {
                throw new ProcessException("USM user file not found, please check the file path and file permissions.", e);
            }

        }
    }

    private void addUsmUserFromArray(final UserDetails userDetails) {
        final UsmUser user = new UsmUser(
                userDetails.getSecurityName(),
                userDetails.getAuthProtocol(),
                userDetails.getAuthPassphrase(),
                userDetails.getPrivProtocol(),
                userDetails.getPrivPassphrase()
        );
        snmpManager.getUSM().addUser(user);
    }

    public void close() {
        try {
            if (snmpManager.getUSM() != null) {
                snmpManager.getUSM().removeAllUsers();
                SecurityModels.getInstance().removeSecurityModel(new Integer32(snmpManager.getUSM().getID()));
            }
            snmpManager.close();
        } catch (IOException e) {
            final String errorMessage = "Could not close SNMP manager.";
            logger.error(errorMessage, e);
            throw new CloseSNMPManagerException(errorMessage);
        }
    }
}
