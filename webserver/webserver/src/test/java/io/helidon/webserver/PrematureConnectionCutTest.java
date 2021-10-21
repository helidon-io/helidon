/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class PrematureConnectionCutTest {

    private static final Logger LOGGER = Logger.getLogger(PrematureConnectionCutTest.class.getName());
    private static final Duration TIMEOUT = Duration.of(15, ChronoUnit.SECONDS);
    private static final int CALL_NUM = 5;

    @Test
    void cutConnectionBefore100Continue() {
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        TestAsyncRunner asyncRunner = null;
        WebServer webServer = null;
        try {
            final TestAsyncRunner async = asyncRunner = new TestAsyncRunner(CALL_NUM);
            webServer = WebServer.builder()
                    .port(0)
                    .routing(Routing.builder()
                            .post((req, res) -> req.content()
                                    .as(InputStream.class)
                                    .thenAccept(is -> async.run(() -> {
                                        try {
                                            is.readAllBytes(); // this is where thread could get blocked indefinitely
                                        } catch (IOException e) {
                                            exceptions.add(e);
                                        } finally {
                                            res.send();
                                        }
                                    }))
                            )
                    )
                    .build()
                    .start()
                    .await(TIMEOUT);

            WebClient webClient = WebClient.builder()
                    .baseUri("http://localhost:" + webServer.port())
                    .build();

            for (int i = 0; i < CALL_NUM; i++) {
                //Sending request with fake content length, but not continuing after 100 code
                incomplete100Call(webClient);
            }

            // Wait for all threads to finish
            asyncRunner.await();

            assertThat("All threads didn't finish in time, probably deadlocked.", asyncRunner.finishedThreads.get(), is(CALL_NUM));
            assertThat("All exceptions were not delivered, when connection closed.", exceptions.size(), is(CALL_NUM));

            exceptions.forEach(e -> {
                assertThat(e.getCause(), anyOf(
                        // One of the following exceptions is expected:
                        // IOE: Connection reset by peer - on MacOS
                        // ISE: Channel closed prematurely by other side! - on Linux
                        instanceOf(IllegalStateException.class),
                        instanceOf(IOException.class))
                );
            });
        } finally {
            Optional.ofNullable(webServer)
                    .ifPresent(ws -> ws.shutdown().await(TIMEOUT));
            Optional.ofNullable(asyncRunner)
                    .ifPresent(TestAsyncRunner::shutdown);
        }
    }

    /**
     * Force Netty to avoid auto close and wait for content with fake content-length
     */
    private void incomplete100Call(WebClient webClient) {
        WebClientResponse response = null;
        try {
            response = webClient
                    .post()
                    .headers(h -> {
                        h.add("Expect", "100-continue");
                        h.contentLength(100);
                        return h;
                    })
                    .submit()
                    .await(TIMEOUT);
        } finally {
            Objects.requireNonNull(response).close();
        }
    }

    private static class TestAsyncRunner {
        final CountDownLatch latch;
        AtomicInteger finishedThreads = new AtomicInteger();
        ExecutorService exec = Executors.newCachedThreadPool();

        public TestAsyncRunner(int calls) {
            this.latch = new CountDownLatch(calls);
        }

        void run(Runnable r) {
            exec.submit(() -> {
                r.run();
                finishedThreads.incrementAndGet();
                latch.countDown();
            });
        }

        void await() {
            try {
                if (!latch.await(TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
                    LOGGER.severe("Latch timeout " + TIMEOUT);
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Latch interrupted " + TIMEOUT, e);
            }
        }

        void shutdown() {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(300, TimeUnit.MILLISECONDS)) {
                    exec.shutdownNow();
                }
            } catch (InterruptedException e) {
                exec.shutdownNow();
            }
        }
    }
}

