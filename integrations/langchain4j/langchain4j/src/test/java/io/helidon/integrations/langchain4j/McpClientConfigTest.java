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

import java.net.URI;
import java.time.Duration;

import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_YAML;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class McpClientConfigTest {

    @Test
    void testMcpClientConfig() {
        //language=YAML
        var cfg = """
                langchain4j:
                  mcp-clients:
                    cli-tools-mcp-server:
                      key: cli-tools-mcp-server
                      uri: http://localhost:9999/cli
                      client-name: test-client
                      client-version: 2.2.2
                      initialization-timeout: PT15M
                      prompts-timeout: PT5M
                      ping-timeout: PT10M
                      protocol-version: 1.2.3
                      reconnect-interval: PT15S
                      resources-timeout: PT10S
                      tool-execution-timeout: PT20S
                      tool-execution-timeout-error-message: Tool timeout!!!
                      log-requests: true
                      log-responses: true
                """;
        var c = Config.just(cfg, APPLICATION_YAML);
        var mcpClientConfig = McpClientFactory.configure(c).findFirst().get();
        assertThat(mcpClientConfig.uri(), is(URI.create("http://localhost:9999/cli")));
        assertThat(mcpClientConfig.key(), optionalValue(is("cli-tools-mcp-server")));
        assertThat(mcpClientConfig.clientName(), optionalValue(is("test-client")));
        assertThat(mcpClientConfig.clientVersion(), optionalValue(is("2.2.2")));
        assertThat(mcpClientConfig.initializationTimeout(), optionalValue(is(Duration.ofMinutes(15))));
        assertThat(mcpClientConfig.pingTimeout(), optionalValue(is(Duration.ofMinutes(10))));
        assertThat(mcpClientConfig.promptsTimeout(), optionalValue(is(Duration.ofMinutes(5))));
        assertThat(mcpClientConfig.protocolVersion(), optionalValue(is("1.2.3")));
        assertThat(mcpClientConfig.reconnectInterval(), optionalValue(is(Duration.ofSeconds(15))));
        assertThat(mcpClientConfig.resourcesTimeout(), optionalValue(is(Duration.ofSeconds(10))));
        assertThat(mcpClientConfig.toolExecutionTimeout(), optionalValue(is(Duration.ofSeconds(20))));
        assertThat(mcpClientConfig.toolExecutionTimeoutErrorMessage(), optionalValue(is("Tool timeout!!!")));
        assertThat(mcpClientConfig.logRequests(), optionalValue(is(true)));
        assertThat(mcpClientConfig.logResponses(), optionalValue(is(true)));
    }

    @Test
    void testMcpClientDeprecatedConfig() {
        //language=YAML
        var cfg = """
                langchain4j:
                  mcp-clients:
                    cli-tools-mcp-server:
                      sse-uri: http://localhost:9999/cli
                      log-requests: false
                      log-responses: false
                """;
        var c = Config.just(cfg, APPLICATION_YAML);
        var mcpClientConfig = McpClientFactory.configure(c).findFirst().get();
        assertThat(mcpClientConfig.uri(), is(URI.create("http://localhost:9999/cli")));
        assertThat(mcpClientConfig.key(), optionalValue(is("cli-tools-mcp-server")));
    }

    @Test
    void testMcpClientCustomKey() {
        //language=YAML
        var cfg = """
                langchain4j:
                  mcp-clients:
                    cli-tools-mcp-server:
                      key: different-cli-tools-mcp-server
                      uri: http://localhost:9999/cli
                      log-requests: false
                      log-responses: false
                """;
        var c = Config.just(cfg, APPLICATION_YAML);
        var mcpClientConfig = McpClientFactory.configure(c).findFirst().get();
        assertThat(mcpClientConfig.uri(), is(URI.create("http://localhost:9999/cli")));
        assertThat(mcpClientConfig.key(), optionalValue(is("different-cli-tools-mcp-server")));
    }
}
