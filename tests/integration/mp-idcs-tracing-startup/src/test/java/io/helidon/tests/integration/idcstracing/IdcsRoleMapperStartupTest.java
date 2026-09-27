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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class IdcsRoleMapperStartupTest {

    @Test
    void shouldNotLoadSingleTenantIdcsMetadataDuringMpStartup() throws IOException {
        shouldNotLoadIdcsMetadataDuringMpStartup(false, "localhost");
    }

    @Test
    void shouldNotLoadMultitenantIdcsMetadataDuringMpStartup() throws IOException {
        shouldNotLoadIdcsMetadataDuringMpStartup(true, "127.0.0.1");
    }

    private void shouldNotLoadIdcsMetadataDuringMpStartup(boolean multitenant, String metadataHost) throws IOException {
        try (MetadataServer metadataServer = new MetadataServer(metadataHost)) {
            Map<String, String> properties = new HashMap<>();
            properties.put("server.port", "0");
            properties.put("otel.sdk.disabled", "true");
            properties.put("security.providers.0.idcs-role-mapper.multitenant", Boolean.toString(multitenant));
            properties.put("security.providers.0.idcs-role-mapper.oidc-config.client-id", "test-client");
            properties.put("security.providers.0.idcs-role-mapper.oidc-config.client-secret", "test-secret");
            properties.put("security.providers.0.idcs-role-mapper.oidc-config.identity-uri", metadataServer.baseUri());

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
                assertThat(metadataServer.metadataRequests(), is(0));
            } finally {
                if (server != null && server.port() > 0) {
                    server.stop();
                }
            }
        }
    }

    private static final class MetadataServer implements AutoCloseable {
        private final AtomicInteger metadataRequests = new AtomicInteger();
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final String baseUri;
        private final byte[] metadata;

        private MetadataServer(String host) throws IOException {
            serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "idcs-metadata-startup-test");
                thread.setDaemon(true);
                return thread;
            });
            baseUri = "http://" + host + ":" + serverSocket.getLocalPort();
            metadata = ("""
                    {
                        "token_endpoint": "%1$s/token",
                        "authorization_endpoint": "%1$s/authorize",
                        "end_session_endpoint": "%1$s/logout",
                        "issuer": "%1$s",
                        "introspection_endpoint": "%1$s/introspect"
                    }
                    """).formatted(baseUri).getBytes(StandardCharsets.UTF_8);
            executor.execute(this::serve);
        }

        private String baseUri() {
            return baseUri;
        }

        private int metadataRequests() {
            return metadataRequests.get();
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        throw new IllegalStateException("Metadata test server failed", e);
                    }
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                                                                            StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            String headerLine = reader.readLine();
            while (headerLine != null && !headerLine.isEmpty()) {
                headerLine = reader.readLine();
            }

            boolean metadataRequest = requestLine != null
                    && requestLine.startsWith("GET /.well-known/openid-configuration ");
            if (metadataRequest) {
                metadataRequests.incrementAndGet();
            }

            byte[] body = metadataRequest ? metadata : new byte[0];
            String status = metadataRequest ? "200 OK" : "404 Not Found";
            byte[] headers = ("""
                    HTTP/1.1 %s\r
                    Content-Type: application/json\r
                    Content-Length: %d\r
                    Connection: close\r
                    \r
                    """).formatted(status, body.length).getBytes(StandardCharsets.US_ASCII);

            OutputStream output = socket.getOutputStream();
            output.write(headers);
            output.write(body);
            output.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                serverSocket.close();
            } finally {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
