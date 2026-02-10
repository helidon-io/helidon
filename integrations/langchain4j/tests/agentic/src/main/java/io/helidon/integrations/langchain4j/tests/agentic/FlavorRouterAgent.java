/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.integrations.langchain4j.Ai;

import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@Ai.Agent("flavor-router")
public interface FlavorRouterAgent {

    @UserMessage("""
            You are an Helidon expert.
            The user request is {{request}}.
            """)
    @ConditionalAgent(outputKey = "response", subAgents = {
            HelidonMpExpert.class,
            HelidonSeExpert.class
    })
    String askExpert(@V("request") String request);

    @ActivationCondition(HelidonSeExpert.class)
    static boolean activateSeExpert(@V("flavor") HelidonFlavor flavor) {
        if (flavor == HelidonFlavor.SE) {
            System.out.println("Activate SE Expert!");
            return true;
        }
        return false;
    }

    @ActivationCondition(HelidonMpExpert.class)
    static boolean activateMpExpert(@V("flavor") HelidonFlavor flavor) {
        if (flavor == HelidonFlavor.MP) {
            System.out.println("Activate MP Expert!");
            return true;
        }
        return false;
    }
}
