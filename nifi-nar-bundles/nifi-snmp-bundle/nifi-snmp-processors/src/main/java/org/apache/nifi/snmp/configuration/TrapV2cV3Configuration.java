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
package org.apache.nifi.snmp.configuration;

public class TrapV2cV3Configuration implements TrapConfiguration {

    private final String trapOidValue;
    private final int sysUpTime;

    public TrapV2cV3Configuration(final String trapOidValue, final int sysUpTime) {
        this.trapOidValue = trapOidValue;
        this.sysUpTime = sysUpTime;
    }

    @Override
    public String getTrapOidValue() {
        return trapOidValue;
    }

    @Override
    public int getSysUpTime() {
        return sysUpTime;
    }

    @Override
    public String getEnterpriseOid() {
        throw new UnsupportedOperationException("Enterprise OID is SNMPv1 specific property.");
    }

    @Override
    public String getAgentAddress() {
        throw new UnsupportedOperationException("Agent address is SNMPv1 specific property.");
    }

    @Override
    public int getGenericTrapType() {
        throw new UnsupportedOperationException("Generic trap type is SNMPv1 specific property.");
    }

    @Override
    public Integer getSpecificTrapType() {
        throw new UnsupportedOperationException("Specific trap type is SNMPv1 specific property.");
    }

    @Override
    public int getTimeStamp() {
        throw new UnsupportedOperationException("Timestamp is SNMPv1 specific property.");
    }
}
