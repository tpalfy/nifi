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
package org.apache.nifi.snmp.dto;

import org.apache.nifi.snmp.utils.SNMPUtils;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

public class UserDetails {

    private final OctetString securityName;
    private final OID authProtocol;
    private final OctetString authPassphrase;
    private final OID privProtocol;
    private final OctetString privPassphrase;

    public UserDetails(final String securityName, final String authProtocol, final String authPassphrase, final String privProtocol,
                       final String privPassphrase) {
        this.securityName = new OctetString(securityName);
        this.authProtocol = SNMPUtils.getAuth(authProtocol);
        this.authPassphrase = new OctetString(authPassphrase);
        this.privProtocol = SNMPUtils.getPriv(privProtocol);
        this.privPassphrase = new OctetString(privPassphrase);
    }

    public OctetString getSecurityName() {
        return securityName;
    }

    public OID getAuthProtocol() {
        return authProtocol;
    }

    public OctetString getAuthPassphrase() {
        return authPassphrase;
    }

    public OID getPrivProtocol() {
        return privProtocol;
    }

    public OctetString getPrivPassphrase() {
        return privPassphrase;
    }
}
