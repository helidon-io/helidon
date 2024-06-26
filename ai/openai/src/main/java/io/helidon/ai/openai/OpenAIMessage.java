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

/**
 * Represents a message in the OpenAI context.
 * This class encapsulates the role and content of a message.
 * It implements {@link Serializable} for object serialization.
 *
 */
public class OpenAIMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private String role;
    private String content;

    /**
     * Required public constructor.
     */
    public OpenAIMessage() {
    }

    /**
     * Gets the role of the message sender.
     *
     * @return the role of the message sender.
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the role of the message sender.
     *
     * @param role the role of the message sender.
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * Gets the content of the message.
     *
     * @return the content of the message.
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the content of the message.
     *
     * @param content the content of the message.
     */
    public void setContent(String content) {
        this.content = content;
    }
}
