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

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

@Prototype.Configured
@Prototype.Blueprint
interface McpClientConfigBlueprint {

    /**
     * The default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.mcp-clients";

    /**
     * The initial URI where to connect to the server and request a SSE
     * channel.
     *
     * @return sse uri
     */
    @Option.Configured
    URI sseUri();

    /**
     * Sets the name that the client will use to identify itself to the
     * MCP server in the initialization message.
     * Overwrites the default client name from langchain4j.
     *
     * @return client name
     */
    @Option.Configured
    Optional<String> clientName();

    /**
     * Sets the version string that the client will use to identify
     * itself to the MCP server in the initialization message.
     * Overwrites the default client version from langchain4j.
     *
     * @return client version
     */
    @Option.Configured
    Optional<String> clientVersion();

    /**
     * Sets a unique identifier for the client. If none is provided, a
     * UUID will be automatically generated. This key is later used to identify the client
     * in the service registry.
     *
     * @return client key
     */
    @Option.Configured
    Optional<String> key();

    /**
     * Sets the protocol version that the client will advertise in the
     * initialization message. Overwrites the default version from langchain4j.
     *
     * @return protocol version
     */
    @Option.Configured
    Optional<String> protocolVersion();

    /**
     * Sets the timeout for initializing the client.
     * Overwrites the default timeout for initializing from langchain4j.
     *
     * @return initialization timout
     */
    @Option.Configured
    Optional<Duration> initializationTimeout();

    /**
     * Sets the timeout for tool execution.
     * This value applies to each tool execution individually.
     * A value of zero means no timeout.
     * Overwrites the default timeout for tool execution from langchain4j.
     *
     * @return tool execution timout
     */
    @Option.Configured
    Optional<Duration> toolExecutionTimeout();

    /**
     * Sets the timeout for resource-related operations (listing resources as well as reading the contents of a resource).
     * A value of zero means no timeout.
     * Overwrites the default timeout for resource-related operations from langchain4j.
     *
     * @return resources timeout
     */
    @Option.Configured
    Optional<Duration> resourcesTimeout();

    /**
     * The timeout for prompt-related operations (listing prompts as well as rendering the contents of a prompt).
     * A value of zero means no timeout.
     * Overwrites the default timeout for prompt-related operations from langchain4j.
     *
     * @return prompts timeout
     */
    @Option.Configured
    Optional<Duration> promptsTimeout();

    /**
     * The timeout to apply when waiting for a ping response.
     * Overwrites the default timeout when waiting for a ping response from langchain4j.
     *
     * @return ping timeout
     */
    @Option.Configured
    Optional<Duration> pingTimeout();

    /**
     * The delay before attempting to reconnect after a failed connection.
     * Overwrites the default reconnect interval from langchain4j.
     *
     * @return reconnect interval
     */
    @Option.Configured
    Optional<Duration> reconnectInterval();

    /**
     * The error message to return when a tool execution times out.
     * Overwrites the default error message from langchain4j.
     *
     * @return time out error message
     */
    @Option.Configured
    Optional<String> toolExecutionTimeoutErrorMessage();

    /**
     * Whether to log request traffic.
     *
     * @return log request traffic
     */
    @Option.Configured
    Optional<Boolean> logRequests();

    /**
     * Whether to log response traffic.
     *
     * @return log response traffic
     */
    @Option.Configured
    Optional<Boolean> logResponses();

}
