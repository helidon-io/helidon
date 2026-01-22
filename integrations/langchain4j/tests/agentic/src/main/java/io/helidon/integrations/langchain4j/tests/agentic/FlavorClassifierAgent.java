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

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

@Ai.Agent("flavor-classifier")
@Ai.ChatModel("openai-cheap-model")
public interface FlavorClassifierAgent {

    @UserMessage("""
            Analyze the following user request about Helidon framework and categorize it as 'mp' - MicroProfile flavor 
            or 'se' - Standard Edition flavor of Helidon.
            In case the request doesn't belong to any of those categories categorize it as 'se'.
            Reply with only one of those words and nothing else.
            The user request is: '{{request}}'.
            """)
    @Agent(value = "Categorize a user request", outputKey = "flavor")
    HelidonFlavor classify(@V("request") String request);
}
