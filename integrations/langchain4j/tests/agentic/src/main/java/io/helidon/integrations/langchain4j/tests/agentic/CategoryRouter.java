/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
 * An AI‑driven router that classifies free‑form user requests into a
 * specific {@link RequestCategory}. The router analyses the supplied text
 * and determines whether the request is related to legal, medical,
 * technical matters, or does not fit any of these categories.
 *
 * @see RequestCategory
 */
@Ai.Agent("category-router")
@Ai.ChatModel("open-ai")
public interface CategoryRouter {

    /**
     * Classifies the given user request into a {@link RequestCategory}.
     *
     * @param request the user request to be categorized
     * @return the category of the request: {@code LEGAL}, {@code MEDICAL},
     *         {@code TECHNICAL} or {@code UNKNOWN}
     */
    @UserMessage("""
            Analyze the following user request and categorize it as 'legal', 'medical' or 'technical'.
            In case the request doesn't belong to any of those categories categorize it as 'unknown'.
            Reply with only one of those words and nothing else.
            The user request is: '{{request}}'.
            """)
    @Agent(value = "Categorize a user request", outputKey = "category")
    RequestCategory classify(@V("request") String request);
}
