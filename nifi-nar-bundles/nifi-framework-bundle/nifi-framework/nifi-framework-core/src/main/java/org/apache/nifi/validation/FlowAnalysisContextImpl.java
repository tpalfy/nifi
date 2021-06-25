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

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FlowAnalysisContextImpl implements FlowAnalysisContext {
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>> idToScopeToRuleNameToRuleViolation = new ConcurrentHashMap<>();

    @Override
    public void addComponentRuleViolation(RuleViolation ruleViolation) {
        idToScopeToRuleNameToRuleViolation
            .computeIfAbsent(ruleViolation.getSubjectId(), __ -> new ConcurrentHashMap<>())
            .computeIfAbsent(ruleViolation.getScope(), __ -> new ConcurrentHashMap<>())
            .compute(ruleViolation.getRuleName(), (ruleName, currentRuleViolation) -> {
                RuleViolation newRuleViolation = new RuleViolation(
                    ruleViolation.getRuleType(),
                    ruleViolation.getSubjectId(),
                    ruleViolation.getScope(),
                    ruleName,
                    ruleViolation.getErrorMessage());

                if (currentRuleViolation != null) {
                    newRuleViolation.setEnabled(currentRuleViolation.isEnabled());
                }

                return newRuleViolation;
            });
    }

    @Override
    public void updateComponentRuleViolation(String componentId, String scope, String ruleName, boolean enabled) {
        Optional.ofNullable(idToScopeToRuleNameToRuleViolation.get(componentId))
            .map(scopeToRuleNameToRuleViolation -> scopeToRuleNameToRuleViolation.get(scope))
            .map(ruleNameToRuleViolation -> ruleNameToRuleViolation.get(ruleName))
            .ifPresent(ruleViolation -> ruleViolation.setEnabled(enabled));
    }

    @Override
    public void deleteComponentRuleViolation(String componentId, String scope, String ruleName) {
        Optional.ofNullable(idToScopeToRuleNameToRuleViolation.get(componentId))
            .map(scopeToRuleNameToRuleViolation -> scopeToRuleNameToRuleViolation.get(scope))
            .ifPresent(scopeToRuleNameToRuleViolation -> scopeToRuleNameToRuleViolation.remove(ruleName));
    }

    @Override
    public ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>> getRuleViolations() {
        return idToScopeToRuleNameToRuleViolation;
    }
}
