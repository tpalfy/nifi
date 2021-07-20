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

public class SNMPConfiguration {

    private final String managerPort;
    private final String targetHost;
    private final String targetPort;
    private final int retries;
    private final int timeout;
    private final int version;
    private final String authProtocol;
    private final String authPassphrase;
    private final String privacyProtocol;
    private final String privacyPassphrase;
    private final String securityName;
    private final String securityLevel;
    private final String communityString;

    SNMPConfiguration(final String managerPort,
                      final String targetHost,
                      final String targetPort,
                      final int retries,
                      final int timeout,
                      final int version,
                      final String authProtocol,
                      final String authPassphrase,
                      final String privacyProtocol,
                      final String privacyPassphrase,
                      final String securityName,
                      final String securityLevel,
                      final String communityString) {
        this.managerPort = managerPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.retries = retries;
        this.timeout = timeout;
        this.version = version;
        this.authProtocol = authProtocol;
        this.authPassphrase = authPassphrase;
        this.privacyProtocol = privacyProtocol;
        this.privacyPassphrase = privacyPassphrase;
        this.securityName = securityName;
        this.securityLevel = securityLevel;
        this.communityString = communityString;
    }

    public String getManagerPort() {
        return managerPort;
    }

    public String getTargetHost() {
        return targetHost;
    }

    public String getTargetPort() {
        return targetPort;
    }

    public int getRetries() {
        return retries;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getVersion() {
        return version;
    }

    public String getAuthProtocol() {
        return authProtocol;
    }

    public String getAuthPassphrase() {
        return authPassphrase;
    }

    public String getPrivacyProtocol() {
        return privacyProtocol;
    }

    public String getPrivacyPassphrase() {
        return privacyPassphrase;
    }

    public String getSecurityName() {
        return securityName;
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public String getCommunityString() {
        return communityString;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String managerPort = "0";
        private String targetHost;
        private String targetPort;
        private int retries;
        private int timeout = 500;
        private int version;
        private String authProtocol;
        private String authPassphrase;
        private String privacyProtocol;
        private String privacyPassphrase;
        private String securityName;
        private String securityLevel;
        private String communityString;

        public Builder setManagerPort(final String managerPort) {
            this.managerPort = managerPort;
            return this;
        }

        public Builder setTargetHost(final String targetHost) {
            this.targetHost = targetHost;
            return this;
        }

        public Builder setTargetPort(final String targetPort) {
            this.targetPort = targetPort;
            return this;
        }

        public Builder setRetries(final int retries) {
            this.retries = retries;
            return this;
        }

        public Builder setTimeout(final int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder setVersion(final int version) {
            this.version = version;
            return this;
        }

        public Builder setAuthProtocol(final String authProtocol) {
            this.authProtocol = authProtocol;
            return this;
        }

        public Builder setAuthPassphrase(final String authPassphrase) {
            this.authPassphrase = authPassphrase;
            return this;
        }

        public Builder setPrivacyProtocol(final String privacyProtocol) {
            this.privacyProtocol = privacyProtocol;
            return this;
        }

        public Builder setPrivacyPassphrase(final String privacyPassphrase) {
            this.privacyPassphrase = privacyPassphrase;
            return this;
        }

        public Builder setSecurityName(final String securityName) {
            this.securityName = securityName;
            return this;
        }

        public Builder setSecurityLevel(final String securityLevel) {
            this.securityLevel = securityLevel;
            return this;
        }

        public Builder setCommunityString(String communityString) {
            this.communityString = communityString;
            return this;
        }

        public SNMPConfiguration build() {
            return new SNMPConfiguration(managerPort, targetHost, targetPort, retries, timeout, version, authProtocol,
                    authPassphrase, privacyProtocol, privacyPassphrase, securityName, securityLevel, communityString);
        }
    }
}
