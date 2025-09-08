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

import io.helidon.integrations.langchain4j.Ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;

@Ai.Agent("helidon-se-expert")
@Ai.ChatModel("gemini-flash-model")
public interface HelidonSeExpert {

    @UserMessage("""
            You are a Helidon SE expert.
            Analyze the following user request about Helidon SE and provide the best possible answer.
            Always warn against using reflection and stress out that Helidon SE provides best possible performance.
            The user request is {{request}}.
            """)
    @Agent(value = "A Helidon SE expert", outputKey = "response")
    @InputGuardrails(ForbiddenWordsInputGuardrail.class)
    String askExpert(@V("request") String request);
}