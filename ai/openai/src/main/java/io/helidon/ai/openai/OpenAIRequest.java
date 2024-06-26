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
 * Represents a request to the OpenAI API.
 * This class encapsulates the model to be used and the list of messages included in the request.
 * It implements {@link Serializable} for object serialization.
 *
 */
public class OpenAIRequest implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_OPENID_MODEL = "gpt-4o";
    private String model = DEFAULT_OPENID_MODEL;
    private List<OpenAIMessage> messages = List.of();

    /**
     * Required public constructor.
     */
    public OpenAIRequest() {
    }

    /**
     * Gets the model to be used for the request.
     * The default model is "gpt-4o".
     *
     * @return the model to be used for the request.
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the model to be used for the request.
     *
     * @param model the model to be used for the request.
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Gets the list of messages included in the request.
     *
     * @return the list of messages included in the request.
     */
    public List<OpenAIMessage> getMessages() {
        return messages;
    }

    /**
     * Sets the list of messages included in the request.
     *
     * @param messages the list of messages included in the request.
     */
    public void setMessages(List<OpenAIMessage> messages) {
        this.messages = messages;
    }
}

