package org.apache.nifi;


import org.apache.nifi.annotation.behavior.DynamicProperties;
import org.apache.nifi.annotation.behavior.DynamicProperty;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.configuration.DefaultSchedule;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.scheduling.SchedulingStrategy;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SupportsBatching
@Tags({"test"})
@InputRequirement(InputRequirement.Requirement.INPUT_REQUIRED)
@ReadsAttributes({
        @ReadsAttribute(attribute = "simple.template.read.attribute.1", description = "Simple template read attribute 1")
})
@CapabilityDescription("Simple template purpose description")
@DynamicProperties({
        @DynamicProperty(name = "Simple Template Dynamic Property 1", value = "Simple Template dynamic property value",
                expressionLanguageScope = ExpressionLanguageScope.VARIABLE_REGISTRY,
                description = "Simple template dynamic property 1 description.")
})
@DefaultSchedule(strategy = SchedulingStrategy.TIMER_DRIVEN, period = "10 sec")
public class SimpleTemplateProcessor extends AbstractProcessor {
    static final PropertyDescriptor SIMPLE_TEMPLATE_PROPERTY = new PropertyDescriptor.Builder()
            .name("simple-template-property-1")
            .displayName("Simple Template Property 1")
            .description("Simple template property 1 description")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();


    static final Relationship REL_SUCCESS = new Relationship.Builder()
            .name("success")
            .description("FlowFiles are routed to success relationship")
            .build();

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        List<PropertyDescriptor> propertyDescriptors = Collections.unmodifiableList(Arrays.asList((
                SIMPLE_TEMPLATE_PROPERTY
        )));

        return propertyDescriptors;
    }

    @Override
    protected PropertyDescriptor getSupportedDynamicPropertyDescriptor(final String propertyDescriptorName) {
        PropertyDescriptor propertyDescriptor = new PropertyDescriptor.Builder()
                .name(propertyDescriptorName)
                .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
                .required(false)
                .dynamic(true)
                .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
                .build();

        return propertyDescriptor;
    }

    @Override
    public Set<Relationship> getRelationships() {
        HashSet<Relationship> relationships = new HashSet<>(Arrays.asList(
                REL_SUCCESS
        ));

        return relationships;
    }

    @Override
    public void onTrigger(ProcessContext context, ProcessSession session) throws ProcessException {
        getLogger().debug("Simple Template Processor triggered");

        FlowFile inFlowFile = session.get();
        if (inFlowFile == null) {
            context.yield();
            return;
        }

        getLogger().debug("Simple Template Processor received flow file with size " + inFlowFile.getSize() + "B");

        session.transfer(inFlowFile, REL_SUCCESS);
    }
}
