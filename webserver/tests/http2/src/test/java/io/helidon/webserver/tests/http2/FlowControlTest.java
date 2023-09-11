/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.tests.http2;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import io.helidon.common.reactive.BufferedEmittingPublisher;
import io.helidon.common.reactive.Multi;
import io.helidon.http.http2.WindowSize;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.WebServerConfig;
import io.helidon.webserver.http2.Http2Config;
import io.helidon.webserver.http2.Http2ConnectionSelector;
import io.helidon.webserver.http2.Http2Route;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.Method.GET;
import static io.helidon.http.Method.PUT;
import static java.lang.System.Logger.Level.DEBUG;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

@ServerTest
class FlowControlTest {

    private static final System.Logger LOGGER = System.getLogger(FlowControlTest.class.getName());
    private static final ExecutorService exec = Executors.newCachedThreadPool();
    private static final String DATA_10_K = "Helidon!!!".repeat(1_000);
    private static final String EXPECTED = DATA_10_K.repeat(5) + DATA_10_K.toUpperCase().repeat(5);
    private static final int TIMEOUT_SEC = 150;

    private static volatile CompletableFuture<Void> flowControlServerLatch = new CompletableFuture<>();
    private static volatile CompletableFuture<Void> flowControlClientLatch = new CompletableFuture<>();

    private final WebServer server;

    FlowControlTest(WebServer server) {
        this.server = server;
    }

    @SetUpServer
    static void setUpServer(WebServerConfig.Builder serverBuilder) {
        serverBuilder
                .addProtocol(Http2Config.builder()
                                     .initialWindowSize(WindowSize.DEFAULT_WIN_SIZE)
                                     .build())
                .addConnectionSelector(Http2ConnectionSelector.builder()
                                               .http2Config(Http2Config.builder()
                                                                    .initialWindowSize(WindowSize.DEFAULT_WIN_SIZE)
                                                                    .build())
                                               .build())
                .host("localhost")
                .routing(router -> router
                        .route(Http2Route.route(GET, "/", (req, res) -> res.send("OK")))
                        .route(Http2Route.route(PUT, "/flow-control", (req, res) -> {
                            StringBuilder sb = new StringBuilder();
                            AtomicLong cnt = new AtomicLong();
                            InputStream is = req.content().inputStream();
                            for (byte[] b = is.readNBytes(5_000);
                                    b.length != 0;
                                    b = is.readNBytes(5_000)) {
                                int lastLength = b.length;
                                long receivedData = cnt.updateAndGet(o -> o + lastLength);
                                if (receivedData > 0) {
                                    // Unblock client to assert sent data
                                    flowControlClientLatch.complete(null);
                                    // Block server, give client time to assert
                                    flowControlServerLatch.join();
                                }
                                sb.append(new String(b));
                            }
                            is.close();
                            res.send(sb.toString());

                        }))
                        .route(Http2Route.route(GET, "/flow-control", (req, res) -> {
                            res.send(EXPECTED);
                        }))
                );
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        exec.shutdown();
        if (!exec.awaitTermination(TIMEOUT_SEC, TimeUnit.SECONDS)) {
            exec.shutdownNow();
        }
    }

    @Test
    void flowControlWebClientInOut() throws ExecutionException, InterruptedException, TimeoutException {
        flowControlServerLatch = new CompletableFuture<>();
        flowControlClientLatch = new CompletableFuture<>();
        AtomicLong sentData = new AtomicLong();

        var client = Http2Client.builder()
                .protocolConfig(http2 -> http2.priorKnowledge(true)
                        .initialWindowSize(WindowSize.DEFAULT_WIN_SIZE)
                )
                .baseUri("http://localhost:" + server.port())
                .build();

        var req = client.method(PUT)
                .path("/flow-control");

        CompletableFuture<String> responded = new CompletableFuture<>();

        exec.submit(() -> {
            try (var res = req
                    .outputStream(
                            out -> {
                                for (int i = 0; i < 5; i++) {
                                    byte[] bytes = DATA_10_K.getBytes();
                                    LOGGER.log(DEBUG,
                                               () -> String.format("CL: Sending %d bytes", bytes.length));
                                    out.write(bytes);
                                    sentData.updateAndGet(o -> o + bytes.length);
                                }
                                for (int i = 0; i < 5; i++) {
                                    byte[] bytes = DATA_10_K.toUpperCase().getBytes();
                                    LOGGER.log(DEBUG,
                                               () -> String.format("CL: Sending %d bytes", bytes.length));
                                    out.write(bytes);
                                    sentData.updateAndGet(o -> o + bytes.length);
                                }
                                out.close();
                            }
                    )) {
                responded.complete(res.as(String.class));
            }
        });

        flowControlClientLatch.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        // Now client can't send more, because server didn't ask for it (Window update)
        // Wait a bit if more than allowed is sent
        Thread.sleep(300);
        // Depends on the win update strategy, can't be full 100k
        assertThat(sentData.get(), is(70_000L));
        // Let server ask for the rest of the data
        flowControlServerLatch.complete(null);
        String response = responded.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(sentData.get(), is(100_000L));
        assertThat(response, is(EXPECTED));
    }

    @Test
    void flowControlWebClientInbound() {
        var client = Http2Client.builder()
                .protocolConfig(http2 -> http2.priorKnowledge(true))
                .baseUri("http://localhost:" + server.port())
                .build();

        try (var res = client.method(GET)
                .path("/flow-control")
                .request()) {
            assertThat(res.entity().as(String.class), is(EXPECTED));
        }
    }

    @Test
    void flowControlHttpClientInOut() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        flowControlServerLatch = new CompletableFuture<>();
        flowControlClientLatch = new CompletableFuture<>();
        AtomicLong sentData = new AtomicLong();

        BufferedEmittingPublisher<ByteBuffer> publisher = BufferedEmittingPublisher.create();

        HttpClient cl = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        cl.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + server.port()))
                        .GET().build(), HttpResponse.BodyHandlers.discarding());

        CompletableFuture<String> responded = new CompletableFuture<>();

        exec.submit(() -> {
            try {
                HttpResponse<String> response =
                        cl.send(HttpRequest.newBuilder()
                                        .timeout(Duration.ofSeconds(5))
                                        .uri(URI.create("http://localhost:" + server.port() + "/flow-control"))
                                        .PUT(HttpRequest.BodyPublishers.fromPublisher(
                                                Multi.create(publisher)
                                                        .peek(bb -> sentData.updateAndGet(
                                                                o -> o + bb.array().length))
                                                        .log(Level.FINE)
                                        ))
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                responded.complete(response.body());
            } catch (IOException | InterruptedException e) {
                responded.completeExceptionally(e);
            }
        });

        for (int i = 0; i < 5; i++) {
            byte[] bytes = DATA_10_K.getBytes();
            LOGGER.log(DEBUG, () -> String.format("CL: Sending %d bytes", bytes.length));
            publisher.emit(ByteBuffer.wrap(bytes));
        }
        for (int i = 0; i < 5; i++) {
            byte[] bytes = DATA_10_K.toUpperCase().getBytes();
            LOGGER.log(DEBUG, () -> String.format("CL: Sending %d bytes", bytes.length));
            publisher.emit(ByteBuffer.wrap(bytes));
        }

        publisher.complete();

        flowControlClientLatch.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        // Now client can't send more, because server didn't ask for it (Window update)
        // Wait a bit if more than allowed is sent
        Thread.sleep(300);
        // Depends on the win update strategy, can't be full 100k
        assertThat(sentData.get(), lessThan(99_000L));
        // Let server ask for the rest of the data
        flowControlServerLatch.complete(null);
        String response = responded.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(sentData.get(), is(100_000L));
        assertThat(response, is(EXPECTED));
    }
}
