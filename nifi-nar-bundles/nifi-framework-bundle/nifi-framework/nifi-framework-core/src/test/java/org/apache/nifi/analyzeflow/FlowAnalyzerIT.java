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
package org.apache.nifi.analyzeflow;

import org.apache.nifi.components.ValidationResult;
import org.apache.nifi.components.validation.ValidationStatus;
import org.apache.nifi.controller.FlowAnalysisRuleNode;
import org.apache.nifi.controller.ProcessorNode;
import org.apache.nifi.controller.service.ControllerServiceNode;
import org.apache.nifi.flow.VersionedControllerService;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flowanalysis.AbstractFlowAnalysisRule;
import org.apache.nifi.flowanalysis.ComponentAnalysisResult;
import org.apache.nifi.flowanalysis.FlowAnalysisRuleContext;
import org.apache.nifi.flowanalysis.FlowAnalysisRuleType;
import org.apache.nifi.flowanalysis.GroupAnalysisResult;
import org.apache.nifi.groups.ProcessGroup;
import org.apache.nifi.integration.cs.CounterControllerService;
import org.apache.nifi.validation.RuleViolation;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class FlowAnalyzerIT extends AbstractFlowAnalysisIT {
    private MainFlowAnalyzer mainFlowAnalyzer;

    private AtomicInteger processGroupViolationCounter;

    @Before
    public void setUp() throws Exception {
        processGroupViolationCounter = new AtomicInteger(0);

        mainFlowAnalyzer = new MainFlowAnalyzer();

        mainFlowAnalyzer.setFlowAnalysisRuleProvider(getFlowController());
        mainFlowAnalyzer.setExtensionManager(getExtensionManager());
        mainFlowAnalyzer.setControllerServiceProvider(getFlowController().getControllerServiceProvider());
        mainFlowAnalyzer.setFlowAnalysisContext(flowAnalysisContext);
    }

    @Test
    public void testAnalyzeProcessorNoRule() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        ConcurrentMap expected = new ConcurrentHashMap<>();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorNoViolation() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });

        ConcurrentMap expected = new ConcurrentHashMap<>();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorDisableRuleBeforeAnalysis() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult("Unreported violation message");

                return Optional.of(componentAnalysisResult);
            }
        });
        flowAnalysisRuleNode.disable();

        ConcurrentMap expected = new ConcurrentHashMap<>();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorProduceViolation() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(flowAnalysisRuleNode.getIdentifier(), new RuleViolation(
                        flowAnalysisRuleNode.getRuleType(),
                        processorNode.getIdentifier(),
                        processorNode.getIdentifier(),
                        flowAnalysisRuleNode.getIdentifier(),
                        violationMessage
                    ));
                }});
            }});
        }};

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorThenAnalyzeAgainWithDifferentResult() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage1 = "Violation message1";
        String violationMessage2 = "Violation message2";

        AtomicReference<String> violationMessageHolder = new AtomicReference<>(violationMessage1);

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessageHolder.get());

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(flowAnalysisRuleNode.getIdentifier(), new RuleViolation(
                        flowAnalysisRuleNode.getRuleType(),
                        processorNode.getIdentifier(),
                        processorNode.getIdentifier(),
                        flowAnalysisRuleNode.getIdentifier(),
                        violationMessage2
                    ));
                }});
            }});
        }};

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        violationMessageHolder.set(violationMessage2);
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorProduceViolationFirstNoViolationSecond() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation message";

        AtomicReference<String> violationMessageHolder = new AtomicReference<>(violationMessage);

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                String violationMessage = violationMessageHolder.get();

                if (violationMessage == null) {
                    return Optional.empty();
                } else {
                    ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                    return Optional.of(componentAnalysisResult);
                }
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<>();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        violationMessageHolder.set(null);
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorDisableRuleAfterAnalysis() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation of this message disappears when rule is disabled";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expectedBeforeDisable = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(flowAnalysisRuleNode.getIdentifier(), new RuleViolation(
                        flowAnalysisRuleNode.getRuleType(),
                        processorNode.getIdentifier(),
                        processorNode.getIdentifier(),
                        flowAnalysisRuleNode.getIdentifier(),
                        violationMessage
                    ));
                }});
            }});
        }};

        ConcurrentMap expectedAfterDisable = new ConcurrentHashMap<>();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expectedBeforeDisable, actual);

        // WHEN
        flowAnalysisRuleNode.disable();
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        assertEquals(expectedAfterDisable, actual);
    }

    @Test
    public void testAnalyzeProcessorAndControllerServiceWithSameRuleProduceIndependentViolations() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        ControllerServiceNode controllerServiceNode = createControllerServiceNode(CounterControllerService.class.getName());

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(flowAnalysisRuleNode.getIdentifier(), new RuleViolation(
                        flowAnalysisRuleNode.getRuleType(),
                        processorNode.getIdentifier(),
                        processorNode.getIdentifier(),
                        flowAnalysisRuleNode.getIdentifier(),
                        violationMessage
                    ));
                }});
            }});
            put(controllerServiceNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(controllerServiceNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(flowAnalysisRuleNode.getIdentifier(), new RuleViolation(
                        flowAnalysisRuleNode.getRuleType(),
                        controllerServiceNode.getIdentifier(),
                        controllerServiceNode.getIdentifier(),
                        flowAnalysisRuleNode.getIdentifier(),
                        violationMessage
                    ));
                }});
            }});
        }};

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        mainFlowAnalyzer.analyzeControllerService(controllerServiceNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessGroupDisableRuleBeforeAnalysis() throws Exception {
        // GIVEN
        String violationMessage = "Violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(createGeneralGroupAnalysisResult(violationMessage));

                return results;
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<>();

        // WHEN;
        rule.disable();
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessGroupProduceViolation() throws Exception {
        // GIVEN
        String violationMessage = "Violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(createGeneralGroupAnalysisResult(violationMessage));

                return results;
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put("processGroupViolation_0", new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), new RuleViolation(
                        rule.getRuleType(),
                        "processGroupViolation_0",
                        processGroup.getIdentifier(),
                        rule.getIdentifier(),
                        violationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessGroupWithProcessor() throws Exception {
        // GIVEN
        String processGroupViolationMessage = "ProcessGroup violation message";
        String processorViolationMessage = "Processor violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        ProcessorNode processorNode = createProcessorNode(processGroup);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(createGeneralGroupAnalysisResult(processGroupViolationMessage));

                processGroup.getProcessors().stream()
                    .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                    .forEach(results::add);

                return results;
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put("processGroupViolation_0", new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), new RuleViolation(
                        rule.getRuleType(),
                        "processGroupViolation_0",
                        processGroup.getIdentifier(),
                        rule.getIdentifier(),
                        processGroupViolationMessage
                    ));
                }});
            }});
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), new RuleViolation(
                        rule.getRuleType(),
                        processorNode.getIdentifier(),
                        processGroup.getIdentifier(),
                        rule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessGroupAndProcessorWithDifferentRules() throws Exception {
        // GIVEN
        String processGroupViolationMessage = "ProcessGroup violation message";
        String processorViolationMessage = "Processor violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        ProcessorNode processorNode = createProcessorNode(processGroup);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode processGroupAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(createGeneralGroupAnalysisResult(processGroupViolationMessage));

                return results;
            }
        });

        FlowAnalysisRuleNode processorAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(processorViolationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put("processGroupViolation_0", new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processGroupAnalyzerRule.getIdentifier(), new RuleViolation(
                        processorAnalyzerRule.getRuleType(),
                        "processGroupViolation_0",
                        processGroup.getIdentifier(),
                        processGroupAnalyzerRule.getIdentifier(),
                        processGroupViolationMessage
                    ));
                }});
            }});
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processorAnalyzerRule.getIdentifier(), new RuleViolation(
                        processorAnalyzerRule.getRuleType(),
                        processorNode.getIdentifier(),
                        processorNode.getIdentifier(),
                        processorAnalyzerRule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorIndividuallyAndAsPartOfGroup() throws Exception {
        // GIVEN
        String processGroupViolationMessage = "ProcessGroup violation message";
        String processorViolationMessage = "Processor violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        ProcessorNode processorNode = createProcessorNode(processGroup);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode processGroupAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(createGeneralGroupAnalysisResult(processGroupViolationMessage));

                processGroup.getProcessors().stream()
                    .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                    .forEach(results::add);

                return results;
            }
        });

        FlowAnalysisRuleNode processorAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(processorViolationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put("processGroupViolation_0", new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processGroupAnalyzerRule.getIdentifier(), new RuleViolation(
                        processorAnalyzerRule.getRuleType(),
                        "processGroupViolation_0",
                        processGroup.getIdentifier(),
                        processGroupAnalyzerRule.getIdentifier(),
                        processGroupViolationMessage
                    ));
                }});
            }});
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processGroupAnalyzerRule.getIdentifier(), new RuleViolation(
                        processGroupAnalyzerRule.getRuleType(),
                        processorNode.getIdentifier(),
                        processGroup.getIdentifier(),
                        processGroupAnalyzerRule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processorAnalyzerRule.getIdentifier(), new RuleViolation(
                        processorAnalyzerRule.getRuleType(),
                        processorNode.getIdentifier(),
                        processorNode.getIdentifier(),
                        processorAnalyzerRule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorIndividuallyAndAsPartOfGroupButThenDisableGroupRule() throws Exception {
        // GIVEN
        String processGroupViolationMessage = "ProcessGroup violation message";
        String processorViolationMessage = "Processor violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        ProcessorNode processorNode = createProcessorNode(processGroup);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode processGroupAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(createGeneralGroupAnalysisResult(processGroupViolationMessage));

                processGroup.getProcessors().stream()
                    .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                    .forEach(results::add);

                return results;
            }
        });

        FlowAnalysisRuleNode processorAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(processorViolationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processorAnalyzerRule.getIdentifier(), new RuleViolation(
                        processorAnalyzerRule.getRuleType(),
                        processorNode.getIdentifier(),
                        processorNode.getIdentifier(),
                        processorAnalyzerRule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        processGroupAnalyzerRule.disable();

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessorIndividuallyAndAsPartOfGroupButThenDisableProcessorRule() throws Exception {
        // GIVEN
        String processGroupViolationMessage = "ProcessGroup violation message";
        String processorViolationMessage = "Processor violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        ProcessorNode processorNode = createProcessorNode(processGroup);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode processGroupAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(createGeneralGroupAnalysisResult(processGroupViolationMessage));

                processGroup.getProcessors().stream()
                    .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                    .forEach(results::add);

                return results;
            }
        });

        FlowAnalysisRuleNode processorAnalyzerRule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(processorViolationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put("processGroupViolation_0", new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processGroupAnalyzerRule.getIdentifier(), new RuleViolation(
                        processorAnalyzerRule.getRuleType(),
                        "processGroupViolation_0",
                        processGroup.getIdentifier(),
                        processGroupAnalyzerRule.getIdentifier(),
                        processGroupViolationMessage
                    ));
                }});
            }});
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(processGroupAnalyzerRule.getIdentifier(), new RuleViolation(
                        processGroupAnalyzerRule.getRuleType(),
                        processorNode.getIdentifier(),
                        processGroup.getIdentifier(),
                        processGroupAnalyzerRule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        processorAnalyzerRule.disable();

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessGroupWithChildProcessGroupBothContainingProcessors() throws Exception {
        // GIVEN
        String processorViolationMessage = "Processor violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());
        ProcessGroup childProcessGroup = createProcessGroup(processGroup);

        ProcessorNode processorNode = createProcessorNode(processGroup);
        ProcessorNode childProcessorNode = createProcessorNode(childProcessGroup);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                processGroup.getProcessors().stream()
                    .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                    .forEach(results::add);

                processGroup.getProcessGroups().stream()
                    .flatMap(childProcessGroup -> childProcessGroup.getProcessors().stream())
                    .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                    .forEach(results::add);

                return results;
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), new RuleViolation(
                        rule.getRuleType(),
                        processorNode.getIdentifier(),
                        processGroup.getIdentifier(),
                        rule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
            put(childProcessorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(childProcessGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), new RuleViolation(
                        rule.getRuleType(),
                        childProcessorNode.getIdentifier(),
                        childProcessGroup.getIdentifier(),
                        rule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testAnalyzeProcessGroupProduceViolationThenChildProcessGroupProduceNoViolation() throws Exception {
        // GIVEN
        String processorViolationMessage = "Processor violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());
        ProcessGroup childProcessGroup = createProcessGroup(processGroup);

        ProcessorNode processorNode = createProcessorNode(processGroup);
        ProcessorNode childProcessorNode = createProcessorNode(childProcessGroup);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);
        VersionedProcessGroup versionedChildProcessGroup = mapProcessGroup(childProcessGroup);

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                if (processGroup.getIdentifier().equals(versionedProcessGroup.getIdentifier())) {
                    processGroup.getProcessors().stream()
                        .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                        .forEach(results::add);

                    processGroup.getProcessGroups().stream()
                        .flatMap(childProcessGroup -> childProcessGroup.getProcessors().stream())
                        .map(processor -> new GroupAnalysisResult(processor, processorViolationMessage))
                        .forEach(results::add);
                }

                return results;
            }
        });

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), new RuleViolation(
                        rule.getRuleType(),
                        processorNode.getIdentifier(),
                        processGroup.getIdentifier(),
                        rule.getIdentifier(),
                        processorViolationMessage
                    ));
                }});
            }});
        }};

        // WHEN;
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);
        mainFlowAnalyzer.analyzeProcessGroup(versionedChildProcessGroup);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testRecommendationDoesNotInvalidateComponent() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });
        flowAnalysisRuleNode.setRuleType(FlowAnalysisRuleType.RECOMMENDATION);

        Collection<ValidationResult> expected = Collections.emptyList();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        // THEN
        Collection<ValidationResult> actual = processorNode.getValidationErrors();

        assertEquals(expected, actual);
    }

    @Test
    public void testPolicyInvalidatesProcessor() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });
        flowAnalysisRuleNode.setRuleType(FlowAnalysisRuleType.POLICY);

        Collection<ValidationResult> expected = Arrays.asList(
            new ValidationResult.Builder()
                .subject(processorNode.getComponent().getClass().getSimpleName())
                .valid(false)
                .explanation(violationMessage)
                .build()
        );

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        // THEN
        Collection<ValidationResult> actual = processorNode.getValidationErrors();

        assertEquals(expected, actual);
    }

    @Test
    public void testPolicyInvalidatesControllerService() throws Exception {
        // GIVEN
        ControllerServiceNode controllerServiceNode = createControllerServiceNode(CounterControllerService.class.getName());

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });
        flowAnalysisRuleNode.setRuleType(FlowAnalysisRuleType.POLICY);

        Collection<ValidationResult> expected = Arrays.asList(
            new ValidationResult.Builder()
                .subject(controllerServiceNode.getComponent().getClass().getSimpleName())
                .valid(false)
                .explanation(violationMessage)
                .build()
        );

        // WHEN
        mainFlowAnalyzer.analyzeControllerService(controllerServiceNode);
        controllerServiceNode.performValidation();

        // THEN
        Collection<ValidationResult> actual = controllerServiceNode.getValidationErrors();

        assertEquals(expected, actual);
    }

    @Test
    public void testChangingPolicyToRecommendationRemovesValidationError() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });
        flowAnalysisRuleNode.setRuleType(FlowAnalysisRuleType.POLICY);

        Collection<ValidationResult> expected = Arrays.asList();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        assertEquals(ValidationStatus.INVALID, processorNode.getValidationStatus());

        flowAnalysisRuleNode.setRuleType(FlowAnalysisRuleType.RECOMMENDATION);

        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        // THEN
        assertEquals(ValidationStatus.VALID, processorNode.getValidationStatus());
        Collection<ValidationResult> actual = processorNode.getValidationErrors();

        assertEquals(expected, actual);
    }

    @Test
    public void testDisablingRuleViolationRemovesValidationError() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });
        flowAnalysisRuleNode.setRuleType(FlowAnalysisRuleType.POLICY);

        Collection<ValidationResult> expected = Arrays.asList();

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        assertEquals(ValidationStatus.INVALID, processorNode.getValidationStatus());

        flowAnalysisContext.updateRuleViolation(
            processorNode.getIdentifier(),
            processorNode.getIdentifier(),
            flowAnalysisRuleNode.getIdentifier(),
            false
        );

        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        // THEN
        assertEquals(ValidationStatus.VALID, processorNode.getValidationStatus());
        Collection<ValidationResult> actual = processorNode.getValidationErrors();

        assertEquals(expected, actual);
    }

    @Test
    public void testReEnablingRuleViolationProducesValidationError() throws Exception {
        // GIVEN
        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        String violationMessage = "Violation message";

        FlowAnalysisRuleNode flowAnalysisRuleNode = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult componentAnalysisResult = new ComponentAnalysisResult(violationMessage);

                return Optional.of(componentAnalysisResult);
            }
        });
        flowAnalysisRuleNode.setRuleType(FlowAnalysisRuleType.POLICY);

        Collection<ValidationResult> expected = Arrays.asList(
            new ValidationResult.Builder()
                .subject(processorNode.getComponent().getClass().getSimpleName())
                .valid(false)
                .explanation(violationMessage)
                .build()
        );

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        assertEquals(ValidationStatus.INVALID, processorNode.getValidationStatus());

        flowAnalysisContext.updateRuleViolation(
            processorNode.getIdentifier(),
            processorNode.getIdentifier(),
            flowAnalysisRuleNode.getIdentifier(),
            false
        );

        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        assertEquals(ValidationStatus.VALID, processorNode.getValidationStatus());

        flowAnalysisContext.updateRuleViolation(
            processorNode.getIdentifier(),
            processorNode.getIdentifier(),
            flowAnalysisRuleNode.getIdentifier(),
            true
        );

        mainFlowAnalyzer.analyzeProcessor(processorNode);
        processorNode.performValidation();

        // THEN
        assertEquals(ValidationStatus.INVALID, processorNode.getValidationStatus());
        Collection<ValidationResult> actual = processorNode.getValidationErrors();

        assertEquals(expected, actual);
    }

    @Test
    public void testDisabledProcessorRuleViolationRemainsDisabledAfterNewAnalysis() throws Exception {
        // GIVEN
        String violationMessage = "Violation message";

        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Optional<ComponentAnalysisResult> analyzeComponent(
                String ruleName,
                FlowAnalysisRuleContext context,
                Object component,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                ComponentAnalysisResult result = new ComponentAnalysisResult(violationMessage);

                return Optional.of(result);
            }
        });
        rule.setRuleType(FlowAnalysisRuleType.POLICY);

        RuleViolation expectedRuleViolation = new RuleViolation(
            rule.getRuleType(),
            processorNode.getIdentifier(),
            processorNode.getIdentifier(),
            rule.getIdentifier(),
            violationMessage
        );
        expectedRuleViolation.setEnabled(false);

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processorNode.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), expectedRuleViolation);
                }});
            }});
        }};

        // WHEN
        mainFlowAnalyzer.analyzeProcessor(processorNode);

        flowAnalysisContext.updateRuleViolation(
            processorNode.getIdentifier(),
            processorNode.getIdentifier(),
            rule.getIdentifier(),
            false
        );

        mainFlowAnalyzer.analyzeProcessor(processorNode);

        processorNode.performValidation();

        // THEN
        assertEquals(ValidationStatus.VALID, processorNode.getValidationStatus());

        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testDisabledProcessorSpecificGroupScopedRuleViolationRemainsDisabledAfterNewAnalysis() throws Exception {
        // GIVEN
        String violationMessage = "Violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        ProcessorNode processorNode = createProcessorNode((context, session) -> {
        });
        processGroup.addProcessor(processorNode);

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                processGroup.getProcessors().stream()
                    .map(processor -> new GroupAnalysisResult(processor, violationMessage))
                    .forEach(results::add);

                return results;
            }
        });
        rule.setRuleType(FlowAnalysisRuleType.POLICY);

        RuleViolation expectedRuleViolation = new RuleViolation(
            rule.getRuleType(),
            processorNode.getIdentifier(),
            processGroup.getIdentifier(),
            rule.getIdentifier(),
            violationMessage
        );
        expectedRuleViolation.setEnabled(false);

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(processorNode.getIdentifier(), new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), expectedRuleViolation);
                }});
            }});
        }};

        // WHEN
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        flowAnalysisContext.updateRuleViolation(
            processorNode.getIdentifier(),
            processGroup.getIdentifier(),
            rule.getIdentifier(),
            false
        );

        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        processorNode.performValidation();

        // THEN
        assertEquals(ValidationStatus.VALID, processorNode.getValidationStatus());

        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    @Test
    public void testDisabledGenericGroupScopedRuleViolationRemainsDisabledAfterNewAnalysis() throws Exception {
        // GIVEN
        String genericViolationSubjectId = "generic-process_group-violation";
        String violationMessage = "Violation message";

        ProcessGroup processGroup = createProcessGroup(getRootGroup());

        VersionedProcessGroup versionedProcessGroup = mapProcessGroup(processGroup);

        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            public Collection<GroupAnalysisResult> analyzeProcessGroup(
                String ruleName,
                FlowAnalysisRuleContext context,
                VersionedProcessGroup processGroup,
                Function<String, VersionedControllerService> versionedControllerServiceProvider
            ) {
                Collection<GroupAnalysisResult> results = new ArrayList<>();

                results.add(new GroupAnalysisResult(genericViolationSubjectId, violationMessage));

                return results;
            }
        });

        RuleViolation expectedRuleViolation = new RuleViolation(
            rule.getRuleType(),
            genericViolationSubjectId,
            processGroup.getIdentifier(),
            rule.getIdentifier(),
            violationMessage
        );
        expectedRuleViolation.setEnabled(false);

        ConcurrentMap expected = new ConcurrentHashMap<String, ConcurrentMap<String, ConcurrentMap<String, RuleViolation>>>() {{
            put(genericViolationSubjectId, new ConcurrentHashMap<String, ConcurrentMap<String, RuleViolation>>() {{
                put(processGroup.getIdentifier(), new ConcurrentHashMap<String, RuleViolation>() {{
                    put(rule.getIdentifier(), expectedRuleViolation);
                }});
            }});
        }};

        // WHEN
        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        flowAnalysisContext.updateRuleViolation(
            genericViolationSubjectId,
            processGroup.getIdentifier(),
            rule.getIdentifier(),
            false
        );

        mainFlowAnalyzer.analyzeProcessGroup(versionedProcessGroup);

        // THEN
        ConcurrentMap actual = flowAnalysisContext.getRuleViolations();

        assertEquals(expected, actual);
    }

    private GroupAnalysisResult createGeneralGroupAnalysisResult(String processGroupViolationMessage) {
        return new GroupAnalysisResult(
            "processGroupViolation_" + processGroupViolationCounter.getAndIncrement(),
            processGroupViolationMessage
        );
    }
}
