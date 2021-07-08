package org.apache.nifi.analyzeflow;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.FlowAnalysisRuleNode;
import org.apache.nifi.controller.scheduling.TestStandardProcessScheduler;
import org.apache.nifi.controller.service.ControllerServiceNode;
import org.apache.nifi.flowanalysis.AbstractFlowAnalysisRule;
import org.apache.nifi.nar.SystemBundle;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FlowAnalysisRuleLifeCycleIT extends AbstractFlowAnalysisIT {
    @Test
    public void testCreateRules() throws Exception {
        // GIVEN
        FlowAnalysisRuleNode rule1 = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });
        FlowAnalysisRuleNode rule2 = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });

        Set<FlowAnalysisRuleNode> expected = new HashSet<FlowAnalysisRuleNode>() {{
            add(rule1);
            add(rule2);
        }};

        // WHEN
        Set<FlowAnalysisRuleNode> actual = getFlowController().getFlowManager().getAllFlowAnalysisRules();

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    public void testCannotDeleteEnabledRule() throws Exception {
        // GIVEN
        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });

        // WHEN
        try {
            getFlowController().getFlowManager().removeFlowAnalysisRule(rule);
            fail();
        } catch (IllegalStateException e) {
            // THEN
            assertEquals(
                "Cannot delete " + rule.getIdentifier() + " because it is enabled",
                e.getMessage()
            );
        }
    }

    @Test
    public void testDeleteRule() throws Exception {
        // GIVEN
        FlowAnalysisRuleNode rule1 = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });
        FlowAnalysisRuleNode rule2 = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });

        Set<FlowAnalysisRuleNode> expected = new HashSet<FlowAnalysisRuleNode>() {{
            add(rule2);
        }};

        // WHEN
        rule1.disable();
        getFlowController().getFlowManager().removeFlowAnalysisRule(rule1);
        Set<FlowAnalysisRuleNode> actual = getFlowController().getFlowManager().getAllFlowAnalysisRules();

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    public void testCannotEnableEnabledRule() throws Exception {
        // GIVEN
        FlowAnalysisRuleNode rule = createAndEnableFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });

        // WHEN
        try {
            rule.enable();
            fail();
        } catch (IllegalStateException e) {
            // THEN
            assertEquals(
                "Cannot enable " + rule.getIdentifier() + " because it is not disabled",
                e.getMessage()
            );
        }
    }

    @Test
    public void testCannotDisableDisabledRule() throws Exception {
        // GIVEN
        FlowAnalysisRuleNode rule = createFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
        });

        // WHEN
        try {
            rule.disable();
            fail();
        } catch (IllegalStateException e) {
            // THEN
            assertEquals(
                "Cannot disable " + rule.getIdentifier() + " because it is already disabled",
                e.getMessage()
            );
        }
    }

    @Test
    public void testEnableAndDisableServiceEnablesAndDisablesReferencingRule() throws Exception {
        // GIVEN
        PropertyDescriptor controllerServiceReferencingPropertyDescriptor = new PropertyDescriptor.Builder()
            .name("controllerService")
            .identifiesControllerService(TestStandardProcessScheduler.SimpleTestService.class)
            .required(true)
            .build();

        FlowAnalysisRuleNode rule = createFlowAnalysisRuleNode(new AbstractFlowAnalysisRule() {
            @Override
            protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
                List<PropertyDescriptor> propertyDescriptors = new ArrayList<>();
                propertyDescriptors.add(controllerServiceReferencingPropertyDescriptor);

                return propertyDescriptors;
            }
        });

        final ControllerServiceNode service = getFlowController().getFlowManager().createControllerService(
            TestStandardProcessScheduler.SimpleTestService.class.getName(),
            UUID.randomUUID().toString(),
            SystemBundle.SYSTEM_BUNDLE_COORDINATE,
            Collections.emptySet(),
            true,
            true
        );
        getFlowController().getFlowManager().addRootControllerService(service);
        service.resetValidationState();

        Map<String, String> ruleProperties = new HashMap<>();
        ruleProperties.put(controllerServiceReferencingPropertyDescriptor.getName(), service.getIdentifier());
        rule.setProperties(ruleProperties);

        // WHEN
        // THEN
        assertFalse(rule.isEnabled());

        getFlowController().getControllerServiceProvider().enableControllerService(service).get();
        service.awaitEnabled(5, TimeUnit.SECONDS);
        rule.resetValidationState();
        getFlowController().getControllerServiceProvider().scheduleReferencingComponents(service);

        assertTrue(rule.isEnabled());

        getFlowController().getControllerServiceProvider().disableControllerService(service).get();
        rule.resetValidationState();
        getFlowController().getControllerServiceProvider().unscheduleReferencingComponents(service);

        assertFalse(rule.isEnabled());
    }
}
