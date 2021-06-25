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

public class GroupAnalysisResult {
    private final Optional<VersionedComponent> component;
    private final String message;

    public GroupAnalysisResult(String messages) {
        this(Optional.empty(), messages);
    }

    public GroupAnalysisResult(VersionedComponent component, String messages) {
        this(Optional.of(component), messages);
    }

    private GroupAnalysisResult(Optional<VersionedComponent> component, String messages) {
        this.component = component;
        this.message = messages;
    }

    public String getMessage() {
        return message;
    }

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
