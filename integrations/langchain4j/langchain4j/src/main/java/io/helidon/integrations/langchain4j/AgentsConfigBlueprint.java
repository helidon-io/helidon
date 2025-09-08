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

package io.helidon.integrations.langchain4j;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
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

@Prototype.Blueprint
@Prototype.Configured(AgentsConfigBlueprint.CONFIG_ROOT)
interface AgentsConfigBlueprint {
    /**
     * The default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.agents";

    /**
     * If set to {@code false}, agent will not be available even if configured.
     *
     * @return whether agent is enabled, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    @Option.Configured
    Optional<String> name();

    @Option.Configured
    Optional<String> description();

    @Option.Configured
    Optional<String> outputKey();

    @Option.Configured
    Optional<Boolean> async();

    @Option.Configured
    Optional<Boolean> executeToolsConcurrently();

    @Option.Configured
    Optional<String> chatModel();

    @Option.Configured
    Optional<String> chatMemory();

    @Option.Configured
    Optional<String> chatMemoryProvider();

    @Option.Configured
    Optional<String> contentRetriever();

    @Option.Configured
    Optional<String> retrievalAugmentor();

    @Option.Configured
    Optional<String> toolProvider();

    @Option.Configured
    Set<Class<?>> tools();

    @Option.Configured
    Set<String> mcpClients();

    @Option.Configured
    @Option.Singular
    Set<Class<? extends InputGuardrail>> inputGuardrails();

    @Option.Configured
    @Option.Singular
    Set<Class<? extends OutputGuardrail>> outputGuardrails();

    default void configure(AgenticServices.DeclarativeAgentCreationContext<?> ctx, ServiceRegistry serviceRegistry) {
        var agentBuilder = ctx.agentBuilder();
        Objects.requireNonNull(agentBuilder, "agentBuilder must not be null");

        this.chatModel().map(s -> serviceRegistry.getNamed(ChatModel.class, s))
                .ifPresent(agentBuilder::chatModel);

        this.name().ifPresent(agentBuilder::name);
        this.description().ifPresent(agentBuilder::description);
        this.outputKey().ifPresent(agentBuilder::outputKey);
        this.async().ifPresent(agentBuilder::async);
        this.executeToolsConcurrently()
                .filter(b -> b)
                .ifPresent(b -> agentBuilder.executeToolsConcurrently());

        this.chatMemory().map(s -> serviceRegistry.getNamed(ChatMemory.class, s))
                .ifPresent(agentBuilder::chatMemory);
        this.chatMemoryProvider().map(s -> serviceRegistry.getNamed(ChatMemoryProvider.class, s))
                .ifPresent(agentBuilder::chatMemoryProvider);
        this.contentRetriever().map(s -> serviceRegistry.getNamed(ContentRetriever.class, s))
                .ifPresent(agentBuilder::contentRetriever);
        this.retrievalAugmentor().map(s -> serviceRegistry.getNamed(RetrievalAugmentor.class, s))
                .ifPresent(agentBuilder::retrievalAugmentor);
        this.toolProvider().map(s -> serviceRegistry.getNamed(ToolProvider.class, s))
                .ifPresent(agentBuilder::toolProvider);

        // tools – only set when the list is non‑empty
        if (!tools().isEmpty()) {
            Object[] classes = tools()
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
        if (!mcpClients().isEmpty()) {
            McpToolProvider.Builder mcpToolProviderBuilder = McpToolProvider.builder();
            McpClient[] classes = mcpClients()
                    .stream()
                    .map(n -> serviceRegistry.firstNamed(McpClient.class, n)
                            .orElseThrow(() -> new IllegalStateException("No service bean found for mcp client " + n)))
                    .toArray(McpClient[]::new);
            mcpToolProviderBuilder.mcpClients(classes);
            agentBuilder.toolProvider(mcpToolProviderBuilder.build());
        }

        // guardrails – only set when the lists are non‑empty
        if (!inputGuardrails().isEmpty()) {
            @SuppressWarnings("unchecked")
            Class<? extends InputGuardrail>[] classes =
                    inputGuardrails().toArray(Class[]::new);
            agentBuilder.inputGuardrailClasses(classes);
        }

        if (!outputGuardrails().isEmpty()) {
            @SuppressWarnings("unchecked")
            Class<? extends OutputGuardrail>[] classes =
                    outputGuardrails().toArray(Class[]::new);
            agentBuilder.outputGuardrailClasses(classes);
        }
    }
}
