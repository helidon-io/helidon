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
 * Represents a choice made by OpenAI.
 * This class encapsulates the index of the choice, the message associated with the choice,
 * and the reason why the choice was finished.
 * It implements {@link Serializable} for object serialization.
 *
 */
public class OpenAIChoice implements Serializable {

    private static final long serialVersionUID = 1L;
    private int index;
    private OpenAIMessage message;
    private String finish_reason;

    /**
     * Required public constructor.
     */
    public OpenAIChoice() {
    }

    /**
     * Gets the index of the choice.
     *
     * @return the index of the choice.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Sets the index of the choice.
     *
     * @param index the index of the choice.
     */
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * Gets the message associated with the choice.
     *
     * @return the message associated with the choice.
     */
    public OpenAIMessage getMessage() {
        return message;
    }

    /**
     * Sets the message associated with the choice.
     *
     * @param message the message associated with the choice.
     */
    public void setMessage(OpenAIMessage message) {
        this.message = message;
    }

    /**
     * Gets the reason why the choice was finished.
     *
     * @return the reason why the choice was finished.
     */
    public String getFinish_reason() {
        return finish_reason;
    }

    /**
     * Sets the reason why the choice was finished.
     *
     * @param finish_reason the reason why the choice was finished.
     */
    public void setFinish_reason(String finish_reason) {
        this.finish_reason = finish_reason;
    }

}

