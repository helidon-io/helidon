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

package io.helidon.metrics.publishers.otlp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.http.HeaderNames;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

class TestOtlpTimeout {
    @Test
    void shutdownCancelsStalledHttpAndClosesConnection() throws Exception {
        try (StallingCollector collector = new StallingCollector()) {
            var publisher = OtlpPublisher.builder()
                    .endpoint(URI.create("http://127.0.0.1:" + collector.port() + "/v1/metrics"))
                    .interval(Duration.ofDays(1))
                    .timeout(Duration.ofMillis(500))
                    .build();
            long started;
            var factory = HelidonMetricsFactory.builder()
                    .metricsConfig(MetricsConfig.builder().addPublisher(publisher))
                    .build();
            try {
                factory.globalRegistry().getOrCreate(factory.counterBuilder("shutdown.counter")).increment();
                started = System.nanoTime();
            } finally {
                factory.close();
            }

            assertThat("Stalled HTTP cannot hold registry shutdown indefinitely",
                       Duration.ofNanos(System.nanoTime() - started), lessThan(Duration.ofSeconds(5)));
            assertThat("The timed-out publishing connection is closed at the collector",
                       collector.bytesBeforeEof.get(5, TimeUnit.SECONDS), greaterThan(0));
        }
    }

    @Test
    void periodicExportResumesAfterHttpTimeout() throws Exception {
        var releaseStalledRequest = new CountDownLatch(1);
        var successfulRequest = new CountDownLatch(1);
        var requestCount = new AtomicInteger();
        var server = WebServer.builder()
                .port(0)
                .shutdownHook(false)
                .routing(routing -> routing.post("/v1/metrics", (request, response) -> {
                    request.content().as(byte[].class);
                    if (requestCount.incrementAndGet() == 1) {
                        try {
                            releaseStalledRequest.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException _) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        successfulRequest.countDown();
                    }
                    response.header(HeaderNames.CONTENT_TYPE, "application/json");
                    response.send("{}");
                }))
                .build().start();
        try {
            var publisher = OtlpPublisher.builder()
                    .endpoint(URI.create("http://localhost:" + server.port() + "/v1/metrics"))
                    .interval(Duration.ofMillis(50))
                    .timeout(Duration.ofMillis(300))
                    .build();
            var factory = HelidonMetricsFactory.builder()
                    .metricsConfig(MetricsConfig.builder().addPublisher(publisher))
                    .build();
            try {
                factory.globalRegistry().getOrCreate(factory.counterBuilder("timeout.counter")).increment();

                assertThat("A timed-out export must not terminate periodic publishing",
                           successfulRequest.await(5, TimeUnit.SECONDS), is(true));
            } finally {
                factory.close();
            }
        } finally {
            releaseStalledRequest.countDown();
            server.stop();
        }
    }

    private static final class StallingCollector implements AutoCloseable {
        private final CompletableFuture<Integer> bytesBeforeEof = new CompletableFuture<>();
        private final AtomicReference<Socket> connection = new AtomicReference<>();
        private final ServerSocket listener;
        private final Thread worker;

        private StallingCollector() throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            worker = Thread.ofVirtual().start(() -> {
                try (Socket socket = listener.accept()) {
                    connection.set(socket);
                    socket.setSoTimeout(5000);
                    byte[] buffer = new byte[1024];
                    int total = 0;
                    int count;
                    while ((count = socket.getInputStream().read(buffer)) >= 0) {
                        total += count;
                    }
                    bytesBeforeEof.complete(total);
                } catch (IOException e) {
                    bytesBeforeEof.completeExceptionally(e);
                }
            });
        }

        @Override
        public void close() throws Exception {
            listener.close();
            Socket socket = connection.get();
            if (socket != null) {
                socket.close();
            }
            worker.interrupt();
            assertThat("Collector worker terminated", worker.join(Duration.ofSeconds(5)), is(true));
        }

        private int port() {
            return listener.getLocalPort();
        }
    }
}
