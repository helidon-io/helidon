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

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Expert routing agent that forwards a user request to the appropriate specialist
 * sub‑agent based on the request category.
 *
 * <p>The {@code askExpert} method receives the user's request and, via the
 * {@link ConditionalAgent} annotation, delegates processing to one of the
 * {@code MedicalExpert}, {@code TechnicalExpert}, or {@code LegalExpert}
 * agents. The selection of the sub‑agent is controlled by the static activation
 * condition methods defined in this interface.</p>
 *
 * @see ConditionalAgent
 * @see UserMessage
 */
@Ai.Agent("experts-agent")
@Ai.ChatModel("google-gemini")
public interface ExpertsAgent {

    /**
     * Routes the supplied user request to the appropriate specialist sub‑agent
     * (medical, technical, or legal) based on the request category determined by
     * the activation conditions.
     *
     * @param request the user's request to be examined; any textual input
     *                describing a problem, question, or scenario that requires expert
     *                analysis.
     * @return the textual response produced by the selected expert sub‑agent.
     */
    @UserMessage("""
            You are an expert.
            The user request is {{request}}.
            """)
    @ConditionalAgent(outputKey = "response", subAgents = {
            MedicalExpert.class,
            TechnicalExpert.class,
            LegalExpert.class
    })
    String askExpert(@V("request") String request);

    /**
     * Determines whether the {@link MedicalExpert} sub‑agent should be activated based on
     * the supplied request category.
     *
     * @param category the request category derived from the user's input
     * @return {@code true} if {@code category} equals {@link RequestCategory#MEDICAL};
     *         otherwise {@code false}
     */
    @ActivationCondition(MedicalExpert.class)
    static boolean activateMedical(@V("category") RequestCategory category) {
        return category == RequestCategory.MEDICAL;
    }

    /**
     * Determines whether the {@link TechnicalExpert} sub‑agent should be activated based on
     * the supplied request category.
     *
     * @param category the request category derived from the user's input
     * @return {@code true} if {@code category} equals {@link RequestCategory#TECHNICAL};
     *         otherwise {@code false}
     */
    @ActivationCondition(TechnicalExpert.class)
    static boolean activateTechnical(@V("category") RequestCategory category) {
        return category == RequestCategory.TECHNICAL;
    }

    /**
     * Determines whether the {@link LegalExpert} sub‑agent should be activated based on
     * the supplied request category.
     *
     * @param category the request category derived from the user's input
     * @return {@code true} if {@code category} equals {@link RequestCategory#LEGAL};
     *         otherwise {@code false}
     */
    @ActivationCondition(LegalExpert.class)
    static boolean activateLegal(@V("category") RequestCategory category) {
        return category == RequestCategory.LEGAL;
    }
}
