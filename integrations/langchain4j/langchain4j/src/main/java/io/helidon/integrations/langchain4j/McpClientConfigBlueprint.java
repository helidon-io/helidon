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
