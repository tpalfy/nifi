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

import org.apache.nifi.snmp.configuration.SNMPConfiguration;

public interface SNMPConfigurations {

    String DEFAULT_HOST = "127.0.0.1";
    String COMMUNITY_STRING = "public";

    // V3 security (users are set in test agents)
    String SECURITY_LEVEL = "authPriv";
    String SECURITY_NAME = "SHAAES128";
    String AUTH_PROTOCOL = "SHA";
    String AUTH_PASSPHRASE = "SHAAES128AuthPassphrase";
    String PRIV_PROTOCOL = "AES128";
    String PRIV_PASSPHRASE = "SHAAES128PrivPassphrase";

    SNMPConfiguration createSnmpGetSetConfiguration(int agentPort);

    SNMPConfiguration createSnmpGetSetConfigWithCustomHost(final String host, final int agentPort);

    SNMPConfiguration createSnmpListenTrapConfig(final int managerPort);

}
