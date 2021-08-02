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
package org.apache.nifi.analyzeflow.ruleimpl;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.flowanalysis.AbstractFlowAnalysisRule;
import org.apache.nifi.flowanalysis.ComponentAnalysisResult;
import org.apache.nifi.flowanalysis.FlowAnalysisRuleContext;
import org.apache.nifi.processor.util.StandardValidators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Tags({"component", "processor", "type"})
@CapabilityDescription("Produces rule violations for each processor of a given type.")
public class DisallowProcessorType extends AbstractFlowAnalysisRule {
    public static final PropertyDescriptor PROCESSOR_TYPE = new PropertyDescriptor.Builder()
        .name("processor-type")
        .displayName("Processor Type")
        .description("Processors of the given type will produce a rule violation (i.e. they shouldn't exist).")
        .required(true)
        .addValidator(StandardValidators.NON_BLANK_VALIDATOR)
        .defaultValue(null)
        .build();

    private final static List<PropertyDescriptor> propertyDescriptors;

    static {
        List<PropertyDescriptor> _propertyDescriptors = new ArrayList<>();
        _propertyDescriptors.add(PROCESSOR_TYPE);
        propertyDescriptors = Collections.unmodifiableList(_propertyDescriptors);
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return propertyDescriptors;
    }

    @Override
    public Optional<ComponentAnalysisResult> analyzeComponent(Object component, FlowAnalysisRuleContext context) {
        String processorType = context.getProperty(PROCESSOR_TYPE).getValue();

        if (component instanceof VersionedProcessor) {
            VersionedProcessor processor = (VersionedProcessor) component;

            String encounteredProcessorType = processor.getType();
            encounteredProcessorType = encounteredProcessorType.substring(encounteredProcessorType.lastIndexOf(".") + 1);

            if (encounteredProcessorType.equals(processorType)) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(
                    "'" + processorType + "' is not allowed!"
                );

                return Optional.of(componentAnalysisResult);
            }
        }

        return Optional.empty();
    }
}
