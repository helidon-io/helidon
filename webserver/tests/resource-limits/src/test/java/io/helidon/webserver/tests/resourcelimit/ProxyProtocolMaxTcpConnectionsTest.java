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

package io.helidon.webserver.tests.resourcelimit;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ProxyProtocolMaxTcpConnectionsTest {
    private static final int SOCKET_TIMEOUT_MILLIS = 5000;

    @Test
    void failedProxyProtocolSetupReleasesConnectionLimit() throws Exception {
        WebServer server = WebServer.builder()
                .port(0)
                .enableProxyProtocol(true)
                .maxTcpConnections(1)
                .routing(routing -> routing.get("/", (req, res) -> res.send("ok")))
                .build()
                .start();

        try {
            try (Socket malformed = new Socket("127.0.0.1", server.port())) {
                malformed.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                malformed.getOutputStream().write("PROXY \r\n".getBytes(StandardCharsets.US_ASCII));
                malformed.getOutputStream().flush();
                try {
                    assertThat(malformed.getInputStream().read(), is(-1));
                } catch (SocketException e) {
                    // Connection reset is also an acceptable close signal for the malformed socket.
                }
            }

            String response = "";
            try (Socket valid = new Socket("127.0.0.1", server.port())) {
                valid.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                valid.getOutputStream()
                        .write(("PROXY TCP4 192.168.0.1 192.168.0.11 56324 443\r\n"
                                + "GET / HTTP/1.1\r\n"
                                + "Host: localhost\r\n"
                                + "Connection: close\r\n"
                                + "\r\n").getBytes(StandardCharsets.US_ASCII));
                valid.getOutputStream().flush();
                ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[256];
                do {
                    int read = valid.getInputStream().read(buffer);
                    if (read == -1) {
                        break;
                    }
                    responseBytes.write(buffer, 0, read);
                    response = responseBytes.toString(StandardCharsets.US_ASCII);
                } while (!response.contains("ok"));
            }

            assertThat(response, containsString("200 OK"));
            assertThat(response, containsString("ok"));
        } finally {
            server.stop();
        }
    }
}
