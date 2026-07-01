/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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

package io.helidon.webclient.grpc.tests;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.helidon.common.configurable.Resource;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.testing.junit5.ServerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests server connection problems, such as a disappearing server.
 */
@ServerTest
class GrpcConnectionErrorTest extends GrpcBaseTest {
    private static final long TIMEOUT_SECONDS = 10;

    private final WebServer server;
    private final TrackingExecutor executor = new TrackingExecutor();
    private final GrpcClient grpcClient;

    private GrpcConnectionErrorTest(WebServer server) {
        this.server = server;
        Tls clientTls = Tls.builder()
                .trust(trust -> trust
                        .keystore(store -> store
                                .passphrase("password")
                                .trustStore(true)
                                .keystore(Resource.create("client.p12"))))
                .build();
        GrpcClientProtocolConfig config = GrpcClientProtocolConfig.builder()
                .heartbeatPeriod(Duration.ofSeconds(1))     // detects failure faster
                .build();
        this.grpcClient = GrpcClient.builder()
                .executor(executor)
                .tls(clientTls)
                .protocolConfig(config)
                .baseUri("https://localhost:" + server.port())
                .build();
    }

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void testListenerFailuresStillStopCallWorkers() throws InterruptedException {
        ClientCall<Strings.StringMessage, Strings.StringMessage> cancelledCall =
                grpcClient.channel().newCall(StringServiceGrpc.getJoinMethod(), CallOptions.DEFAULT);
        cancelledCall.start(new ClientCall.Listener<>() {
            @Override
            public void onClose(Status status, Metadata trailers) {
                throw new IllegalStateException("cancel listener failure");
            }
        }, new Metadata());

        IllegalStateException listenerFailure = assertThrows(IllegalStateException.class,
                                                              () -> cancelledCall.cancel("cancel", null));
        assertThat(listenerFailure.getMessage(), is("cancel listener failure"));
        assertEventually(() -> executor.activeTasks() == 0, TIMEOUT_SECONDS * 1000);

        CountDownLatch callClosed = new CountDownLatch(1);
        AtomicReference<Status> closeStatus = new AtomicReference<>();
        ClientCall<Strings.StringMessage, Strings.StringMessage> call =
                grpcClient.channel().newCall(StringServiceGrpc.getJoinMethod(), CallOptions.DEFAULT);
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onClose(Status status, Metadata trailers) {
                closeStatus.set(status);
                callClosed.countDown();
                throw new IllegalStateException("listener failure");
            }
        }, new Metadata());
        call.sendMessage(newStringMessage("hello"));

        server.stop();

        assertEventually(() -> callClosed.getCount() == 0 && executor.activeTasks() == 0,
                         TIMEOUT_SECONDS * 1000);
        assertThat(closeStatus.get().isOk(), is(false));
    }

    private static void assertEventually(Supplier<Boolean> predicate, long millis) throws InterruptedException {
        long start = System.currentTimeMillis();
        do {
            if (predicate.get()) {
                return;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() - start <= millis);
        fail("Predicate failed after " + millis + " milliseconds");
    }

    private static final class TrackingExecutor extends AbstractExecutorService {
        private final ExecutorService delegate = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
        private final AtomicInteger activeTasks = new AtomicInteger();

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(() -> {
                activeTasks.incrementAndGet();
                try {
                    command.run();
                } finally {
                    activeTasks.decrementAndGet();
                }
            });
        }

        int activeTasks() {
            return activeTasks.get();
        }
    }
}
