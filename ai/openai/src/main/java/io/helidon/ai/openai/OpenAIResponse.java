/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.ai.openai;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a response from the OpenAI API.
 * This class encapsulates various details about the response such as
 * the ID, object type, creation timestamp, model used, system fingerprint,
 * finish reason, choices made, and usage statistics.
 * It implements {@link Serializable} for object serialization.
 *
 */
public class OpenAIResponse implements Serializable {

    private static final long serialVersionUID = 1L;
    private String id;
    private String object;
    private long created;
    private String model;
    private String system_fingerprint;
    private String finish_reason;
    private List<OpenAIChoice> choices = List.of();
    private OpenAIUsage usage;
    
    /**
     * Required public constructor.
     */
    public OpenAIResponse() {
    }

    /**
     * Gets the ID of the response.
     *
     * @return the ID of the response.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the ID of the response.
     *
     * @param id the ID of the response.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the object type of the response.
     *
     * @return the object type of the response.
     */
    public String getObject() {
        return object;
    }

    /**
     * Sets the object type of the response.
     *
     * @param object the object type of the response.
     */
    public void setObject(String object) {
        this.object = object;
    }

    /**
     * Gets the creation timestamp of the response.
     *
     * @return the creation timestamp of the response.
     */
    public long getCreated() {
        return created;
    }

    /**
     * Sets the creation timestamp of the response.
     *
     * @param created the creation timestamp of the response.
     */
    public void setCreated(long created) {
        this.created = created;
    }

    /**
     * Gets the model used to generate the response.
     *
     * @return the model used to generate the response.
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the model used to generate the response.
     *
     * @param model the model used to generate the response.
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Gets the system fingerprint of the response.
     *
     * @return the system fingerprint of the response.
     */
    public String getSystem_fingerprint() {
        return system_fingerprint;
    }

    /**
     * Sets the system fingerprint of the response.
     *
     * @param system_fingerprint the system fingerprint of the response.
     */
    public void setSystem_fingerprint(String system_fingerprint) {
        this.system_fingerprint = system_fingerprint;
    }

    /**
     * Gets the reason why the response was finished.
     *
     * @return the reason why the response was finished.
     */
    public String getFinish_reason() {
        return finish_reason;
    }

    /**
     * Sets the reason why the response was finished.
     *
     * @param finish_reason the reason why the response was finished.
     */
    public void setFinish_reason(String finish_reason) {
        this.finish_reason = finish_reason;
    }

    /**
     * Gets the list of choices included in the response.
     *
     * @return the list of choices included in the response.
     */
    public List<OpenAIChoice> getChoices() {
        return choices;
    }

    /**
     * Sets the list of choices included in the response.
     *
     * @param choices the list of choices included in the response.
     */
    public void setChoices(List<OpenAIChoice> choices) {
        this.choices = choices;
    }

    /**
     * Gets the usage statistics of the response.
     *
     * @return the usage statistics of the response.
     */
    public OpenAIUsage getUsage() {
        return usage;
    }

    /**
     * Sets the usage statistics of the response.
     *
     * @param usage the usage statistics of the response.
     */
    public void setUsage(OpenAIUsage usage) {
        this.usage = usage;
    }
}

