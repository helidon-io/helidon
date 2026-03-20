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

package io.helidon.microprofile.tyrus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.Configuration;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.OnMessage;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpoint;
import jakarta.websocket.server.ServerEndpointConfig;
import org.glassfish.tyrus.spi.UpgradeResponse;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

@AddBean(WebSocketHandshakeRejectionTest.HandshakeRejectingEndpoint.class)
@Configuration(configSources = "application.yaml")
class WebSocketHandshakeRejectionTest extends WebSocketBaseTest {

    @Test
    void testHandshakeWithoutRequiredHeader() throws Exception {
        String statusLine = openHandshake(null);
        assertThat(statusLine, startsWith("HTTP/1.1 403"));
    }

    @Test
    void testHandshakeWithRequiredHeader() throws Exception {
        String statusLine = openHandshake("42");
        assertThat(statusLine, is("HTTP/1.1 101 Switching Protocols"));
    }

    private String openHandshake(String tenantId) throws Exception {
        int serverPort = port();

        try (var socket = new java.net.Socket("localhost", serverPort);
             Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                                                                              StandardCharsets.UTF_8))) {
            writer.write("GET /reject HTTP/1.1\r\n");
            writer.write("Host: localhost:" + serverPort + "\r\n");
            writer.write("Upgrade: websocket\r\n");
            writer.write("Connection: Upgrade\r\n");
            writer.write("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n");
            writer.write("Sec-WebSocket-Version: 13\r\n");
            if (tenantId != null) {
                writer.write("X-Tenant-Id: " + tenantId + "\r\n");
            }
            writer.write("\r\n");
            writer.flush();

            String statusLine = reader.readLine();
            String headerLine;
            do {
                headerLine = reader.readLine();
            } while (headerLine != null && !headerLine.isEmpty());
            return statusLine;
        }
    }

    @ServerEndpoint(value = "/reject", configurator = RejectingConfigurator.class)
    public static class HandshakeRejectingEndpoint {

        @OnMessage
        public String onMessage(String message) {
            return message;
        }
    }

    public static class RejectingConfigurator extends ServerEndpointConfig.Configurator {

        @Override
        public void modifyHandshake(ServerEndpointConfig sec,
                                    HandshakeRequest request,
                                    HandshakeResponse response) {
            List<String> tenantIdHeaders = request.getHeaders().get("X-Tenant-Id");
            if (tenantIdHeaders == null || tenantIdHeaders.isEmpty()) {
                UpgradeResponse upgradeResponse = (UpgradeResponse) response;
                upgradeResponse.setReasonPhrase("Forbidden");
                upgradeResponse.setStatus(403);
            }
        }
    }
}
