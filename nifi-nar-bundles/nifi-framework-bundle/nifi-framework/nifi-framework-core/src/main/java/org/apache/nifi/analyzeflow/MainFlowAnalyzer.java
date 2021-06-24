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

import org.apache.nifi.controller.ProcessorNode;
import org.apache.nifi.controller.flowanalysis.FlowAnalysisRuleProvider;
import org.apache.nifi.controller.flowanalysis.FlowAnalyzer;
import org.apache.nifi.controller.FlowAnalysisRuleNode;
import org.apache.nifi.controller.service.ControllerServiceNode;
import org.apache.nifi.controller.service.ControllerServiceProvider;
import org.apache.nifi.flowanalysis.ComponentAnalysisResult;
import org.apache.nifi.nar.ExtensionManager;
import org.apache.nifi.flow.VersionedControllerService;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flow.VersionedProcessor;
import org.apache.nifi.registry.flow.mapping.NiFiRegistryFlowMapper;
import org.apache.nifi.validation.FlowAnalysisContext;
import org.apache.nifi.validation.RuleViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class MainFlowAnalyzer implements FlowAnalyzer {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private FlowAnalysisContext flowAnalysisContext;

    private FlowAnalysisRuleProvider flowAnalysisRuleProvider;
    private ExtensionManager extensionManager;
    private ControllerServiceProvider controllerServiceProvider;

    private Function<String, VersionedControllerService> controllerServiceDetailsProvider = id -> {
        final NiFiRegistryFlowMapper mapper = createMapper();

        ControllerServiceNode controllerServiceNode = controllerServiceProvider.getControllerServiceNode(id);

        VersionedControllerService versionedControllerService = mapper.mapControllerService(
            controllerServiceNode,
            controllerServiceProvider,
            Collections.emptySet(),
            new HashMap<>()
        );

        return versionedControllerService;
    };

    @Override
    public void analyzeProcessor(ProcessorNode processorNode) {
        logger.debug("Running analysis on {}", processorNode);

        final NiFiRegistryFlowMapper mapper = createMapper();

        VersionedProcessor versionedProcessor = mapper.mapProcessor(
            processorNode,
            controllerServiceProvider,
            Collections.emptySet(),
            new HashMap<>()
        );

        analyzeComponent(
            versionedProcessor.getIdentifier(),
            flowAnalysisRuleNode -> flowAnalysisRuleNode.getFlowAnalysisRule().analyzeComponent(
                flowAnalysisRuleNode.getName(),
                flowAnalysisRuleNode.getFlowAnalysisRuleContext(),
                versionedProcessor,
                controllerServiceDetailsProvider
            )
        );
    }

    @Override
    public void analyzeControllerService(ControllerServiceNode controllerServiceNode) {
        logger.debug("Running analysis on {}", controllerServiceNode);

        final NiFiRegistryFlowMapper mapper = createMapper();

        VersionedControllerService versionedControllerService = mapper.mapControllerService(
            controllerServiceNode,
            controllerServiceProvider,
            Collections.emptySet(),
            new HashMap<>()
        );

        analyzeComponent(
            versionedControllerService.getIdentifier(),
            flowAnalysisRuleNode -> flowAnalysisRuleNode.getFlowAnalysisRule().analyzeComponent(
                flowAnalysisRuleNode.getName(),
                flowAnalysisRuleNode.getFlowAnalysisRuleContext(),
                versionedControllerService,
                controllerServiceDetailsProvider
            )
        );
    }

    @Override
    public void analyzeProcessGroup(VersionedProcessGroup processGroup) {
        logger.debug("Running analysis on process group {}.", processGroup.getIdentifier());

        String groupId = processGroup.getIdentifier();
        Instant start = Instant.now();

        Set<FlowAnalysisRuleNode> flowAnalysisRules = flowAnalysisRuleProvider.getAllFlowAnalysisRules();

        flowAnalysisRules.stream()
            .filter(FlowAnalysisRuleNode::isEnabled)
            .forEach(flowAnalysisRuleNode -> {
                String ruleName = flowAnalysisRuleNode.getName();

                try {
//                    Collection<ComponentAnalysisResult> analysisResults = flowAnalysisRuleNode.getFlowAnalysisRule().analyzeProcessGroup(
//                        flowAnalysisRuleNode.getName(),
//                        flowAnalysisRuleNode.getFlowAnalysisRuleContext(),
//                        processGroup,
//                        controllerServiceDetailsProvider
//                    );
//
//                    analysisResults.forEach(analysisResult -> {
//                        flowAnalysisContext.addRuleViolation(
//                            groupId,
//                            new RuleViolation(
//                                flowAnalysisRuleNode.getRuleType(),
//                                groupId,
//                                ruleName,
//                                analysisResult.getMessage())
//                        );
//
//                        result.addResult(flowAnalysisRuleNode.getRuleType(), analysisResult);
//                    });
                } catch (Exception e) {
                    logger.error("FlowAnalysis error while running '{}' against group '{}'", ruleName, groupId, e);
                }
            });

        flowAnalysisContext.getIdToRuleNameToRuleViolations().entrySet()
            .removeIf((idAndRuleNameToRuleViolations -> idAndRuleNameToRuleViolations.getValue().isEmpty()));

        Instant end = Instant.now();

        long durationMs = Duration.between(start, end).toMillis();

        logger.debug("Flow Analysis took {} ms", durationMs);
    }

    private void analyzeComponent(String componentId, Function<FlowAnalysisRuleNode, Optional<ComponentAnalysisResult>> ruleRunner) {
        Instant start = Instant.now();

        Set<FlowAnalysisRuleNode> flowAnalysisRules = flowAnalysisRuleProvider.getAllFlowAnalysisRules();

        flowAnalysisRules.stream()
            .filter(FlowAnalysisRuleNode::isEnabled)
            .forEach(flowAnalysisRuleNode -> {
                String ruleName = flowAnalysisRuleNode.getName();

                try {
                    Optional<ComponentAnalysisResult> analysisResultOptional = ruleRunner.apply(flowAnalysisRuleNode);

                    analysisResultOptional.ifPresent(analysisResult -> {
                        flowAnalysisContext.addRuleViolation(
                            componentId,
                            new RuleViolation(
                                flowAnalysisRuleNode.getRuleType(),
                                componentId,
                                ruleName,
                                analysisResult.getMessage())
                        );
                    });

                    if (!analysisResultOptional.isPresent()) {
                        flowAnalysisContext.removeRuleViolation(componentId, ruleName);
                    }
                } catch (Exception e) {
                    logger.error("FlowAnalysis error while running '{}' against '{}'", ruleName, componentId, e);
                }
            });

        flowAnalysisContext.getIdToRuleNameToRuleViolations().entrySet()
            .removeIf((idAndRuleNameToRuleViolations -> idAndRuleNameToRuleViolations.getValue().isEmpty()));

        Instant end = Instant.now();

        long durationMs = Duration.between(start, end).toMillis();

        logger.debug("Flow Analysis took {} ms", durationMs);
    }

    public NiFiRegistryFlowMapper createMapper() {
        NiFiRegistryFlowMapper mapper = new NiFiRegistryFlowMapper(extensionManager, Function.identity());

        return mapper;
    }

    @Override
    public void setFlowAnalysisRuleProvider(FlowAnalysisRuleProvider flowAnalysisRuleProvider) {
        this.flowAnalysisRuleProvider = flowAnalysisRuleProvider;
    }

    @Override
    public void setExtensionManager(ExtensionManager extensionManager) {
        this.extensionManager = extensionManager;
    }

    @Override
    public void setControllerServiceProvider(ControllerServiceProvider controllerServiceProvider) {
        this.controllerServiceProvider = controllerServiceProvider;
    }

    public void setFlowAnalysisContext(FlowAnalysisContext flowAnalysisContext) {
        this.flowAnalysisContext = flowAnalysisContext;
    }
}
