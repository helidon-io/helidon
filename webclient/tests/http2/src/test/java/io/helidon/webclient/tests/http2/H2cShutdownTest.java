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

package io.helidon.webclient.tests.http2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.http.Method;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.http2.Http2Upgrader;
import io.helidon.webserver.spi.ServerConnection;
import io.helidon.webserver.spi.ServerConnectionSelector;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class H2cShutdownTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HOLD_TIMEOUT = Duration.ofSeconds(30);

    private static final ConcurrentMap<Integer, CompletableFuture<Void>> connectionClosed = new ConcurrentHashMap<>();

    private static volatile CompletableFuture<Integer> heldRequestAReceived = new CompletableFuture<>();
    private static volatile CompletableFuture<Void> releaseHeldRequestA = new CompletableFuture<>();
    private static volatile CompletableFuture<Integer> heldRequestBReceived = new CompletableFuture<>();
    private static volatile CompletableFuture<Void> releaseHeldRequestB = new CompletableFuture<>();

    private final int serverPort;

    H2cShutdownTest(WebServer server) {
        serverPort = server.port();
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder server) {
        Http2Config http2Config = Http2Config.builder()
                .maxConcurrentStreams(1)
                .build();
        server.protocolsDiscoverServices(false)
                .addConnectionSelector(recordingSelector(Http2ConnectionSelector.builder()
                                                                 .http2Config(http2Config)
                                                                 .build()))
                .addConnectionSelector(recordingSelector(Http1ConnectionSelector.builder()
                                                                 .config(Http1Config.create())
                                                                 .addUpgrader(Http2Upgrader.create(http2Config))
                                                                 .build()));
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder routing) {
        routing.route(Http2Route.route(Method.GET, "/held-a", (req, res) -> {
            int clientPort = req.remotePeer().port();
            heldRequestAReceived.complete(clientPort);
            try {
                releaseHeldRequestA.get(HOLD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding request A", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Failed while holding request A", e);
            }
            res.send(String.valueOf(clientPort));
        }));
        routing.route(Http2Route.route(Method.GET, "/held-b", (req, res) -> {
            int clientPort = req.remotePeer().port();
            heldRequestBReceived.complete(clientPort);
            try {
                releaseHeldRequestB.get(HOLD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding request B", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Failed while holding request B", e);
            }
            res.send(String.valueOf(clientPort));
        }));
        routing.route(Http2Route.route(Method.GET,
                                       "/second",
                                       (req, res) -> res.send(String.valueOf(req.remotePeer().port()))));
    }

    @BeforeEach
    void resetRequests() {
        connectionClosed.clear();
        heldRequestAReceived = new CompletableFuture<>();
        releaseHeldRequestA = new CompletableFuture<>();
        heldRequestBReceived = new CompletableFuture<>();
        releaseHeldRequestB = new CompletableFuture<>();
    }

    @Test
    void closesDisplacedUpgradedConnection() {
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .connectionCacheSize(1)
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(false))
                .connectTimeout(TIMEOUT)
                .baseUri("http://127.0.0.1:" + serverPort)
                .build();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<Integer> heldResponse = requestAsync(http2Client, "/held-a", executor);
        try {
            int heldClientPort = await(heldRequestAReceived, "request A to reach the server");
            int secondClientPort;
            try (var response = http2Client.get("/second")
                    .readTimeout(TIMEOUT)
                    .request()) {
                secondClientPort = Integer.parseInt(response.entity().as(String.class));
            }
            assertThat("request B should use a new connection while request A occupies the only stream",
                       secondClientPort,
                       not(is(heldClientPort)));

            CompletableFuture<Void> heldConnectionClosed =
                    connectionClosed.computeIfAbsent(heldClientPort, ignored -> new CompletableFuture<>());
            CompletableFuture<Void> secondConnectionClosed =
                    connectionClosed.computeIfAbsent(secondClientPort, ignored -> new CompletableFuture<>());
            assertThat("request A connection should remain open while its stream is held",
                       heldConnectionClosed.isDone(),
                       is(false));

            releaseHeldRequestA.complete(null);
            assertThat(await(heldResponse, "request A to complete"), is(heldClientPort));
            await(heldConnectionClosed, "displaced connection from client port " + heldClientPort + " to close");

            int reusedClientPort;
            try (var response = http2Client.get("/second")
                    .readTimeout(TIMEOUT)
                    .request()) {
                reusedClientPort = Integer.parseInt(response.entity().as(String.class));
            }
            assertThat("request B connection should remain reusable after request A connection is retired",
                       reusedClientPort,
                       is(secondClientPort));
            assertThat("request B connection should remain open before client shutdown",
                       secondConnectionClosed.isDone(),
                       is(false));

            http2Client.closeResource();
            await(secondConnectionClosed, "connection from client port " + secondClientPort + " to close");
        } finally {
            releaseHeldRequestA.complete(null);
            http2Client.closeResource();
            heldResponse.cancel(true);
            executor.shutdownNow();
        }
    }

    @Test
    void reusesAvailableConnectionWithinConfiguredCacheSize() {
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .connectionCacheSize(2)
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(false))
                .connectTimeout(TIMEOUT)
                .baseUri("http://127.0.0.1:" + serverPort)
                .build();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<Integer> heldResponseA = requestAsync(http2Client, "/held-a", executor);
        CompletableFuture<Integer> heldResponseB = null;
        try {
            int clientPortA = await(heldRequestAReceived, "request A to reach the server");
            heldResponseB = requestAsync(http2Client, "/held-b", executor);
            int clientPortB = await(heldRequestBReceived, "request B to reach the server");
            assertThat("request B should use a new connection while request A occupies the only stream",
                       clientPortB,
                       not(is(clientPortA)));

            releaseHeldRequestA.complete(null);
            assertThat(await(heldResponseA, "request A to complete"), is(clientPortA));

            int clientPortC;
            try (var response = http2Client.get("/second")
                    .readTimeout(TIMEOUT)
                    .request()) {
                clientPortC = Integer.parseInt(response.entity().as(String.class));
            }
            assertThat("request C should reuse request A connection while request B occupies the other connection",
                       clientPortC,
                       is(clientPortA));

            releaseHeldRequestB.complete(null);
            assertThat(await(heldResponseB, "request B to complete"), is(clientPortB));

            CompletableFuture<Void> connectionAClosed =
                    connectionClosed.computeIfAbsent(clientPortA, ignored -> new CompletableFuture<>());
            CompletableFuture<Void> connectionBClosed =
                    connectionClosed.computeIfAbsent(clientPortB, ignored -> new CompletableFuture<>());
            assertThat("request A connection should remain open before client shutdown",
                       connectionAClosed.isDone(),
                       is(false));
            assertThat("request B connection should remain open before client shutdown",
                       connectionBClosed.isDone(),
                       is(false));

            http2Client.closeResource();
            await(connectionAClosed, "connection from client port " + clientPortA + " to close");
            await(connectionBClosed, "connection from client port " + clientPortB + " to close");
        } finally {
            releaseHeldRequestA.complete(null);
            releaseHeldRequestB.complete(null);
            http2Client.closeResource();
            heldResponseA.cancel(true);
            if (heldResponseB != null) {
                heldResponseB.cancel(true);
            }
            executor.shutdownNow();
        }
    }

    @Test
    void closesUpgradedConnectionWaitingForInitialSettings() throws Exception {
        byte[] clientPreface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] headerTerminator = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            serverSocket.setSoTimeout((int) TIMEOUT.toMillis());
            Http2Client http2Client = Http2Client.builder()
                    .shareConnectionCache(false)
                    .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(false))
                    .connectTimeout(TIMEOUT)
                    .baseUri("http://127.0.0.1:" + serverSocket.getLocalPort())
                    .build();
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            CompletableFuture<Void> request = CompletableFuture.runAsync(() -> {
                try (var response = http2Client.get("/")
                        .readTimeout(HOLD_TIMEOUT)
                        .request()) {
                    response.entity().as(String.class);
                }
            }, executor);
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout((int) TIMEOUT.toMillis());
                var input = socket.getInputStream();
                var requestBytes = new ByteArrayOutputStream();
                int terminatorIndex = 0;
                while (terminatorIndex < headerTerminator.length) {
                    int next = input.read();
                    if (next == -1) {
                        fail("Client closed before sending the h2c upgrade request");
                    }
                    requestBytes.write(next);
                    if (next == headerTerminator[terminatorIndex]) {
                        terminatorIndex++;
                    } else {
                        terminatorIndex = next == headerTerminator[0] ? 1 : 0;
                    }
                }
                String requestHeaders = requestBytes.toString(StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
                assertThat(requestHeaders, containsString("\r\nupgrade: h2c\r\n"));
                assertThat(requestHeaders, containsString("\r\nhttp2-settings:"));

                var output = socket.getOutputStream();
                output.write(("HTTP/1.1 101 Switching Protocols\r\n"
                                      + "Connection: Upgrade\r\n"
                                      + "Upgrade: h2c\r\n"
                                      + "\r\n")
                                     .getBytes(StandardCharsets.US_ASCII));
                output.flush();
                assertArrayEquals(clientPreface, input.readNBytes(clientPreface.length));

                http2Client.closeResource();
                while (input.read() != -1) {
                    // Drain any final HTTP/2 frames until the client closes its socket.
                }
                try {
                    output.write(new byte[] {0, 0, 0, 4, 0, 0, 0, 0, 0});
                    output.flush();
                } catch (IOException _) {
                    // The client may close before the peer sends its initial settings.
                }

                ExecutionException requestFailure = assertThrows(ExecutionException.class,
                                                                  () -> request.get(TIMEOUT.toMillis(),
                                                                                    TimeUnit.MILLISECONDS));
                assertThat(requestFailure.getCause(), notNullValue());
            } finally {
                http2Client.closeResource();
                request.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    private static ServerConnectionSelector recordingSelector(ServerConnectionSelector delegate) {
        return new ServerConnectionSelector() {
            @Override
            public int bytesToIdentifyConnection() {
                return delegate.bytesToIdentifyConnection();
            }

            @Override
            public Support supports(BufferData data) {
                return delegate.supports(data);
            }

            @Override
            public Set<String> supportedApplicationProtocols() {
                return delegate.supportedApplicationProtocols();
            }

            @Override
            public ServerConnection connection(ConnectionContext ctx) {
                ServerConnection connection = delegate.connection(ctx);
                int clientPort = ctx.remotePeer().port();
                return new ServerConnection() {
                    @Override
                    public void handle(Limit limit) throws InterruptedException {
                        try {
                            connection.handle(limit);
                        } finally {
                            connectionClosed.computeIfAbsent(clientPort, ignored -> new CompletableFuture<>())
                                    .complete(null);
                        }
                    }

                    @Override
                    public Duration idleTime() {
                        return connection.idleTime();
                    }

                    @Override
                    public void close(boolean interrupt) {
                        connection.close(interrupt);
                    }
                };
            }
        };
    }

    private static CompletableFuture<Integer> requestAsync(Http2Client client, String path, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try (var response = client.get(path)
                    .readTimeout(TIMEOUT)
                    .request()) {
                return Integer.parseInt(response.entity().as(String.class));
            }
        }, executor);
    }

    private static <T> T await(CompletableFuture<T> future, String description) {
        try {
            return future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("Interrupted while waiting for " + description, e);
        } catch (ExecutionException | TimeoutException e) {
            return fail("Failed while waiting for " + description, e);
        }
    }
}
