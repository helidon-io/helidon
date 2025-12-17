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

package io.helidon.integrations.langchain4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

@Prototype.Blueprint
@Prototype.Configured(AgentsConfigBlueprint.CONFIG_ROOT)
interface AgentsConfigBlueprint {
    /**
     * The default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.agents";

    /**
     * If set to {@code false} (default), OpenAiChatModel will not be available even if configured.
     *
     * @return whether OpenAiChatModel is enabled, defaults to {@code false}
     */
    @Option.Configured
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
    String chatModel();

    @Option.Configured
    Optional<String> chatMemory();

    @Option.Configured
    Optional<String> chatMemoryProvider();

    @Option.Configured
    Optional<String> contentRetriever();

    @Option.Configured
    Optional<String> retrievalAugmentor();

    @Option.Configured
    List<String> inputGuardrails();

    @Option.Configured
    List<String> outputGuardrails();

    default void configure(AgentBuilder<?> agentBuilder, ServiceRegistry serviceRegistry) {
        Objects.requireNonNull(agentBuilder, "agentBuilder must not be null");
        // required configuration
        agentBuilder.chatModel(serviceRegistry.getNamed(ChatModel.class, chatModel()));

        // optional simple values
        this.name().ifPresent(agentBuilder::name);
        this.description().ifPresent(agentBuilder::description);
        this.outputKey().ifPresent(agentBuilder::outputKey);
        this.async().ifPresent(agentBuilder::async);
        this.executeToolsConcurrently()
                .filter(b -> b)
                .ifPresent(b -> agentBuilder.executeToolsConcurrently());

        // optional components
        this.chatMemory().map(s -> serviceRegistry.getNamed(ChatMemory.class, s))
                .ifPresent(agentBuilder::chatMemory);
        this.chatMemoryProvider().map(s -> serviceRegistry.getNamed(ChatMemoryProvider.class, s))
                .ifPresent(agentBuilder::chatMemoryProvider);
        this.contentRetriever().map(s -> serviceRegistry.getNamed(ContentRetriever.class, s))
                .ifPresent(agentBuilder::contentRetriever);
        this.retrievalAugmentor().map(s -> serviceRegistry.getNamed(RetrievalAugmentor.class, s))
                .ifPresent(agentBuilder::retrievalAugmentor);

        // guardrails – only set when the lists are non‑empty
        if (!inputGuardrails().isEmpty()) {
            agentBuilder.inputGuardrails(inputGuardrails()
                                                 .stream()
                                                 .map(s -> serviceRegistry.getNamed(InputGuardrail.class, s))
                                                 .toList()
                                                 .toArray(new InputGuardrail[0])
            );
        }
        if (!outputGuardrails().isEmpty()) {
            agentBuilder.outputGuardrails(inputGuardrails()
                                                  .stream()
                                                  .map(s -> serviceRegistry.getNamed(OutputGuardrail.class, s))
                                                  .toList()
                                                  .toArray(new OutputGuardrail[0])
            );
        }
    }
}
