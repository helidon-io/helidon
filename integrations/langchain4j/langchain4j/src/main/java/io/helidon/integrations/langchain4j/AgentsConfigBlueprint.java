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

import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;

/**
 * Configuration for a single LangChain4j agent.
 * <p>
 * The configuration primarily consists of:
 * <ul>
 *     <li>Basic agent metadata (name, description, output key)</li>
 *     <li>Execution settings (async invocation and concurrent tool execution)</li>
 *     <li>References to services resolved from the {@link ServiceRegistry} (model, memory, retriever, tools, etc.)</li>
 *     <li>Guardrail class lists resolved from the {@link ServiceRegistry}</li>
 * </ul>
 */
@Prototype.Blueprint
@Prototype.Configured(HelidonConstants.AGENTS_KEY)
@Prototype.CustomMethods(AgentsConfigSupport.class)
interface AgentsConfigBlueprint {

    /**
     * If set to {@code false}, agent will not be available even if configured.
     *
     * @return whether agent is enabled, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Agent identifier used to label the agent in workflows and/or agent registries.
     * <p>
     * If configured, this value is applied to the underlying agent builder via {@code agentBuilder.name(...)}.
     *
     * @return configured agent name, or empty if not configured
     */
    @Option.Configured
    Optional<String> name();

    /**
     * Description of the agent.
     * It should be clear and descriptive to allow a language model
     * to understand the agent's purpose and its intended use.
     *
     * @return description of the agent.
     */
    @Option.Configured
    Optional<String> description();

    /**
     * Key of the output variable that will be used to store the result of the agent's invocation.
     *
     * @return name of the output variable.
     */
    @Option.Configured
    Optional<String> outputKey();

    /**
     * If true, the agent will be invoked in an asynchronous manner,
     * allowing the workflow to continue without waiting for the agent's result.
     *
     * @return true if the agent should be invoked asynchronously, false otherwise.
     */
    @Option.Configured
    Optional<Boolean> async();

    /**
     * If true, the agent's tools can be invoked in a concurrent manner.
     *
     * @return true if agent's tools can be invoked concurrently.
     */
    @Option.Configured
    Optional<Boolean> executeToolsConcurrently();

    /**
     * Name of the {@link dev.langchain4j.model.chat.ChatModel} service to use for this agent.
     * <p>
     * The value is resolved from the {@link ServiceRegistry}.
     *
     * @return configured name of the chat model service, or empty if not configured
     */
    @Option.Configured
    Optional<String> chatModel();

    /**
     * Name of the {@link dev.langchain4j.memory.ChatMemory} service to use for this agent.
     * <p>
     * The value is resolved from the {@link ServiceRegistry}.
     *
     * @return configured name of the chat memory service, or empty if not configured
     */
    @Option.Configured
    Optional<String> chatMemory();

    /**
     * Name of the {@link dev.langchain4j.memory.chat.ChatMemoryProvider} service to use for this agent.
     * <p>
     * The value is resolved from the {@link ServiceRegistry}.
     *
     * @return configured name of the chat memory provider service, or empty if not configured
     */
    @Option.Configured
    Optional<String> chatMemoryProvider();

    /**
     * Name of the {@link dev.langchain4j.rag.content.retriever.ContentRetriever} service to use for this agent.
     * <p>
     * The value is resolved from the {@link ServiceRegistry}.
     *
     * @return configured name of the content retriever service, or empty if not configured
     */
    @Option.Configured
    Optional<String> contentRetriever();

    /**
     * Name of the {@link dev.langchain4j.rag.RetrievalAugmentor} service to use for this agent.
     * <p>
     * The value is resolved from the {@link ServiceRegistry}.
     *
     * @return configured name of the retrieval augmentor service, or empty if not configured
     */
    @Option.Configured
    Optional<String> retrievalAugmentor();

    /**
     * Name of the {@link dev.langchain4j.service.tool.ToolProvider} service to use for this agent.
     * <p>
     * The value is resolved from the {@link ServiceRegistry}.
     *
     * @return configured name of the tool provider service, or empty if not configured
     */
    @Option.Configured
    Optional<String> toolProvider();

    /**
     * Tool service classes to register with the agent.
     * <p>
     * Each class is resolved from the {@link ServiceRegistry},
     * and the resulting service instances are registered using {@code agentBuilder.tools(...)}.
     *
     * @return configured set of tool classes, or an empty set if not configured
     */
    @Option.Configured
    Set<Class<?>> tools();

    /**
     * Names of {@link dev.langchain4j.mcp.client.McpClient} services to use for MCP-backed tools.
     * <p>
     * Each name is resolved from the {@link ServiceRegistry},
     * the clients are then used to build an {@link dev.langchain4j.mcp.McpToolProvider} which is registered as the agent's tool
     * provider.
     *
     * @return configured set of MCP client service names, or an empty set if not configured
     */
    @Option.Configured
    Set<String> mcpClients();

    /**
     * Input guardrail classes to apply to the agent.
     * <p>
     * Each class is resolved from the {@link ServiceRegistry}.
     *
     * @return configured set of input guardrail classes, or an empty set if not configured
     */
    @Option.Configured
    @Option.Singular
    Set<Class<? extends InputGuardrail>> inputGuardrails();

    /**
     * Output guardrail classes to apply to the agent.
     * <p>
     * Each class is resolved from the {@link ServiceRegistry},
     *
     * @return configured set of output guardrail classes, or an empty set if not configured
     */
    @Option.Configured
    @Option.Singular
    Set<Class<? extends OutputGuardrail>> outputGuardrails();
}
