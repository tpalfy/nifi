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

import com.google.common.base.Preconditions;

public final class TrapV1Configuration implements TrapConfiguration {

    private final String enterpriseOid;
    private final String agentAddress;
    private final int genericTrapType;
    private final Integer specificTrapType;
    private final int timeStamp;

    public TrapV1Configuration(final Builder builder) {
        Preconditions.checkNotNull(builder.enterpiseOid);
        Preconditions.checkNotNull(builder.agentAddress);
        Preconditions.checkArgument(builder.timeStamp >= 0);

        this.enterpriseOid = builder.enterpiseOid;
        this.agentAddress = builder.agentAddress;
        this.genericTrapType = builder.genericTrapType;
        this.specificTrapType = builder.specificTrapType;
        this.timeStamp = builder.timeStamp;
    }

    public String getEnterpriseOid() {
        return enterpriseOid;
    }

    public String getAgentAddress() {
        return agentAddress;
    }

    public int getGenericTrapType() {
        return genericTrapType;
    }

    public Integer getSpecificTrapType() {
        return specificTrapType;
    }

    public int getTimeStamp() {
        return timeStamp;
    }

    @Override
    public String getTrapOidValue() {
        throw new UnsupportedOperationException("Trap OID Value is SNMPv2c/v3 specific property.");
    }

    @Override
    public int getSysUpTime() {
        throw new UnsupportedOperationException("SysUpTime is SNMPv2c/v3 specific property.");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        String enterpiseOid;
        String agentAddress;
        int genericTrapType;
        Integer specificTrapType;
        int timeStamp;

        public Builder enterpriseOid(String enterpiseOid) {
            this.enterpiseOid = enterpiseOid;
            return this;
        }

        public Builder agentAddress(String agentAddress) {
            this.agentAddress = agentAddress;
            return this;
        }

        public Builder genericTrapType(int genericTrapType) {
            this.genericTrapType = genericTrapType;
            return this;
        }

        public Builder specificTrapType(Integer specificTrapType) {
            this.specificTrapType = specificTrapType;
            return this;
        }

        public Builder timeStamp(int timeStamp) {
            this.timeStamp = timeStamp;
            return this;
        }

        public TrapV1Configuration build() {
            return new TrapV1Configuration(this);
        }
    }
}
