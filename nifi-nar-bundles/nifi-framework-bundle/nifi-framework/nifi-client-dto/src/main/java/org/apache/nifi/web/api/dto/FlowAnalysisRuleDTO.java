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
package org.apache.nifi.web.api.dto;

import io.swagger.annotations.ApiModelProperty;

import javax.xml.bind.annotation.XmlType;
import java.util.Collection;
import java.util.Map;

@XmlType(name = "flowAnalysisRule")
public class FlowAnalysisRuleDTO extends ComponentDTO {
    public static final String VALID = "VALID";
    public static final String INVALID = "INVALID";
    public static final String VALIDATING = "VALIDATING";

    private String name;
    private String type;
    private BundleDTO bundle;
    private String state;
    private String comments;
    private Boolean persistsState;
    private Boolean restricted;
    private Boolean deprecated;
    private Boolean isExtensionMissing;
    private Boolean multipleVersionsAvailable;

    private String ruleType;

    private Map<String, String> properties;
    private Map<String, PropertyDescriptorDTO> descriptors;

    private String customUiUrl;
    private String annotationData;

    private Collection<String> validationErrors;
    private String validationStatus;

    /**
     * @return user-defined name of the flow analysis rule
     */
    @ApiModelProperty(
            value = "The name of the flow analysis rule."
    )
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return user-defined comments for the flow analysis rule
     */
    @ApiModelProperty(
            value = "The comments of the flow analysis rule."
    )
    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    /**
     * @return type of flow analysis rule
     */
    @ApiModelProperty(
            value = "The fully qualified type of the flow analysis rule."
    )
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The details of the artifact that bundled this processor type.
     *
     * @return The bundle details
     */
    @ApiModelProperty(
            value = "The details of the artifact that bundled this processor type."
    )
    public BundleDTO getBundle() {
        return bundle;
    }

    public void setBundle(BundleDTO bundle) {
        this.bundle = bundle;
    }
    /**
     * @return whether this flow analysis rule persists state
     */
    @ApiModelProperty(
        value = "Whether the flow analysis rule persists state."
    )
    public Boolean getPersistsState() {
        return persistsState;
    }

    public void setPersistsState(Boolean persistsState) {
        this.persistsState = persistsState;
    }

    /**
     * @return whether this flow analysis rule requires elevated privileges
     */
    @ApiModelProperty(
            value = "Whether the flow analysis rule requires elevated privileges."
    )
    public Boolean getRestricted() {
        return restricted;
    }

    public void setRestricted(Boolean restricted) {
        this.restricted = restricted;
    }

    /**
     * @return Whether the flow analysis rule has been deprecated.
     */
    @ApiModelProperty(
            value = "Whether the flow analysis rule has been deprecated."
    )
    public Boolean getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(Boolean deprecated) {
        this.deprecated= deprecated;
    }

    /**
     * @return whether the underlying extension is missing
     */
    @ApiModelProperty(
            value = "Whether the underlying extension is missing."
    )
    public Boolean getExtensionMissing() {
        return isExtensionMissing;
    }

    public void setExtensionMissing(Boolean extensionMissing) {
        isExtensionMissing = extensionMissing;
    }

    /**
     * @return whether this flow analysis rule has multiple versions available
     */
    @ApiModelProperty(
            value = "Whether the flow analysis rule has multiple versions available."
    )
    public Boolean getMultipleVersionsAvailable() {
        return multipleVersionsAvailable;
    }

    public void setMultipleVersionsAvailable(Boolean multipleVersionsAvailable) {
        this.multipleVersionsAvailable = multipleVersionsAvailable;
    }

    /**
     * @return current scheduling state of the flow analysis rule
     */
    @ApiModelProperty(
            value = "The state of the flow analysis rule.",
            allowableValues = "ENABLED, DISABLED"
    )
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    /**
     * @return Rule type
     */
    @ApiModelProperty(
            value = "Rule type."
    )
    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    /**
     * @return flow analysis rule's properties
     */
    @ApiModelProperty(
            value = "The properties of the flow analysis rule."
    )
    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    /**
     * @return Map of property name to descriptor
     */
    @ApiModelProperty(
            value = "The descriptors for the flow analysis rules properties."
    )
    public Map<String, PropertyDescriptorDTO> getDescriptors() {
        return descriptors;
    }

    public void setDescriptors(Map<String, PropertyDescriptorDTO> descriptors) {
        this.descriptors = descriptors;
    }

    /**
     * @return the URL for this flow analysis rule custom configuration UI if applicable. Null otherwise
     */
    @ApiModelProperty(
            value = "The URL for the custom configuration UI for the flow analysis rule."
    )
    public String getCustomUiUrl() {
        return customUiUrl;
    }

    public void setCustomUiUrl(String customUiUrl) {
        this.customUiUrl = customUiUrl;
    }

    /**
     * @return currently configured annotation data for the flow analysis rule
     */
    @ApiModelProperty(
            value = "The annotation data for the repoting task. This is how the custom UI relays configuration to the flow analysis rule."
    )
    public String getAnnotationData() {
        return annotationData;
    }

    public void setAnnotationData(String annotationData) {
        this.annotationData = annotationData;
    }

    /**
     * Gets the validation errors from this flow analysis rule. These validation errors represent the problems with the flow analysis rule that must be resolved before it can be scheduled to run.
     *
     * @return The validation errors
     */
    @ApiModelProperty(
            value = "Gets the validation errors from the flow analysis rule. These validation errors represent the problems with the flow analysis rule that must be resolved before "
                    + "it can be scheduled to run."
    )
    public Collection<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(Collection<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    @ApiModelProperty(value = "Indicates whether the Processor is valid, invalid, or still in the process of validating (i.e., it is unknown whether or not the Processor is valid)",
        readOnly = true,
        allowableValues = VALID + ", " + INVALID + ", " + VALIDATING)
    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

}
