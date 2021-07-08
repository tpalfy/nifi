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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

public class FlowAnalysisContextImpl implements FlowAnalysisContext {
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>> idToScopeToRuleIdToRuleViolation = new ConcurrentHashMap<>();

    @Override
    public void addRuleViolation(RuleViolation ruleViolation) {
        idToScopeToRuleIdToRuleViolation
            .computeIfAbsent(ruleViolation.getSubjectId(), __ -> new ConcurrentHashMap<>())
            .computeIfAbsent(ruleViolation.getScope(), __ -> new ConcurrentHashMap<>())
            .compute(ruleViolation.getRuleId(), (ruleId, currentRuleViolation) -> {
                RuleViolation newRuleViolation = new RuleViolation(
                    ruleViolation.getRuleType(),
                    ruleViolation.getSubjectId(),
                    ruleViolation.getScope(),
                    ruleId,
                    ruleViolation.getErrorMessage());

                if (currentRuleViolation != null) {
                    newRuleViolation.setEnabled(currentRuleViolation.isEnabled());
                }

                return newRuleViolation;
            });
    }

    @Override
    public void updateRuleViolation(String subjectId, String scope, String ruleId, boolean enabled) {
        Optional.ofNullable(idToScopeToRuleIdToRuleViolation.get(subjectId))
            .map(scopeToRuleIdToRuleViolation -> scopeToRuleIdToRuleViolation.get(scope))
            .map(ruleIdToRuleViolation -> ruleIdToRuleViolation.get(ruleId))
            .ifPresent(ruleViolation -> ruleViolation.setEnabled(enabled));
    }

    @Override
    public void deleteRuleViolation(String subjectId, String scope, String ruleId) {
        Optional.ofNullable(idToScopeToRuleIdToRuleViolation.get(subjectId))
            .map(scopeToRuleIdToRuleViolation -> scopeToRuleIdToRuleViolation.get(scope))
            .ifPresent(scopeToRuleIdToRuleViolation -> scopeToRuleIdToRuleViolation.remove(ruleId));
    }

    @Override
    public ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>> getRuleViolations() {
        return idToScopeToRuleIdToRuleViolation;
    }

    @Override
    public void cleanUp() {
        idToScopeToRuleIdToRuleViolation.values().forEach(scopeToRuleIdToRuleViolation -> {
            scopeToRuleIdToRuleViolation.entrySet().removeIf(scopeAndRuleIdToRuleViolation -> scopeAndRuleIdToRuleViolation.getValue().isEmpty());
        });
        idToScopeToRuleIdToRuleViolation.entrySet().removeIf(idAndScopeToRuleIdToRuleViolation -> idAndScopeToRuleIdToRuleViolation.getValue().isEmpty());
    }
}
