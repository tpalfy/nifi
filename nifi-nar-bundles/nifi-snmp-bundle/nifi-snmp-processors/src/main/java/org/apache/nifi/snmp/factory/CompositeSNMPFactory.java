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
import org.apache.nifi.snmp.exception.InvalidSnmpVersionException;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.mp.SnmpConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CompositeSNMPFactory implements SNMPFactory {

    private static final String INVALID_SNMP_VERSION = "SNMP version is not supported.";
    private static final Map<Integer, SNMPFactory> FACTORIES;

    static {
        final Map<Integer, SNMPFactory> factories = new HashMap<>();
        factories.put(SnmpConstants.version1, new V1SNMPFactory());
        factories.put(SnmpConstants.version2c, new V2cSNMPFactory());
        factories.put(SnmpConstants.version3, new V3SNMPFactory());
        FACTORIES = Collections.unmodifiableMap(factories);
    }

    @Override
    public Snmp createSnmpManagerInstance(final SNMPConfiguration configuration) {
        final SNMPFactory factory = getMatchingFactory(configuration.getVersion());
        return factory.createSnmpManagerInstance(configuration);
    }

    @Override
    public Target createTargetInstance(SNMPConfiguration configuration) {
        final SNMPFactory factory = getMatchingFactory(configuration.getVersion());
        return factory.createTargetInstance(configuration);
    }

    private SNMPFactory getMatchingFactory(final int version) {
        return Optional.ofNullable(FACTORIES.get(version))
                .orElseThrow(() -> new InvalidSnmpVersionException(INVALID_SNMP_VERSION));
    }
}
