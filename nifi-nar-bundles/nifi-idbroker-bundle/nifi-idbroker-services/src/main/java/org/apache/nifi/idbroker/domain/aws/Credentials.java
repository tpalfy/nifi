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
package org.apache.nifi.idbroker.domain.aws;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.StringJoiner;

public class Credentials {
    private String sessionToken;
    private String accessKeyId;
    private String secretAccessKey;
    private Long expiration;

    public Credentials() {
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public Long getExpiration() {
        return expiration;
    }

    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Credentials that = (Credentials) o;

        return new EqualsBuilder()
            .append(sessionToken, that.sessionToken)
            .append(accessKeyId, that.accessKeyId)
            .append(secretAccessKey, that.secretAccessKey)
            .append(expiration, that.expiration)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
            .append(sessionToken)
            .append(accessKeyId)
            .append(secretAccessKey)
            .append(expiration)
            .toHashCode();
    }

    @Override
    public String toString() {
        return new StringJoiner(",\\n  ", this.getClass().getSimpleName() + "[\\n  ", "\\n]")
            .add("sessionToken='" + sessionToken + "'")
            .add("accessKeyId='" + accessKeyId + "'")
            .add("secretAccessKey='" + secretAccessKey + "'")
            .add("expiration=" + expiration)
            .toString();
    }
}
