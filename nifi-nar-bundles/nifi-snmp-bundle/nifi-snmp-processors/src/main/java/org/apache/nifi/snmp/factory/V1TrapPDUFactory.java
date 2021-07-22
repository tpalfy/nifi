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

import org.apache.nifi.snmp.configuration.TrapV1Configuration;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.Target;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.PDUFactory;

import java.util.Optional;
import java.util.function.Supplier;

public class V1TrapPDUFactory implements Supplier<PDU> {
    private static final PDUFactory v1TrapPduFactory = new DefaultPDUFactory(PDU.V1TRAP);

    final Target target;
    final TrapV1Configuration configuration;

    public V1TrapPDUFactory(Target target, TrapV1Configuration configuration) {
        this.target = target;
        this.configuration = configuration;
    }

    @Override
    public PDU get() {
        final PDUv1 pdu = (PDUv1) v1TrapPduFactory.createPDU(target);
        Optional.ofNullable(configuration.getEnterpriseOid()).map(OID::new).ifPresent(pdu::setEnterprise);
        Optional.ofNullable(configuration.getAgentAddress()).map(IpAddress::new).ifPresent(pdu::setAgentAddress);
        pdu.setGenericTrap(configuration.getGenericTrapType());
        Optional.ofNullable(configuration.getSpecificTrapType()).ifPresent(pdu::setSpecificTrap);
        pdu.setTimestamp(configuration.getTimeStamp());
        return pdu;
    }

}
