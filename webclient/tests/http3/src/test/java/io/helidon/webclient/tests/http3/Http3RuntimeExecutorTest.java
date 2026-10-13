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

package io.helidon.webclient.tests.http3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import io.helidon.http.Status;
import io.helidon.webclient.http3.Http3Client;
import io.helidon.webclient.http3.Http3ClientProtocolConfig;
import io.helidon.webclient.http3.Http3ClientResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.LOCAL_CLOSE;
import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.SUCCESS;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.webclient.tests.http3.Http3ClientTestSupport.strictClientBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

class Http3RuntimeExecutorTest {
    private static final String BODY = "0123456789abcdef".repeat(1_024);

    @Test
    @Timeout(60)
    void connectedClientsBorrowPoolWithoutClosingIt() throws Exception {
        CountDownLatch concurrentHandlers = new CountDownLatch(4);
        var runtimeExecutor = new ForkJoinPool(2, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
        try (TestEnvironment environment = TestEnvironment.createSharedListener(routing -> routing
                .get("/body", (_, response) -> response.send(BODY))
                .get("/concurrent", (_, response) -> {
                    concurrentHandlers.countDown();
                    try {
                        if (!concurrentHandlers.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Four concurrent HTTP/3 handlers did not arrive");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted awaiting concurrent HTTP/3 handlers", e);
                    }
                    response.send(BODY);
                }))) {
            var firstObserver = new RecordingTransportObserverService();
            var secondObserver = new RecordingTransportObserverService();
            Http3Client first = client(environment, runtimeExecutor, firstObserver);
            Http3Client second = null;
            try {
                second = client(environment, runtimeExecutor, secondObserver);
                assertResponse(first, "/body");
                assertResponse(second, "/body");

                try (var requests = Executors.newVirtualThreadPerTaskExecutor()) {
                    List<CompletableFuture<Void>> pending = new ArrayList<>();
                    for (Http3Client client : List.of(first, second)) {
                        for (int i = 0; i < 2; i++) {
                            pending.add(CompletableFuture.runAsync(() -> assertResponse(client, "/concurrent"), requests));
                        }
                    }
                    CompletableFuture.allOf(pending.toArray(CompletableFuture<?>[]::new)).get(15, TimeUnit.SECONDS);
                }
                assertThat(concurrentHandlers.getCount(), is(0L));
                assertThat("The explicitly supplied runtime pool executed QUIC work", runtimeExecutor.getPoolSize(),
                           greaterThan(0));

                first.closeResourceAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertClosedConnection(firstObserver, 3);
                assertThat(runtimeExecutor.isShutdown(), is(false));
                assertResponse(second, "/body");

                second.closeResourceAsync().toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertClosedConnection(secondObserver, 4);
                assertThat(runtimeExecutor.isShutdown(), is(false));
                assertThat(runtimeExecutor.submit(() -> 42).get(5, TimeUnit.SECONDS), is(42));
            } finally {
                try {
                    first.closeResource();
                } finally {
                    if (second != null) {
                        second.closeResource();
                    }
                }
            }
        } finally {
            runtimeExecutor.shutdownNow();
            assertThat("The test owner terminates its borrowed pool",
                       runtimeExecutor.awaitTermination(10, TimeUnit.SECONDS), is(true));
        }
        assertThat(runtimeExecutor.isTerminated(), is(true));
    }

    private static Http3Client client(TestEnvironment environment,
                                      ForkJoinPool runtimeExecutor,
                                      RecordingTransportObserverService observer) {
        return strictClientBuilder(Http3ClientProtocolConfig.builder().runtimeExecutor(runtimeExecutor))
                .baseUri(environment.baseUri())
                .tls(environment.clientTlsHttp3())
                .servicesDiscoverServices(false)
                .addService(observer)
                .build();
    }

    private static void assertResponse(Http3Client client, String path) {
        try (Http3ClientResponse response = client.get(path).request()) {
            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.protocolId(), is(Http3Client.PROTOCOL_ID));
            assertThat(response.as(String.class), is(BODY));
        }
    }

    private static void assertClosedConnection(RecordingTransportObserverService observer, int requests) {
        assertThat(observer.connections().size(), is(1));
        var connection = observer.connections().getFirst();
        assertThat(connection.handshake(), is(QUIC_TLS));
        assertThat(connection.handshakeOutcome().orTimeout(10, TimeUnit.SECONDS).join(), is(SUCCESS));
        assertThat(connection.protocols(), contains(PROTOCOL_HTTP_3));
        assertThat(connection.streams().size(), is(requests));
        assertThat(connection.streams().stream()
                           .map(stream -> stream.outcome().orTimeout(10, TimeUnit.SECONDS).join())
                           .toList(), everyItem(is(COMPLETED)));
        assertThat(connection.outcome().orTimeout(10, TimeUnit.SECONDS).join(), is(LOCAL_CLOSE));
        assertThat(observer.lifecyclesStarted(), is(1));
        assertThat(observer.lifecyclesStopped(), is(1));
    }
}
