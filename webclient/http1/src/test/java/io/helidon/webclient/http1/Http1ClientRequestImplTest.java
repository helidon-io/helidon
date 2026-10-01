/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.webclient.http1;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientConnection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

@Timeout(15)
class Http1ClientRequestImplTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Test
    void testUpgradeHeaders() {
        Header req = HeaderValues.create("Upgrade", "websocket");
        Header res = HeaderValues.create("Upgrade", "WebSocket");       // camel case
        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @Test
    void testUpgradeHeadersMany() {
        Header req = HeaderValues.create("Upgrade", "websocket", "h2c");
        Header res = HeaderValues.create("Upgrade", "WebSocket");       // camel case
        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @Test
    void testUpgradeHeadersFail() {
        Header req = HeaderValues.create("Upgrade", "websocket", "http3");
        Header res = HeaderValues.create("Upgrade", "h2c");
        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(false));
    }

    @Test
    void acceptsCommaSeparatedProtocols() {
        Header req = HeaderValues.create("Upgrade", "websocket, h2c");
        Header res = HeaderValues.create("Upgrade", " WebSocket , h2c ");

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @Test
    void acceptsProtocolsAcrossMultipleFieldValues() {
        Header req = HeaderValues.create("Upgrade", "websocket, h2c", "example/1");
        Header res = HeaderValues.create("Upgrade", "WebSocket", "example/1");

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @Test
    void rejectsUnrequestedProtocolAfterAcceptedProtocol() {
        Header req = HeaderValues.create("Upgrade", "websocket");
        Header res = HeaderValues.create("Upgrade", "websocket", "unknown");

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(false));
    }

    @Test
    void rejectsEmptySelection() {
        Header req = HeaderValues.create("Upgrade", "websocket");
        Header res = HeaderValues.create("Upgrade", "");

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(false));
    }

    @Test
    void acceptsTlsWithRetainedHttpAcrossMultipleFieldValues() {
        Header req = HeaderValues.create("Upgrade", "TLS/1.2");
        Header res = HeaderValues.create("Upgrade", "TLS/1.2", "HTTP/1.1");

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @Test
    void acceptsTlsWithCommaSeparatedRetainedHttp() {
        Header req = HeaderValues.create("Upgrade", "TLS/1.2");
        Header res = HeaderValues.create("Upgrade", "TLS/1.2, HTTP/1.1");

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.1", "TLS/1.3, HTTP/1.1", "TLS/1.2, unknown", "TLS/1.2, HTTP/2.0"})
    void rejectsTlsSelectionWithoutAnOfferedUpgradeOrWithAnUnofferedProtocol(String selection) {
        Header req = HeaderValues.create("Upgrade", "TLS/1.2");
        Header res = HeaderValues.create("Upgrade", selection);

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(false));
    }

    @ParameterizedTest(name = "offered={0}, selected={1}")
    @CsvSource(delimiter = '|', value = {
            "h2c | h2c, HTTP/1.1",
            "websocket | websocket, HTTP/1.1",
            "TLS/1.2, h2c | h2c, HTTP/1.1",
            "TLS/1.2, websocket | websocket, HTTP/1.1",
            "TLS/1.2 | HTTP/1.1, TLS/1.2",
            "TLS/1.2, h2c | TLS/1.2, h2c, HTTP/1.1",
            "TLS/1.2 | TLS/1.2, HTTP/1.1, HTTP/1.1",
            "TLS/1.2 | TLS/1.2, TLS/1.2, HTTP/1.1",
            "TLSX/1.2 | TLSX/1.2, HTTP/1.1",
            "TLS/12.3 | TLS/12.3, HTTP/1.1",
            "TLS/1.23 | TLS/1.23, HTTP/1.1",
            "TLS/1.x | TLS/1.x, HTTP/1.1"
    })
    void rejectsUnofferedHttpOutsideAnOrderedTlsStack(String offered, String selected) {
        Header req = HeaderValues.create("Upgrade", offered);
        Header res = HeaderValues.create("Upgrade", selected);

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(false));
    }

    @ParameterizedTest(name = "offered={0}, selected={1}")
    @CsvSource(delimiter = '|', value = {
            "HTTP/1.1 | http/1.1",
            "websocket, HTTP/1.1 | WebSocket, http/1.1",
            "TLS/1.2, HTTP/1.1 | HTTP/1.1, TLS/1.2"
    })
    void explicitlyOfferedHttpUsesOrdinaryMatching(String offered, String selected) {
        Header req = HeaderValues.create("Upgrade", offered);
        Header res = HeaderValues.create("Upgrade", selected);

        assertThat(Http1ClientRequestImpl.upgradeSuccessful(req, res), is(true));
    }

    @ParameterizedTest(name = "commaSeparated={0}")
    @ValueSource(booleans = {false, true})
    void tlsUpgradeWithRetainedHttpHandsOffConnection(boolean commaSeparated) throws Exception {
        try (UpgradeServer server = new UpgradeServer(commaSeparated)) {
            Http1Client client = Http1Client.builder()
                    .baseUri("http://127.0.0.1:" + server.listener.getLocalPort())
                    .readTimeout(TIMEOUT)
                    .build();
            try {
                UpgradeResponse upgrade = client.get("/upgrade")
                        .header(HeaderNames.CONNECTION, "Upgrade")
                        .upgrade("TLS/1.2");
                try {
                    assertThat(upgrade.response().status(), is(Status.SWITCHING_PROTOCOLS_101));
                    assertThat("selected TLS with retained HTTP/1.1 must complete the upgrade", upgrade.isUpgraded(), is(true));
                    ClientConnection connection = upgrade.connection();
                    upgrade.response().close();
                    // The caller owns the upgraded transport; this fixture does not perform a TLS handshake.
                    connection.writer().writeNow(BufferData.create(new byte[] {42}));
                } finally {
                    if (upgrade.isUpgraded()) {
                        upgrade.connection().closeResource();
                    }
                    upgrade.response().close();
                }
                server.await();
            } finally {
                client.closeResource();
            }
        }
    }

    private static String readHead(InputStream input) throws IOException {
        StringBuilder head = new StringBuilder();
        while (head.length() < 16384) {
            int next = input.read();
            if (next == -1) {
                throw new IOException("Connection closed before request headers completed");
            }
            head.append((char) next);
            if (head.toString().endsWith("\r\n\r\n")) {
                return head.toString();
            }
        }
        throw new IOException("Request headers exceed test limit");
    }

    private static final class UpgradeServer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Future<?> task;

        private UpgradeServer(boolean commaSeparated) throws IOException {
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout((int) TIMEOUT.toMillis());
            task = executor.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout((int) TIMEOUT.toMillis());
                    String head = readHead(socket.getInputStream());
                    assertThat(head, startsWith("GET /upgrade HTTP/1.1\r\n"));
                    assertThat(head, containsString("Upgrade: TLS/1.2\r\n"));
                    assertThat(head, containsString("Connection: Upgrade\r\n"));
                    String selection = commaSeparated
                            ? "Upgrade: TLS/1.2, HTTP/1.1\r\n"
                            : "Upgrade: TLS/1.2\r\nUpgrade: HTTP/1.1\r\n";
                    socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\n"
                            + selection + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    assertThat("upgraded connection must remain writable after closing the HTTP response",
                               socket.getInputStream().read(), is(42));
                    assertThat("caller must close the upgraded connection", socket.getInputStream().read(), is(-1));
                }
                return null;
            });
        }

        void await() throws Exception {
            task.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            listener.close();
            executor.shutdownNow();
            executor.close();
        }
    }
}
