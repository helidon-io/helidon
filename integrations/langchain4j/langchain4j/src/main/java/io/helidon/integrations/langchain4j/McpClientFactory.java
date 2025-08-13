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

import io.helidon.common.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;

@Service.Singleton
class McpClientFactory implements Service.ServicesFactory<McpClient> {

    private final List<Service.QualifiedInstance<McpClient>> clients;

    McpClientFactory(Config config) {
        this.clients = config.get(McpClientConfigBlueprint.CONFIG_ROOT)
                .asNodeList()
                .orElse(List.of())
                .stream()
                .map(McpClientFactory::create)
                .toList();
    }

    private static Service.QualifiedInstance<McpClient> create(Config config) {
        McpClientConfig mcpClientConfig = McpClientConfig.create(config);
        HttpMcpTransport transport = new HttpMcpTransport.Builder()
                .sseUrl(mcpClientConfig.sseUrl())
                .build();

        DefaultMcpClient.Builder builder = new DefaultMcpClient.Builder()
                .transport(transport);

        mcpClientConfig.key().ifPresent(builder::key);
        mcpClientConfig.clientVersion().ifPresent(builder::clientVersion);
        mcpClientConfig.clientName().ifPresent(builder::clientName);
        mcpClientConfig.toolExecutionTimeoutErrorMessage().ifPresent(builder::toolExecutionTimeoutErrorMessage);
        mcpClientConfig.initializationTimeout().ifPresent(builder::initializationTimeout);
        mcpClientConfig.toolExecutionTimeout().ifPresent(builder::toolExecutionTimeout);
        mcpClientConfig.pingTimeout().ifPresent(builder::pingTimeout);
        mcpClientConfig.promptsTimeout().ifPresent(builder::promptsTimeout);
        mcpClientConfig.reconnectInterval().ifPresent(builder::reconnectInterval);
        mcpClientConfig.resourcesTimeout().ifPresent(builder::resourcesTimeout);

        DefaultMcpClient mcpClient = builder.build();

        return Service.QualifiedInstance.create(mcpClient, Qualifier.createNamed(mcpClient.key()));
    }

    @Override
    public List<Service.QualifiedInstance<McpClient>> services() {
        return clients;
    }
}
