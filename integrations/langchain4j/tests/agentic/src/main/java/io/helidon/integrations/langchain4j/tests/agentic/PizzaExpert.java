/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.tests.agentic;

import io.helidon.integrations.langchain4j.Ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI‑driven expert for analyzing pizza‑related requests.
 *
 * <p>This interface is implemented by an AI agent that uses the specified
 * language model to evaluate user statements from the perspective of a
 * professional pizza chef.</p>
 */
@Ai.Agent("pizza-expert")
@Ai.ChatModel("google-gemini")
public interface PizzaExpert {

    /**
     * Analyzes the given user request from a pizza chef's perspective and determines
     * whether it mentions pineapple (ananas) on a pizza.
     *
     * @param request the user request to be examined; may contain any textual description
     *                or question related to pizza
     * @return {@code true} if the request explicitly mentions pineapple on pizza; {@code false}
     *         otherwise
     */
    @UserMessage("""
            You are a pizza expert.
            Analyze the following user request under a pizza chef point of view and figure out if it mention ananas on pizza,
            respond only 'true' or 'false'.
            The user request is {{request}}.
            """)
    @Agent(value = "A pizza expert")
    boolean ananasOnPizza(@V("request") String request);
}
