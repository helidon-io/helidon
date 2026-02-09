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

package io.helidon.integrations.langchain4j;

import java.util.Objects;

import io.helidon.builder.api.Prototype;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.tool.ToolProvider;

class AgentsConfigSupport {

    private AgentsConfigSupport() {
    }

    /**
     * Configures LangChain4j {@link dev.langchain4j.agentic.agent.AgentBuilder} from {@link AgentsConfig}.
     * <p>
     * This method resolves any configured service references (such as {@link dev.langchain4j.model.chat.ChatModel},
     * {@link dev.langchain4j.memory.ChatMemory}, {@link dev.langchain4j.memory.chat.ChatMemoryProvider},
     * {@link dev.langchain4j.rag.content.retriever.ContentRetriever},
     * {@link dev.langchain4j.rag.RetrievalAugmentor},
     * {@link dev.langchain4j.service.tool.ToolProvider},
     * and {@link dev.langchain4j.mcp.client.McpClient}) from the provided {@link io.helidon.service.registry.ServiceRegistry} and
     * applies them to the
     * builder obtained from the supplied context.
     *
     * @param ctx             context providing the agent builder to configure
     * @param serviceRegistry registry used to resolve configured service references
     * @throws NullPointerException  if {@code ctx} is null, or if {@code ctx.agentBuilder()} returns null
     * @throws IllegalStateException if any configured tool class or MCP client cannot be resolved from the registry
     */
    @Prototype.PrototypeMethod
    static void configure(AgentsConfig agentsConfig,
                          AgenticServices.DeclarativeAgentCreationContext<?> ctx,
                          ServiceRegistry serviceRegistry) {
        var agentBuilder = ctx.agentBuilder();
        Objects.requireNonNull(agentBuilder, "agentBuilder must not be null");

        agentsConfig.chatModel().map(s -> serviceRegistry.getNamed(ChatModel.class, s))
                .ifPresent(agentBuilder::chatModel);

        agentsConfig.name().ifPresent(agentBuilder::name);
        agentsConfig.description().ifPresent(agentBuilder::description);
        agentsConfig.outputKey().ifPresent(agentBuilder::outputKey);
        agentsConfig.async().ifPresent(agentBuilder::async);
        agentsConfig.executeToolsConcurrently()
                .filter(b -> b)
                .ifPresent(b -> agentBuilder.executeToolsConcurrently());

        agentsConfig.chatMemory().map(s -> serviceRegistry.getNamed(ChatMemory.class, s))
                .ifPresent(agentBuilder::chatMemory);
        agentsConfig.chatMemoryProvider().map(s -> serviceRegistry.getNamed(ChatMemoryProvider.class, s))
                .ifPresent(agentBuilder::chatMemoryProvider);
        agentsConfig.contentRetriever().map(s -> serviceRegistry.getNamed(ContentRetriever.class, s))
                .ifPresent(agentBuilder::contentRetriever);
        agentsConfig.retrievalAugmentor().map(s -> serviceRegistry.getNamed(RetrievalAugmentor.class, s))
                .ifPresent(agentBuilder::retrievalAugmentor);
        agentsConfig.toolProvider().map(s -> serviceRegistry.getNamed(ToolProvider.class, s))
                .ifPresent(agentBuilder::toolProvider);

        // tools – only set when the list is non‑empty
        if (!agentsConfig.tools().isEmpty()) {
            Object[] classes = agentsConfig.tools()
                    .stream()
                    // First look for unnamed types
                    .map(c -> serviceRegistry.first(c)
                            .map(Object.class::cast)
                            // Then try named ones of the same type
                            .or(() -> serviceRegistry.firstNamed(c, "*"))
                            .orElseThrow(() -> new IllegalStateException("No service bean found for tool " + c)))
                    .toArray(Object[]::new);
            agentBuilder.tools(classes);
        }

        // mcp clients – only set when the list is non‑empty
        if (!agentsConfig.mcpClients().isEmpty()) {
            McpToolProvider.Builder mcpToolProviderBuilder = McpToolProvider.builder();
            McpClient[] classes = agentsConfig.mcpClients()
                    .stream()
                    .map(n -> serviceRegistry.firstNamed(McpClient.class, n)
                            .orElseThrow(() -> new IllegalStateException("No service bean found for mcp client " + n)))
                    .toArray(McpClient[]::new);
            mcpToolProviderBuilder.mcpClients(classes);
            agentBuilder.toolProvider(mcpToolProviderBuilder.build());
        }

        // guardrails – only set when the lists are non‑empty
        if (!agentsConfig.inputGuardrails().isEmpty()) {
            @SuppressWarnings("unchecked")
            Class<? extends InputGuardrail>[] classes =
                    agentsConfig.inputGuardrails().toArray(Class[]::new);
            agentBuilder.inputGuardrailClasses(classes);
        }

        if (!agentsConfig.outputGuardrails().isEmpty()) {
            @SuppressWarnings("unchecked")
            Class<? extends OutputGuardrail>[] classes =
                    agentsConfig.outputGuardrails().toArray(Class[]::new);
            agentBuilder.outputGuardrailClasses(classes);
        }
    }
}
