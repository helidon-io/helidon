/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static io.helidon.webserver.BackpressureStrategy.AUTO_FLUSH;
import static io.helidon.webserver.BackpressureStrategy.LINEAR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class WaterMarkedBackpressureIT {

    private static final Logger LOGGER = Logger.getLogger(WaterMarkedBackpressureIT.class.getName());

    static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static ExecutorService executorService;

    @BeforeMethod
    void setUp() {
        executorService = Executors.newSingleThreadExecutor();
    }

    @AfterMethod
    void tearDown() {
        executorService.shutdown();
    }

    /**
     * Verifies default back-pressure strategy is {@link BackpressureStrategy#AUTO_FLUSH}
     * on all sockets.
     */
    @Test
    void defaultStrategy() {
        WebServer webServer = WebServer.builder().build();
        webServer.configuration()
                .sockets()
                .values()
                .forEach(sc -> assertThat(sc.backpressureStrategy(), is(AUTO_FLUSH)));
    }

    @Test
    void linear() {
        AtomicLong receivedSize = new AtomicLong(0);
        AtomicLong lastRequestedNumber = new AtomicLong(0);
        CompletableFuture<Void> firstBatchSent = new CompletableFuture<>();

        WebServer webServer = null;
        try {
            webServer = WebServer.builder()
                    .host("localhost")
                    .backpressureBufferSize(500)
                    .backpressureStrategy(LINEAR)
                    .routing(r -> r.get("/", (req, res) -> {
                        res.send(Multi.range(0, 1000)
                                .observeOn(executorService)
                                .peek(lastRequestedNumber::set)
                                // Never flush!
                                // 5 bytes per chunk
                                .map(l -> DataChunk.create(false, ByteBuffer.wrap((String.format("%05d", l)).getBytes())))
                                .onComplete(() -> firstBatchSent.complete(null))
                                .onCompleteResumeWith(Single.never())
                        );
                    }))
                    .build()
                    .start()
                    .await(TIMEOUT);

            WebClient.builder()
                    .baseUri("http://localhost:" + webServer.port())
                    .build()
                    .get()
                    .path("/")
                    .request()
                    .flatMap(WebClientResponse::content)
                    .forEach(chunk -> {
                        byte[] bytes = chunk.bytes();
                        receivedSize.addAndGet(bytes.length);
                        chunk.release();
                    })
                    .onError(t -> LOGGER.log(Level.SEVERE, t, () -> "Error calling the test server"));

            try {
                Single.create(firstBatchSent, true).await(Duration.ofSeconds(5));
            } catch (CompletionException e) {
                //expected
            }

            // When buffer fills up no other chunks are requested
            assertThat(lastRequestedNumber.get(), is(101L));
            // No data are flushed
            assertThat(receivedSize.get(), is(0L));
        } finally {
            if (webServer != null) {
                webServer.shutdown().await(TIMEOUT);
            }
            executorService.shutdown();
        }
    }

    @Test(invocationCount = 10)
    void autoFlush() {
        AtomicLong receivedSize = new AtomicLong(0);

        WebServer webServer = null;
        try {
            webServer = WebServer.builder()
                    .host("localhost")
                    .backpressureBufferSize(200)
                    .backpressureStrategy(AUTO_FLUSH)
                    .routing(r -> r.get("/", (req, res) -> {
                        res.send(Multi.range(0, 150)
                                .observeOn(executorService)
                                // Never flush!
                                // 5 bytes per chunk
                                .map(l -> String.format("%05d", l))
                                .map(String::getBytes)
                                .map(ByteBuffer::wrap)
                                .map(bb -> DataChunk.create(false, bb))
                                .onCompleteResumeWith(Single.never())
                        );
                    }))
                    .build()
                    .start()
                    .await(TIMEOUT);

            List<String> receivedData = new ArrayList<>(100);

            WebClient.builder()
                    .baseUri("http://localhost:" + webServer.port())
                    .build()
                    .get()
                    .path("/")
                    .request()
                    .flatMap(WebClientResponse::content)
                    .takeWhile(chunk -> {
                        byte[] bytes = chunk.bytes();
                        receivedSize.addAndGet(bytes.length);
                        String data = new String(bytes);
                        chunk.release();
                        receivedData.add(data);
                        return !data.equals("00100");
                    })
                    .ignoreElements()
                    .onErrorResumeWithSingle(t -> {
                        LOGGER.log(Level.WARNING, "Give a chance to assertions", t);
                        return Single.empty();
                    })
                    .await(TIMEOUT);

            assertThat(receivedData, contains(Multi.range(0, 101)
                    .map(l -> String.format("%05d", l)).collectList().await().toArray(String[]::new)));

            // Stochastic test as watermarking depends on Netty's flush callbacks
            assertThat(receivedSize.get(), greaterThan(499L));
        } finally {
            if (webServer != null) {
                webServer.shutdown().await(TIMEOUT);
            }
            executorService.shutdown();
        }
    }
}
