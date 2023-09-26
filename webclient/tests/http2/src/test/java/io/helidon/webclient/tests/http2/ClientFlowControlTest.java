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

package io.helidon.webclient.tests.http2;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import io.helidon.http.Method;
import io.helidon.http.http2.WindowSize;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientResponse;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.lang.System.Logger.Level.DEBUG;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ClientFlowControlTest {

    private static final System.Logger LOGGER = System.getLogger(ClientFlowControlTest.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DATA_10_K = "Helidon!!!".repeat(1_000);
    private static final byte[] BYTES_10_K = DATA_10_K.getBytes(StandardCharsets.UTF_8);
    private static final String DATA_10_K_UP_CASE = DATA_10_K.toUpperCase();
    private static final byte[] BYTES_10_K_UP_CASE = DATA_10_K_UP_CASE.getBytes(StandardCharsets.UTF_8);
    private static final String EXPECTED = DATA_10_K.repeat(5) + DATA_10_K_UP_CASE.repeat(5);
    private static final AtomicLong inboundServerSentData = new AtomicLong();
    private static final Vertx vertx = Vertx.vertx();
    private static final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private static HttpServer server;
    private static CompletableFuture<HttpServerRequest> outboundTestServerRequestRef = new CompletableFuture<>();
    private static Http2Client client;

    @BeforeAll
    static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
        LogConfig.configureRuntime();
        server = vertx.createHttpServer()
                .requestHandler(req -> {
                    switch (req.path()) {
                    case "/in" -> {
                        for (int i = 0; i < 5; i++) {
                            req.response().write(DATA_10_K)
                                    .andThen(event -> LOGGER.log(DEBUG, "Vertx server sent " +
                                            inboundServerSentData.addAndGet(BYTES_10_K.length)));
                        }
                        for (int i = 5; i < 10; i++) {
                            req.response().write(DATA_10_K.toUpperCase())
                                    .andThen(event -> LOGGER.log(DEBUG, "Vertx server sent " +
                                            inboundServerSentData.addAndGet(BYTES_10_K_UP_CASE.length)));
                        }
                        req.end();
                    }
                    case "/out" -> {
                        req.pause();
                        outboundTestServerRequestRef.complete(req);
                        req.handler(e -> {
                            req.response().write(e);
                        }).endHandler(event -> req.response().end());
                    }
                    }
                })
                .listen(-1)
                .toCompletionStage()
                .toCompletableFuture()
                .get(TIMEOUT.toMillis(), MILLISECONDS);

        client = Http2Client.builder()
                .baseUri("http://localhost:" + server.actualPort() + "/")
                .build();
    }

    @AfterAll
    static void afterAll() {
        server.close();
        vertx.close();
        exec.shutdown();
        try {
            if (!exec.awaitTermination(TIMEOUT.toMillis(), MILLISECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            exec.shutdownNow();
        }
    }

    @Test
    void clientOutbound() throws InterruptedException, ExecutionException, TimeoutException {
        outboundTestServerRequestRef = new CompletableFuture<>();

        //Chunk of the frame size * 2 so frame splitting is tested too
        int chunkSize = WindowSize.DEFAULT_MAX_FRAME_SIZE * 2;

        AtomicLong clientSentData = new AtomicLong();

        ByteArrayInputStream baos = new ByteArrayInputStream(EXPECTED.getBytes());
        CompletableFuture<String> clientFuture = CompletableFuture.supplyAsync(() -> {
            try (Http2ClientResponse res = client
                    .method(Method.PUT)
                    .path("/out")
                    .priorKnowledge(true)
                    .outputStream(out -> {
                        while (baos.available() > 0) {
                            byte[] chunk = baos.readNBytes(chunkSize);
                            clientSentData.addAndGet(chunk.length);
                            out.write(chunk);
                        }
                        out.close();
                    })) {
                return res.as(String.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, exec);
        HttpServerRequest req = outboundTestServerRequestRef.get(TIMEOUT.toMillis(), MILLISECONDS);
        req.fetch(1);
        awaitSize("Two client chunks should fit to default window size", clientSentData, chunkSize * 2L);
        // Vertx translates frames to chunks of default MAX_FRAME_SIZE 16384
        // 4 * 16384 depletes Vertx's windows size and force it to send update
        req.fetch(3);
        awaitSize("Three client chunks should have been sent now", clientSentData, chunkSize * 3L);
        req.fetch(Long.MAX_VALUE);
        awaitSize("Three client chunks should have been sent now",
                  clientSentData,
                  EXPECTED.getBytes(StandardCharsets.UTF_8).length);
        assertThat("Echo endpoint should have returned exactly same data",
                   clientFuture.get(TIMEOUT.toMillis(), MILLISECONDS),
                   is(EXPECTED));
    }

    @Test
    void clientInbound() throws InterruptedException {

        AtomicLong receivedByteSize = new AtomicLong();
        try (Http2ClientResponse res = client
                .method(Method.GET)
                .path("/in")
                .priorKnowledge(true)
                .request()) {

            final ByteBuffer bb = ByteBuffer.allocate(EXPECTED.getBytes().length);
            final InputStream is = res.inputStream();

            // Accept only 10k out of initial win size (64k)
            Semaphore semaphore = new Semaphore(10_000);
            CompletableFuture.runAsync(() -> {
                try {
                    int b = -1;
                    for (int i = 0; i < bb.capacity(); i++) {
                        semaphore.acquire();
                        b = is.read();
                        if (b == -1) {
                            break;
                        }
                        receivedByteSize.incrementAndGet();
                        bb.put((byte) b);
                    }
                    is.close();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, exec);

            awaitSize("Server should have accepted 70k, one 10k chunk over 64k(initial win size)",
                      inboundServerSentData, 70_000);
            // Give it time to make a mistake and send more if FC doesn't work correctly
            Thread.sleep(300);
            assertThat("", inboundServerSentData.get(), is(70_000L));

            // Unblock and accept the rest of the data
            semaphore.release(Integer.MAX_VALUE);
            awaitSize("Rest of the data 100k should have been received", receivedByteSize, EXPECTED.getBytes().length);

            String result = new String(bb.array(), StandardCharsets.UTF_8);
            assertThat("Echo endpoint should have returned exactly same data", result, is(EXPECTED));
        }
    }

    private void awaitSize(String msg, AtomicLong actualSize, long expected)
            throws InterruptedException {

        for (int i = 10; i < 5000 && actualSize.get() < expected; i *= 10) {
            Thread.sleep(i);
        }
        assertThat(msg, actualSize.get(), is(expected));
    }
}
