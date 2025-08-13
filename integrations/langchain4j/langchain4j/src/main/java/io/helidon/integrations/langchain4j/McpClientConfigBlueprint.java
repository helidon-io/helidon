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

    @Option.Configured
    String sseUrl();

    @Option.Configured
    Optional<String> clientName();

    @Option.Configured
    Optional<String> clientVersion();

    @Option.Configured
    Optional<String> key();

    @Option.Configured
    Optional<String> protocolVersion();

    @Option.Configured
    Optional<Duration> initializationTimeout();

    @Option.Configured
    Optional<Duration> toolExecutionTimeout();

    @Option.Configured
    Optional<Duration> resourcesTimeout();

    @Option.Configured
    Optional<Duration> promptsTimeout();

    @Option.Configured
    Optional<Duration> pingTimeout();

    @Option.Configured
    Optional<Duration> reconnectInterval();

    @Option.Configured
    Optional<String> toolExecutionTimeoutErrorMessage();

}
