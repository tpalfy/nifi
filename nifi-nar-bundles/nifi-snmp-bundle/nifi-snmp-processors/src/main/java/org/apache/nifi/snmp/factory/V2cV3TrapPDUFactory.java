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

import org.apache.nifi.snmp.configuration.TrapV2cV3Configuration;
import org.snmp4j.PDU;
import org.snmp4j.Target;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.PDUFactory;
import java.util.function.Supplier;

public class V2cV3TrapPDUFactory implements Supplier<PDU> {
    private static final PDUFactory v2cV3trapPduFactory = new DefaultPDUFactory(PDU.TRAP);

    final Target target;
    final TrapV2cV3Configuration configuration;

    public V2cV3TrapPDUFactory(Target target, TrapV2cV3Configuration configuration) {
        this.target = target;
        this.configuration = configuration;
    }

    @Override
    public PDU get() {
        final PDU pdu = v2cV3trapPduFactory.createPDU(target);
        pdu.add(new VariableBinding(SnmpConstants.sysUpTime, new TimeTicks(configuration.getSysUpTime())));
        pdu.add(new VariableBinding(SnmpConstants.snmpTrapOID, new OctetString(configuration.getTrapOidValue())));
        return pdu;
    }

}
