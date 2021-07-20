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
package org.apache.nifi.snmp.helper.configurations;

import org.apache.nifi.snmp.exception.InvalidSnmpVersionException;
import org.snmp4j.mp.SnmpConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SNMPConfigurationFactory {

    private static final Map<Integer, SNMPConfigurations> snmpConfigurationMap;

    static {
        final Map<Integer, SNMPConfigurations> testRunners = new HashMap<>();
        testRunners.put(SnmpConstants.version1, new SNMPV1Configurations());
        testRunners.put(SnmpConstants.version2c, new SNMPV2cConfigurations());
        testRunners.put(SnmpConstants.version3, new SNMPV3Configurations());
        snmpConfigurationMap = Collections.unmodifiableMap(testRunners);
    }

    public static SNMPConfigurations getConfigurations(final int version) {
        return Optional.ofNullable(snmpConfigurationMap.get(version))
                .orElseThrow(() -> new InvalidSnmpVersionException("Invalid SNMP version while creating SNMP configuration."));
    }

}
