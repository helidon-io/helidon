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

package io.helidon.tests.integration.idcstracing;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class IdcsRoleMapperStartupTest {

    @Test
    void shouldNotLoadIdcsMetadataDuringMpStartup() throws IOException {
        AtomicInteger metadataRequests = new AtomicInteger();
        HttpServer metadataServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        String baseUri = "http://localhost:" + metadataServer.getAddress().getPort();
        byte[] metadata = ("""
                {
                    "token_endpoint": "%1$s/token",
                    "authorization_endpoint": "%1$s/authorize",
                    "end_session_endpoint": "%1$s/logout",
                    "issuer": "%1$s",
                    "introspection_endpoint": "%1$s/introspect"
                }
                """).formatted(baseUri).getBytes(StandardCharsets.UTF_8);
        metadataServer.createContext("/.well-known/openid-configuration", exchange -> {
            metadataRequests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, metadata.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(metadata);
            }
        });
        metadataServer.start();

        Map<String, String> properties = new HashMap<>();
        properties.put("server.port", "0");
        properties.put("otel.sdk.disabled", "true");
        properties.put("security.providers.0.idcs-role-mapper.multitenant", "false");
        properties.put("security.providers.0.idcs-role-mapper.oidc-config.client-id", "test-client");
        properties.put("security.providers.0.idcs-role-mapper.oidc-config.client-secret", "test-secret");
        properties.put("security.providers.0.idcs-role-mapper.oidc-config.identity-uri", baseUri);

        Server server = null;
        try {
            Config config = Config.create(ConfigSources.create(properties));
            server = Server.builder()
                    .config(config)
                    .host(InetAddress.getLoopbackAddress().getHostAddress())
                    .port(0)
                    .build();
            server.start();

            assertThat(server.port(), greaterThan(0));
            assertThat(metadataRequests.get(), is(0));
        } finally {
            if (server != null && server.port() > 0) {
                server.stop();
            }
            metadataServer.stop(0);
        }
    }
}
