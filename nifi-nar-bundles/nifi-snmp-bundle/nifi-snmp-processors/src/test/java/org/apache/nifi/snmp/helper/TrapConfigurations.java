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
package org.apache.nifi.snmp.helper;

import org.apache.nifi.snmp.configuration.TrapConfiguration;
import org.apache.nifi.snmp.configuration.TrapV1Configuration;
import org.apache.nifi.snmp.configuration.TrapV2cV3Configuration;
import org.apache.nifi.snmp.exception.InvalidSnmpVersionException;
import org.snmp4j.PDUv1;
import org.snmp4j.mp.SnmpConstants;

public class TrapConfigurations {

    // v1 specific
    private static final String ENTERPRISE_OID = "1.3.5.7.11";
    private static final String AGENT_ADDRESS = "1.2.3.4";
    private static final int GENERIC_TRAP_TYPE = PDUv1.ENTERPRISE_SPECIFIC;
    private static final int SPECIFIC_TRAP_TYPE = 2;
    private static final int TIME_STAMP = 5000;

    // v2c/v3 specific
    private static final String TRAP_OID_VALUE = "testTrapOidValue";
    private static final int SYS_UP_TIME = 5000;

    public static TrapConfiguration getTrapV1Configuration() {
        return TrapV1Configuration.builder()
                .enterpriseOid(ENTERPRISE_OID)
                .agentAddress(AGENT_ADDRESS)
                .genericTrapType(GENERIC_TRAP_TYPE)
                .specificTrapType(SPECIFIC_TRAP_TYPE)
                .timeStamp(TIME_STAMP)
                .build();
    }

    public static TrapConfiguration getTrapV2cV3Configuration() {
        return new TrapV2cV3Configuration(TRAP_OID_VALUE, SYS_UP_TIME);
    }

    public static TrapConfiguration getTrapConfiguration(final int version) {
        if (version == SnmpConstants.version1) {
            return getTrapV1Configuration();
        } else if (version == SnmpConstants.version2c || version == SnmpConstants.version3) {
            return getTrapV2cV3Configuration();
        }
        throw new InvalidSnmpVersionException("Invalid SNMP version while creating trap configuration.");
    }

}
