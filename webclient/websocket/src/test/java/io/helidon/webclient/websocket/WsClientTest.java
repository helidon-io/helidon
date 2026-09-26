/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.websocket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.webclient.api.WebClient;
import io.helidon.websocket.WsListener;
import io.helidon.websocket.WsSession;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WsClientTest {
    @Test
    void rejectedUpgradeClosesConnection() throws Exception {
        assertUpgradeFailureClosesConnection("HTTP/1.1 403 Forbidden\r\n"
                                                     + "Content-Length: 4\r\n"
                                                     + "Connection: close\r\n\r\ndeny");
    }

    @Test
    void invalidUpgradeClosesConnection() throws Exception {
        assertUpgradeFailureClosesConnection("HTTP/1.1 101 Switching Protocols\r\n"
                                                     + "Connection: Upgrade\r\n"
                                                     + "Upgrade: websocket\r\n"
                                                     + "Sec-WebSocket-Accept: invalid\r\n\r\n");
    }

    @Test
    void testSchemeValidation() {
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class,
                             () -> WsClient.builder()
                                     .baseUri("test://localhost:8888/")
                                     .shareConnectionCache(false)
                                     .build()
                                     .connect("/whatever", new WsListener() {
                                         @Override
                                         public void onMessage(WsSession session, String text, boolean last) {
                                             //not used
                                         }
                                     }),
                             "Should have failed because of invalid scheme.");

        assertThat(ex.getMessage(), startsWith("Not supported scheme test"));
    }

    private static void assertUpgradeFailureClosesConnection(String response) throws Exception {
        try (var serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            serverSocket.setSoTimeout(5000);
            var remoteEof = executor.submit(() -> {
                try (var socket = serverSocket.accept()) {
                    socket.setSoTimeout(5000);
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                                                                           StandardCharsets.US_ASCII));
                    String line;
                    do {
                        line = reader.readLine();
                    } while (line != null && !line.isEmpty());
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    return socket.getInputStream().read();
                }
            });

            WebClient client = WebClient.builder()
                    .baseUri(new URI("http", null, serverSocket.getInetAddress().getHostAddress(),
                                     serverSocket.getLocalPort(), null, null, null))
                    .readTimeout(Duration.ofSeconds(5))
                    .connectTimeout(Duration.ofSeconds(5))
                    .shareConnectionCache(false)
                    .build();
            try {
                assertThrows(WsClientException.class, () -> client.client(WsClient.PROTOCOL)
                        .connect("/", new WsListener() { }));
                assertThat("failed upgrade must close the physical connection",
                           remoteEof.get(5, TimeUnit.SECONDS),
                           is(-1));
            } finally {
                client.closeResource();
            }
        }
    }
}
