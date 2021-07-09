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
package org.apache.nifi.flowanalysis;

import org.apache.nifi.flow.VersionedComponent;

import java.util.Optional;
import java.util.StringJoiner;

/**
 * Holds information about a {@link FlowAnalysisRule} violation after analyzing (a part of) the flow, represented by a process group.
 *  One such analysis can result in multiple instances of this class.
 */
public class GroupAnalysisResult {
    private final Optional<VersionedComponent> component;
    private final String message;

    /**
     * Creates a result object that corresponds to the entirety of the analyzed process group.
     * @param messages the rule violation message
     */
    public GroupAnalysisResult(String messages) {
        this(Optional.empty(), messages);
    }

    /**
     * Creates a result object that corresponds to a component within the analyzed process group.
     * @param component the component which this result corresponds to
     * @param messages the rule violation message
     */
    public GroupAnalysisResult(VersionedComponent component, String messages) {
        this(Optional.of(component), messages);
    }

    private GroupAnalysisResult(Optional<VersionedComponent> component, String messages) {
        this.component = component;
        this.message = messages;
    }

    /**
     * @return the rule violation message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return the component this result corresponds to or empty if this result corresponds to the entirety of the process group that was analyzed
     */
    public Optional<VersionedComponent> getComponent() {
        return component;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", GroupAnalysisResult.class.getSimpleName() + "[", "]")
            .add("component='" + component + "'")
            .add("message='" + message + "'")
            .toString();
    }
}
