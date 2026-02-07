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

/**
 * Metadata describing discovered AI agent.
 * <p>
 * Implementations of this interface are typically produced by code generation and used at runtime to
 * identify the agent type, provide a stable logical name, and expose build-time configuration that
 * can be used when wiring the agent into the application.
 */
public interface AgentMetadata {

    /**
     * Returns the Java type that represents LangChain4j agent interface.
     *
     * @return the agent class
     */
    Class<?> agentClass();

    /**
     * Returns the logical name of the agent.
     * <p>
     * This name is intended to be stable and can be used for configuration lookups, registration,
     * and reporting.
     *
     * @return agent name
     */
    String agentName();

    /**
     * Returns build-time configuration associated with this agent.
     *
     * @return build-time agent configuration
     */
    AgentsConfig buildTimeConfig();
}
