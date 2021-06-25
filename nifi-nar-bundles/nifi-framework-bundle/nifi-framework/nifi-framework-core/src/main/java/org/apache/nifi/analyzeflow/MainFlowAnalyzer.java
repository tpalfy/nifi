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
import org.apache.nifi.flow.VersionedComponent;
import org.apache.nifi.flowanalysis.ComponentAnalysisResult;
import org.apache.nifi.flowanalysis.GroupAnalysisResult;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            versionedProcessor,
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
            versionedControllerService,
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

        deleteGroupViolations(
            processGroup,
            _groupId -> flowAnalysisContext.getRuleViolations()
                .values()
                .forEach(scopeToRuleToRuleViolations -> scopeToRuleToRuleViolations.remove(_groupId))
        );

        flowAnalysisContext.getRuleViolations()
            .values()
            .forEach(scopeToRuleToRuleViolations -> scopeToRuleToRuleViolations.remove(groupId));

        flowAnalysisRules.stream()
            .filter(FlowAnalysisRuleNode::isEnabled)
            .forEach(flowAnalysisRuleNode -> {
                String ruleName = flowAnalysisRuleNode.getName();

                try {
                    Collection<GroupAnalysisResult> analysisResults = flowAnalysisRuleNode.getFlowAnalysisRule().analyzeProcessGroup(
                        flowAnalysisRuleNode.getName(),
                        flowAnalysisRuleNode.getFlowAnalysisRuleContext(),
                        processGroup,
                        controllerServiceDetailsProvider
                    );

                    analysisResults.forEach(analysisResult -> {
                        analysisResult.getComponent().ifPresent(component -> {
                            flowAnalysisContext.addRuleViolation(
                                new RuleViolation(
                                    flowAnalysisRuleNode.getRuleType(),
                                    component.getIdentifier(),
                                    component.getGroupIdentifier(),
                                    ruleName, analysisResult.getMessage())
                            );
                        });

                        if (!analysisResult.getComponent().isPresent()) {
                            String uuid = "group_" + UUID.randomUUID().toString();
                            flowAnalysisContext.addRuleViolation(
                                new RuleViolation(
                                    flowAnalysisRuleNode.getRuleType(),
                                    uuid,
                                    groupId,
                                    ruleName, analysisResult.getMessage())
                            );
                        }
                    });
                } catch (Exception e) {
                    logger.error("FlowAnalysis error while running '{}' against group '{}'", ruleName, groupId, e);
                }
            });

        Instant end = Instant.now();

        long durationMs = Duration.between(start, end).toMillis();

        logger.debug("Flow Analysis took {} ms", durationMs);
    }

    private void analyzeComponent(VersionedComponent component, Function<FlowAnalysisRuleNode, Optional<ComponentAnalysisResult>> ruleRunner) {
        Instant start = Instant.now();

        String componentId = component.getIdentifier();
        Set<FlowAnalysisRuleNode> flowAnalysisRules = flowAnalysisRuleProvider.getAllFlowAnalysisRules();

        flowAnalysisRules.stream()
            .filter(FlowAnalysisRuleNode::isEnabled)
            .forEach(flowAnalysisRuleNode -> {
                String ruleName = flowAnalysisRuleNode.getName();

                try {
                    Optional<ComponentAnalysisResult> analysisResultOptional = ruleRunner.apply(flowAnalysisRuleNode);

                    analysisResultOptional.ifPresent(analysisResult -> {
                        flowAnalysisContext.addRuleViolation(
                            new RuleViolation(
                                flowAnalysisRuleNode.getRuleType(),
                                componentId,
                                componentId,
                                ruleName, analysisResult.getMessage())
                        );
                    });

                    if (!analysisResultOptional.isPresent()) {
                        flowAnalysisContext.deleteRuleViolation(componentId, componentId, ruleName);
                    }
                } catch (Exception e) {
                    logger.error("FlowAnalysis error while running '{}' against '{}'", ruleName, component, e);
                }
            });

        Optional.ofNullable(flowAnalysisContext.getRuleViolations().get(componentId))
            .map(scopeToRuleNameToRuleViolation -> scopeToRuleNameToRuleViolation
                .values()
                .stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()))
            .map(Collection::size)
            .filter(size -> size == 0)
            .ifPresent(__ -> flowAnalysisContext.getRuleViolations().remove(componentId));

        Instant end = Instant.now();

        long durationMs = Duration.between(start, end).toMillis();

        logger.debug("Flow Analysis took {} ms", durationMs);
    }

    private void deleteGroupViolations(VersionedProcessGroup processGroup, Consumer<String> groupViolationRemover) {
        groupViolationRemover.accept(processGroup.getIdentifier());
        processGroup.getProcessGroups().forEach(childProcessGroup -> deleteGroupViolations(childProcessGroup, groupViolationRemover));
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
