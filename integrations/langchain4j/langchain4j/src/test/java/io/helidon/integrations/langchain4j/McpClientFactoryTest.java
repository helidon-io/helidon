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

import java.lang.reflect.Field;

import javax.net.ssl.SSLContext;

import io.helidon.config.Config;

import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_YAML;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;

class McpClientFactoryTest {

    @Test
    void testTransportUsesConfiguredTls() throws Exception {
        //language=YAML
        var cfg = """
                langchain4j:
                  mcp-clients:
                    cli-tools-mcp-server:
                      uri: https://localhost:9999/cli
                      tls:
                        trust-all: true
                        endpoint-identification-algorithm: NONE
                """;
        var config = Config.just(cfg, APPLICATION_YAML);
        var mcpClientConfig = McpClientFactory.configure(config).findFirst().orElseThrow();
        var transport = McpClientFactory.createTransport(mcpClientConfig);

        assertThat(extractSslContext(transport), sameInstance(mcpClientConfig.tls().orElseThrow().sslContext()));
    }

    private static SSLContext extractSslContext(StreamableHttpMcpTransport transport) throws Exception {
        Field field = StreamableHttpMcpTransport.class.getDeclaredField("sslContext");
        field.setAccessible(true);
        return (SSLContext) field.get(transport);
    }
}
