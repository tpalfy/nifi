package org.apache.nifi.integration.flowanalysis;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flow.VersionedControllerService;
import org.apache.nifi.flow.VersionedProcessGroup;
import org.apache.nifi.flowanalysis.AbstractFlowAnalysisRule;
import org.apache.nifi.flowanalysis.ComponentAnalysisResult;
import org.apache.nifi.flowanalysis.FlowAnalysisRule;
import org.apache.nifi.flowanalysis.FlowAnalysisRuleContext;
import org.apache.nifi.flowanalysis.GroupAnalysisResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class DelegateFlowAnalysisRule extends AbstractFlowAnalysisRule {
    private FlowAnalysisRule delegate;

    public DelegateFlowAnalysisRule() {
    }

    @Override
    public Optional<ComponentAnalysisResult> analyzeComponent(String ruleName, FlowAnalysisRuleContext context, Object component, Function<String, VersionedControllerService> controllerServiceDetailsProvider) {
        return delegate.analyzeComponent(ruleName, context, component, controllerServiceDetailsProvider);
    }

    @Override
    public Collection<GroupAnalysisResult> analyzeProcessGroup(String ruleName, FlowAnalysisRuleContext context, VersionedProcessGroup processGroup, Function<String, VersionedControllerService> controllerServiceDetailsProvider) {
        return delegate.analyzeProcessGroup(ruleName, context, processGroup, controllerServiceDetailsProvider);
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return delegate.getPropertyDescriptors();
    }

    public FlowAnalysisRule getDelegate() {
        return delegate;
    }

    public void setDelegate(FlowAnalysisRule delegate) {
        this.delegate = delegate;
    }
}
