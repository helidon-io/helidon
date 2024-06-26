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
 * Represents the usage statistics for OpenAI.
 * This class is used to keep track of token usage for prompts and completions.
 * It implements {@link Serializable} for object serialization.
 *
 */
public class OpenAIUsage implements Serializable {

    private static final long serialVersionUID = 1L;
    private int prompt_tokens;
    private int completion_tokens;
    private int total_tokens;

    /**
     * Required public constructor.
     */
    public OpenAIUsage() {
    }

    /**
     * Gets the number of tokens used for the prompt.
     *
     * @return the number of prompt tokens.
     */
    public int getPrompt_tokens() {
        return prompt_tokens;
    }

    /**
     * Sets the number of tokens used for the prompt.
     *
     * @param prompt_tokens the number of prompt tokens.
     */
    public void setPrompt_tokens(int prompt_tokens) {
        this.prompt_tokens = prompt_tokens;
    }

    /**
     * Gets the number of tokens used for the completion.
     *
     * @return the number of completion tokens.
     */
    public int getCompletion_tokens() {
        return completion_tokens;
    }

    /**
     * Sets the number of tokens used for the completion.
     *
     * @param completion_tokens the number of completion tokens.
     */
    public void setCompletion_tokens(int completion_tokens) {
        this.completion_tokens = completion_tokens;
    }

    /**
     * Gets the total number of tokens used (prompt + completion).
     *
     * @return the total number of tokens.
     */
    public int getTotal_tokens() {
        return total_tokens;
    }

    /**
     * Sets the total number of tokens used (prompt + completion).
     *
     * @param total_tokens the total number of tokens.
     */
    public void setTotal_tokens(int total_tokens) {
        this.total_tokens = total_tokens;
    }
}