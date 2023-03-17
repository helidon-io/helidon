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
package io.helidon.nima.http2.webclient;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import io.helidon.nima.http2.webserver.Http2Route;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpServer;
import io.helidon.nima.webserver.WebServer;

import static io.helidon.common.http.Http.Method.PUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@ServerTest
public class FlowControlTest {

    private static final System.Logger LOGGER = System.getLogger(FlowControlTest.class.getName());
    private static volatile CompletableFuture<Void> flowControlServerLatch = new CompletableFuture<>();
    private static volatile CompletableFuture<Void> flowControlClientLatch = new CompletableFuture<>();
    private static final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private static final int TIMEOUT_SEC = 15;
    private final WebServer server;

    @SetUpServer
    static void setUpServer(WebServer.Builder serverBuilder) {
        serverBuilder
                .defaultSocket(builder -> builder.port(-1)
                        .host("localhost")
                )
                .routing(router -> router
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
                );
    }

    FlowControlTest(WebServer server) {
        this.server = server;
    }

    @Test
    void flowControl() throws ExecutionException, InterruptedException, TimeoutException {
        flowControlServerLatch = new CompletableFuture<>();
        flowControlClientLatch = new CompletableFuture<>();
        AtomicLong sentData = new AtomicLong();

        var client = Http2Client.builder()
                .priorKnowledge(true)
                .baseUri("http://localhost:" + server.port())
                .build();

        String data10k = "Helidon!!!".repeat(1_000);

        var req = client.method(PUT)
                .path("/flow-control");

        CompletableFuture<String> responded = new CompletableFuture<>();

        exec.submit(() -> {
            try (var res = req
                    .outputStream(
                            out -> {
                                for (int i = 0; i < 5; i++) {
                                    byte[] bytes = data10k.getBytes();
                                    LOGGER.log(System.Logger.Level.INFO, () -> String.format("CL IF: Sending %d bytes", bytes.length));
                                    out.write(bytes);
                                    sentData.updateAndGet(o -> o + bytes.length);
                                }
                                for (int i = 0; i < 5; i++) {
                                    byte[] bytes = data10k.toUpperCase().getBytes();
                                    LOGGER.log(System.Logger.Level.INFO, () -> String.format("CL IF: Sending %d bytes", bytes.length));
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
        Thread.sleep(150);
        assertThat(sentData.get(), is(70_000L));
        // Let server ask for the rest of the data
        flowControlServerLatch.complete(null);
        String response = responded.get(TIMEOUT_SEC, TimeUnit.SECONDS);
        assertThat(sentData.get(), is(100_000L));
        assertThat(response, is(data10k.repeat(5) + data10k.toUpperCase().repeat(5)));
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        exec.shutdown();
        if (!exec.awaitTermination(TIMEOUT_SEC, TimeUnit.SECONDS)) {
            exec.shutdownNow();
        }
    }
}
