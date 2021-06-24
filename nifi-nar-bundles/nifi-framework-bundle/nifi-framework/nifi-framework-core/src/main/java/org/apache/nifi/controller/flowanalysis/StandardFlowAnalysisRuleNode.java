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
package org.apache.nifi.controller.flowanalysis;

import org.apache.nifi.annotation.behavior.Restricted;
import org.apache.nifi.annotation.documentation.DeprecationNotice;
import org.apache.nifi.authorization.Resource;
import org.apache.nifi.authorization.resource.Authorizable;
import org.apache.nifi.authorization.resource.ResourceFactory;
import org.apache.nifi.authorization.resource.ResourceType;
import org.apache.nifi.components.validation.ValidationTrigger;
import org.apache.nifi.controller.FlowController;
import org.apache.nifi.controller.LoggableComponent;
import org.apache.nifi.controller.ReloadComponent;
import org.apache.nifi.controller.FlowAnalysisRuleNode;
import org.apache.nifi.controller.ValidationContextFactory;
import org.apache.nifi.nar.ExtensionManager;
import org.apache.nifi.parameter.ParameterContext;
import org.apache.nifi.parameter.ParameterLookup;
import org.apache.nifi.registry.ComponentVariableRegistry;
import org.apache.nifi.flowanalysis.FlowAnalysisRuleContext;
import org.apache.nifi.flowanalysis.FlowAnalysisRule;
import org.apache.nifi.validation.FlowAnalysisContext;

public class StandardFlowAnalysisRuleNode extends AbstractFlowAnalysisRuleNode implements FlowAnalysisRuleNode {

    private final FlowController flowController;

    public StandardFlowAnalysisRuleNode(final LoggableComponent<FlowAnalysisRule> reportingTask, final String id, final FlowController controller,
                                        final ValidationContextFactory validationContextFactory, final FlowAnalysisContext flowAnalysisContext,
                                        final ComponentVariableRegistry variableRegistry, final ReloadComponent reloadComponent, final ExtensionManager extensionManager,
                                        final ValidationTrigger validationTrigger) {
        super(reportingTask, id, controller.getControllerServiceProvider(), validationContextFactory, flowAnalysisContext, variableRegistry, reloadComponent, extensionManager, validationTrigger);
        this.flowController = controller;
    }

    public StandardFlowAnalysisRuleNode(final LoggableComponent<FlowAnalysisRule> reportingTask, final String id, final FlowController controller,
                                        final ValidationContextFactory validationContextFactory, final FlowAnalysisContext flowAnalysisContext,
                                        final String componentType, final String canonicalClassName, final ComponentVariableRegistry variableRegistry,
                                        final ReloadComponent reloadComponent, final ExtensionManager extensionManager, final ValidationTrigger validationTrigger, final boolean isExtensionMissing) {
        super(reportingTask, id, controller.getControllerServiceProvider(), validationContextFactory, flowAnalysisContext, componentType, canonicalClassName,
            variableRegistry, reloadComponent, extensionManager, validationTrigger, isExtensionMissing);
        this.flowController = controller;
    }


    @Override
    public Authorizable getParentAuthorizable() {
        return flowController;
    }

    @Override
    public Resource getResource() {
        return ResourceFactory.getComponentResource(ResourceType.FlowAnalysisRule, getIdentifier(), getName());
    }

    @Override
    public boolean isRestricted() {
        return getFlowAnalysisRule().getClass().isAnnotationPresent(Restricted.class);
    }

    @Override
    public Class<?> getComponentClass() {
        return getFlowAnalysisRuleContext().getClass();
    }

    @Override
    public boolean isDeprecated() {
        return getFlowAnalysisRule().getClass().isAnnotationPresent(DeprecationNotice.class);
    }

    @Override
    public FlowAnalysisRuleContext getFlowAnalysisRuleContext() {
        return new StandardFlowAnalysisRuleContext(
            getFlowAnalysisRule(),
            getEffectivePropertyValues(),
            flowController,
            ParameterLookup.EMPTY,
            getVariableRegistry()
        );
    }

    @Override
    protected ParameterContext getParameterContext() {
        return null;
    }
}
