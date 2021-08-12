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

import org.apache.nifi.flow.VersionedComponent;
import org.apache.nifi.flow.VersionedProcessGroup;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class FlowAnalysisContextImpl implements FlowAnalysisContext {
    private final ConcurrentMap<String, ConcurrentMap<RuleViolationKey, RuleViolation>> subjectIdToRuleViolation = new ConcurrentHashMap<>();

    @Override
    public void upsertComponentViolations(String componentId, Collection<RuleViolation> violations) {
        ConcurrentMap<RuleViolationKey, RuleViolation> componentRuleViolations = subjectIdToRuleViolation
            .computeIfAbsent(componentId, __ -> new ConcurrentHashMap<>());

        synchronized (componentRuleViolations) {
            componentRuleViolations.values().stream()
                .filter(violation -> violation.getScope().equals(componentId))
                .forEach(violation -> violation.setAvailable(false));

            violations.forEach(violation -> componentRuleViolations
                .compute(new RuleViolationKey(violation), (ruleViolationKey, currentViolation) -> {
                    if (currentViolation != null) {
                        violation.setEnabled(currentViolation.isEnabled());
                    }

                    return violation;
                })
            );

            componentRuleViolations.entrySet().removeIf(keyAndViolation -> {
                RuleViolation violation = keyAndViolation.getValue();

                return violation.getScope().equals(componentId)
                    && !violation.isAvailable();
            });
        }
    }

    @Override
    public synchronized void upsertGroupViolations(
        VersionedProcessGroup processGroup,
        Collection<RuleViolation> groupViolations,
        Map<VersionedComponent, Collection<RuleViolation>> componentToRuleViolations
    ) {
        hideGroupViolations(processGroup);

        ConcurrentMap<String, Collection<RuleViolation>> newViolationsByGroupId = new ConcurrentHashMap<>();
        groupViolations.forEach(violation -> newViolationsByGroupId
            .computeIfAbsent(violation.getSubjectId(), __ -> new HashSet<>())
            .add(violation)
        );

        newViolationsByGroupId.entrySet().forEach(groupIdAndViolations -> {
            String groupIdWithNewViolation = groupIdAndViolations.getKey();
            Collection<RuleViolation> newGroupViolations = groupIdAndViolations.getValue();

            newGroupViolations.forEach(newViolation -> subjectIdToRuleViolation
                .computeIfAbsent(groupIdWithNewViolation, __ -> new ConcurrentHashMap<>())
                .compute(new RuleViolationKey(newViolation), (ruleViolationKey, currentViolation) -> {
                    if (currentViolation != null) {
                        newViolation.setEnabled(currentViolation.isEnabled());
                    }

                    return newViolation;
                }));
        });

        componentToRuleViolations.forEach((component, componentViolations) -> {
            ConcurrentMap<RuleViolationKey, RuleViolation> componentRuleViolations = subjectIdToRuleViolation
                .computeIfAbsent(component.getIdentifier(), __ -> new ConcurrentHashMap<>());

            componentViolations.forEach(componentViolation -> componentRuleViolations
                .compute(new RuleViolationKey(componentViolation), (ruleViolationKey, currentViolation) -> {
                    if (currentViolation != null) {
                        componentViolation.setEnabled(currentViolation.isEnabled());
                    }

                    return componentViolation;
                })
            );
        });

        purgeGroupViolations(processGroup);
    }

    private void hideGroupViolations(VersionedProcessGroup processGroup) {
        String groupId = processGroup.getIdentifier();

        subjectIdToRuleViolation.values().stream()
            .map(Map::values).flatMap(Collection::stream)
            .filter(violation -> violation.getScope().equals(groupId))
            .forEach(violation -> violation.setAvailable(false));

        processGroup.getProcessGroups().forEach(childProcessGroup -> hideGroupViolations(childProcessGroup));
    }

    private void purgeGroupViolations(VersionedProcessGroup processGroup) {
        String groupId = processGroup.getIdentifier();

        subjectIdToRuleViolation.values().forEach(violationMap ->
            violationMap.entrySet().removeIf(keyAndViolation -> {
                RuleViolation violation = keyAndViolation.getValue();

                return violation.getScope().equals(groupId)
                    && !violation.isAvailable();
            }));

        processGroup.getProcessGroups().forEach(childProcessGroup -> purgeGroupViolations(childProcessGroup));
    }

    @Override
    public void updateRuleViolation(String scope, String subjectId, String ruleId, String issueId, boolean enabled) {
        Optional.ofNullable(subjectIdToRuleViolation.get(subjectId))
            .map(violationMap -> violationMap.get(new RuleViolationKey(scope, subjectId, ruleId, issueId)))
            .ifPresent(violation -> violation.setEnabled(enabled));
    }

    @Override
    public Collection<RuleViolation> getRuleViolationsForSubject(String subjectId) {
        HashSet<RuleViolation> ruleViolationsForSubject = Optional.ofNullable(subjectIdToRuleViolation.get(subjectId))
            .map(Map::values)
            .map(HashSet::new)
            .orElse(new HashSet<>());

        return ruleViolationsForSubject;
    }

    @Override
    public Collection<RuleViolation> getRuleViolationsForGroup(String groupId) {
        Set<RuleViolation> groupViolations = subjectIdToRuleViolation.values().stream()
            .map(Map::values).flatMap(Collection::stream)
            .filter(violation -> violation.getGroupId().equals(groupId))
            .collect(Collectors.toSet());

        return groupViolations;
    }

    @Override
    public Collection<RuleViolation> getAllRuleViolations() {
        Set<RuleViolation> allRuleViolations = subjectIdToRuleViolation.values().stream()
            .map(Map::values).flatMap(Collection::stream)
            .collect(Collectors.toSet());

        return allRuleViolations;
    }

    @Override
    public void removeRuleViolationsForSubject(String subjectId) {
        subjectIdToRuleViolation.remove(subjectId);
    }

    @Override
    public void removeRuleViolationsForRule(String ruleId) {
        subjectIdToRuleViolation.values().stream()
            .forEach(
                violationMap -> violationMap
                    .entrySet()
                    .removeIf(keyAndViolation -> keyAndViolation.getValue().getRuleId().equals(ruleId))
            );
    }

    @Override
    public void cleanUp() {
        subjectIdToRuleViolation.entrySet().removeIf(subjectIdAndViolationMap -> subjectIdAndViolationMap.getValue().isEmpty());
    }
}
