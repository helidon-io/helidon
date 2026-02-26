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

package io.helidon.integrations.langchain4j.tests.agenticmcp;

import io.helidon.integrations.langchain4j.Ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * CLI expert that explains Helidon CLI commands.
 */
@Ai.Agent("cli-expert")
@Ai.ChatModel("openai-cheap-model")
@Ai.McpClients("cli-tools-mcp-server")
public interface CliExpert {

    @UserMessage("""
            You are a command line expert helping users with Helidon CLI.
            Provide a short step-by-step answer to the request: {{request}}
            and always include the exact CLI command on its own line.
            """)
    @Agent(value = "Helidon CLI specialist", outputKey = "response")
    String answer(@V("request") String request);
}