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
package org.apache.nifi.validation;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.nifi.flowanalysis.FlowAnalysisRuleType;

import java.util.StringJoiner;

public class RuleViolation {
    private final FlowAnalysisRuleType ruleType;
    private final String subjectId;
    private final String scope;
    private final String ruleId;
    private final String errorMessage;

    private boolean enabled;

    private boolean available;

    public RuleViolation(FlowAnalysisRuleType ruleType, String subjectId, String scope, String ruleId, String errorMessage) {
        this.ruleType = ruleType;
        this.subjectId = subjectId;
        this.scope = scope;
        this.ruleId = ruleId;
        this.errorMessage = errorMessage;
        this.enabled = true;
        this.available = true;
    }

    public FlowAnalysisRuleType getRuleType() {
        return ruleType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getScope() {
        return scope;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RuleViolation.class.getSimpleName() + "[", "]")
            .add("ruleType=" + ruleType)
            .add("subjectId='" + subjectId + "'")
            .add("scope='" + scope + "'")
            .add("ruleId='" + ruleId + "'")
            .add("errorMessage='" + errorMessage + "'")
            .add("enabled=" + enabled)
            .add("available=" + available)
            .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        RuleViolation that = (RuleViolation) o;

        return new EqualsBuilder()
            .append(enabled, that.enabled)
            .append(available, that.available)
            .append(ruleType, that.ruleType)
            .append(subjectId, that.subjectId)
            .append(scope, that.scope)
            .append(ruleId, that.ruleId)
            .append(errorMessage, that.errorMessage)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
            .append(ruleType)
            .append(subjectId)
            .append(scope)
            .append(ruleId)
            .append(errorMessage)
            .append(enabled)
            .append(available)
            .toHashCode();
    }
}
