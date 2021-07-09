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

import java.util.concurrent.ConcurrentMap;

/**
 * Manages {@link RuleViolation}s produced during flow analysis and stores them in a multidimensional map
 */
public interface FlowAnalysisContext {
    /**
     * Add a new violation
     * @param ruleViolation the violation to be added
     */
    void addRuleViolation(RuleViolation ruleViolation);

    /**
     * Update an existing violation
     * @param subjectId see {@link RuleViolation#getSubjectId()}
     * @param scope see {@link RuleViolation#getScope()}
     * @param ruleId see {@link RuleViolation#getRuleId()}
     * @param enabled see {@link RuleViolation#isEnabled()}
     */
    void updateRuleViolation(String subjectId, String scope, String ruleId, boolean enabled);

    /**
     * Delete an existing violation
     * @param subjectId see {@link RuleViolation#getSubjectId()}
     * @param scope see {@link RuleViolation#getScope()}
     * @param ruleId see {@link RuleViolation#getRuleId()}
     */
    void deleteRuleViolation(String subjectId, String scope, String ruleId);

    /**
     * Provides the stored rule violations stored in a multidimensional map.
     *  The keys in the map are:
     *         <ol>
     *            <li>id of the subject the violation corresponds to</li>
     *            <li>the scope of the analysis</li>
     *            <li>id of the rule</li>
     *         </ol>
     * @return the map that stores the violations
     */
    ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>> getRuleViolations();

    /**
     * Removes empty entries from the map storing the rule violations
     */
    void cleanUp();
}
