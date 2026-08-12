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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class H2cShutdownTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HOLD_TIMEOUT = Duration.ofSeconds(30);

    private static final ConcurrentMap<Integer, CompletableFuture<Void>> connectionClosed = new ConcurrentHashMap<>();

    private static volatile CompletableFuture<Integer> heldRequestReceived = new CompletableFuture<>();
    private static volatile CompletableFuture<Void> releaseHeldRequest = new CompletableFuture<>();

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
        routing.route(Http2Route.route(Method.GET, "/held", (req, res) -> {
            int clientPort = req.remotePeer().port();
            heldRequestReceived.complete(clientPort);
            try {
                releaseHeldRequest.get(HOLD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding request A", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Failed while holding request A", e);
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
        heldRequestReceived = new CompletableFuture<>();
        releaseHeldRequest = new CompletableFuture<>();
    }

    @Test
    void closesDisplacedUpgradedConnection() {
        Http2Client http2Client = Http2Client.builder()
                .shareConnectionCache(false)
                .protocolConfig(Http2ClientProtocolConfig.builder().priorKnowledge(false))
                .connectTimeout(TIMEOUT)
                .baseUri("http://127.0.0.1:" + serverPort)
                .build();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<Integer> heldResponse = CompletableFuture.supplyAsync(() -> {
            try (var response = http2Client.get("/held")
                    .readTimeout(TIMEOUT)
                    .request()) {
                return Integer.parseInt(response.entity().as(String.class));
            }
        }, executor);
        try {
            int heldClientPort = await(heldRequestReceived, "request A to reach the server");
            int secondClientPort;
            try (var response = http2Client.get("/second")
                    .readTimeout(TIMEOUT)
                    .request()) {
                secondClientPort = Integer.parseInt(response.entity().as(String.class));
            }
            assertThat("request B should use a new connection while request A occupies the only stream",
                       secondClientPort,
                       not(is(heldClientPort)));

            releaseHeldRequest.complete(null);
            assertThat(await(heldResponse, "request A to complete"), is(heldClientPort));

            CompletableFuture<Void> heldConnectionClosed =
                    connectionClosed.computeIfAbsent(heldClientPort, ignored -> new CompletableFuture<>());
            CompletableFuture<Void> secondConnectionClosed =
                    connectionClosed.computeIfAbsent(secondClientPort, ignored -> new CompletableFuture<>());
            assertThat("request A connection should remain open before client shutdown",
                       heldConnectionClosed.isDone(),
                       is(false));
            assertThat("request B connection should remain open before client shutdown",
                       secondConnectionClosed.isDone(),
                       is(false));

            http2Client.closeResource();
            await(secondConnectionClosed, "connection from client port " + secondClientPort + " to close");
            await(heldConnectionClosed, "connection from client port " + heldClientPort + " to close");
        } finally {
            releaseHeldRequest.complete(null);
            http2Client.closeResource();
            heldResponse.cancel(true);
            executor.shutdownNow();
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
