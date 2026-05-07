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

package io.helidon.security.integration.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ProtocolUpgradeHandler;
import io.helidon.webserver.websocket.WebSocketRouting;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

class WebSocketSecurityUpgradeTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ORIGIN = "https://evil.example";
    private static final String UPGRADE_POLICY_HEADER = "X-Upgrade-Policy";
    private static final String UPGRADE_POLICY_VALUE = "checked";

    private static WebServer server;

    @BeforeAll
    static void beforeAll() {
        Security security = Security.create(Config.create().get("security"));

        server = WebServer.builder()
                .defaultSocket(socket -> socket.host("localhost").port(0))
                .addRouting(Routing.builder()
                                    .register(WebSecurity.create(security))
                                    .get("/secure-ws[/{*}]", WebSecurity.authenticate())
                                    .get("/secure-ws/echo", new UpgradePolicyHandler())
                                    .build())
                .addRouting(WebSocketRouting.builder()
                                    .endpoint("/secure-ws", WebSocketTestEndpoint.class)
                                    .build())
                .build()
                .start()
                .await(TIMEOUT);
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.shutdown()
                    .await(TIMEOUT);
        }
    }

    @Test
    void unauthenticatedUpgradeIsRejected() throws IOException {
        assertThat(upgradeStatusLine(), startsWith("HTTP/1.1 401 "));
    }

    @Test
    void unauthenticatedUpgradeWithSplitConnectionHeadersIsRejected() throws IOException {
        assertThat(upgradeStatusLine("Connection: keep-alive"), startsWith("HTTP/1.1 401 "));
    }

    @Test
    void upgradeWithPayloadIsRejected() throws IOException {
        assertThat(upgradeStatusLineWithBody("x", "Content-Length: 1"), startsWith("HTTP/1.1 401 "));
    }

    @Test
    void authenticatedUpgradeWithPayloadIsRejected() throws IOException {
        assertThat(upgradeStatusLineWithBody("x",
                                             "Authorization: Basic " + basicCredentials(),
                                             "Content-Length: 1"),
                   startsWith("HTTP/1.1 400 "));
    }

    @Test
    void authenticatedChunkedUpgradeIsRejected() throws IOException {
        assertThat(upgradeStatusLineWithBody("1\r\nx\r\n0\r\n\r\n",
                                             "Authorization: Basic " + basicCredentials(),
                                             "Transfer-Encoding: chunked"),
                   startsWith("HTTP/1.1 400 "));
    }

    @Test
    void authenticatedUpgradeIsAccepted() throws Exception {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            writeUpgradeRequest(writer, "", "Authorization: Basic " + basicCredentials());

            InputStream input = socket.getInputStream();
            assertThat(readHttpHeaders(input), startsWith("HTTP/1.1 101 "));

            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] mask = new byte[] {1, 2, 3, 4};
            OutputStream output = socket.getOutputStream();
            output.write(0x81);
            output.write(0x80 | payload.length);
            output.write(mask);
            for (int i = 0; i < payload.length; i++) {
                output.write(payload[i] ^ mask[i % mask.length]);
            }
            output.flush();

            int first = input.read();
            int second = input.read();
            assertThat(first & 0x0F, is(1));

            int length = second & 0x7F;
            if (length == 126) {
                length = (input.read() << 8) + input.read();
            } else if (length == 127) {
                throw new IOException("Unexpected large WebSocket frame");
            }

            boolean masked = (second & 0x80) != 0;
            byte[] responseMask = masked ? input.readNBytes(mask.length) : null;
            byte[] responsePayload = input.readNBytes(length);
            if (responsePayload.length != length || (masked && responseMask.length != mask.length)) {
                throw new IOException("Unexpected end of stream");
            }
            if (masked) {
                for (int i = 0; i < responsePayload.length; i++) {
                    responsePayload[i] = (byte) (responsePayload[i] ^ responseMask[i % responseMask.length]);
                }
            }
            assertThat(new String(responsePayload, StandardCharsets.UTF_8), is("hello"));
        }
    }

    @Test
    void authenticatedUpgradeIncludesPolicyHeaders() throws IOException {
        String response = upgradeResponseWithBody("", "Authorization: Basic " + basicCredentials());
        assertThat(response, containsString(UPGRADE_POLICY_HEADER + ": " + UPGRADE_POLICY_VALUE + "\r\n"));
    }

    @Test
    void upgradeHeaderWithoutConnectionUpgradeFallsThroughAsHttp() throws IOException {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.print("GET /secure-ws/echo HTTP/1.1\r\n");
            writer.print("Host: localhost\r\n");
            writer.print("Upgrade: websocket\r\n");
            writer.print("Authorization: Basic " + basicCredentials() + "\r\n");
            writer.print("\r\n");
            writer.flush();

            assertThat(readHttpHeaders(socket.getInputStream()), startsWith("HTTP/1.1 404 "));
        }
    }

    private static String basicCredentials() {
        return Base64.getEncoder()
                .encodeToString("john:password".getBytes(StandardCharsets.UTF_8));
    }

    private static String upgradeStatusLine(String... extraHeaders) throws IOException {
        return upgradeStatusLineWithBody("", extraHeaders);
    }

    private static String upgradeStatusLineWithBody(String body, String... extraHeaders) throws IOException {
        String response = upgradeResponseWithBody(body, extraHeaders);
        return response.substring(0, response.indexOf("\r\n"));
    }

    private static String upgradeResponseWithBody(String body, String... extraHeaders) throws IOException {
        try (Socket socket = new Socket("localhost", server.port())) {
            socket.setSoTimeout((int) TIMEOUT.toMillis());

            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            writeUpgradeRequest(writer, body, extraHeaders);
            return readHttpHeaders(socket.getInputStream());
        }
    }

    private static void writeUpgradeRequest(PrintWriter writer, String body, String... extraHeaders) {
        writer.print("GET /secure-ws/echo HTTP/1.1\r\n");
        writer.print("Host: localhost\r\n");
        for (String extraHeader : extraHeaders) {
            writer.print(extraHeader);
            writer.print("\r\n");
        }
        writer.print("Connection: Upgrade\r\n");
        writer.print("Upgrade: websocket\r\n");
        writer.print("Origin: " + ORIGIN + "\r\n");
        writer.print("Sec-WebSocket-Key: 0SBbaRkS/idPrmvImDNHBA==\r\n");
        writer.print("Sec-WebSocket-Version: 13\r\n");
        writer.print("\r\n");
        writer.print(body);
        writer.flush();
    }

    private static String readHttpHeaders(InputStream input) throws IOException {
        StringBuilder response = new StringBuilder();
        int matched = 0;
        byte[] end = new byte[] {'\r', '\n', '\r', '\n'};
        int read;
        while ((read = input.read()) != -1) {
            response.append((char) read);
            if (read == end[matched]) {
                matched++;
                if (matched == end.length) {
                    break;
                }
            } else {
                matched = read == end[0] ? 1 : 0;
            }
        }
        return response.toString();
    }

    private static final class UpgradePolicyHandler implements ProtocolUpgradeHandler {
        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            res.headers().add(UPGRADE_POLICY_HEADER, UPGRADE_POLICY_VALUE);
            req.next();
        }
    }

}
