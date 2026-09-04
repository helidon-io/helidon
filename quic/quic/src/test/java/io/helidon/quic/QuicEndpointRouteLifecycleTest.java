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

package io.helidon.quic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class QuicEndpointRouteLifecycleTest {
    @Test
    void shouldObserveSuccessfulRemovalAfterCompletion() throws Exception {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
        assertThat(lifecycle.register(connection, 1), is(true));
        assertThat(lifecycle.beginRemoval(connection), is(true));

        lifecycle.completeRemoval();

        lifecycle.whenRemoved().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(lifecycle.owner(), is((QuicPacketReceiver) null));
    }

    @Test
    void shouldPreserveExceptionalRemovalObservedBeforeOrAfterCompletion() throws Exception {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        IllegalStateException earlyFailure = new IllegalStateException("observer installed before failure");
        QuicEndpointRouteLifecycle observedEarly = new QuicEndpointRouteLifecycle(connection);
        assertThat(observedEarly.register(connection, 1), is(true));
        CompletableFuture<Void> early = observedEarly.whenRemoved().toCompletableFuture();
        assertThat(observedEarly.beginRemoval(connection), is(true));
        observedEarly.completeRemovalExceptionally(earlyFailure);

        ExecutionException earlyException = assertThrows(ExecutionException.class,
                                                         () -> early.get(5, TimeUnit.SECONDS));
        assertThat(earlyException.getCause(), sameInstance(earlyFailure));

        IllegalStateException lateFailure = new IllegalStateException("observer installed after failure");
        QuicEndpointRouteLifecycle observedLate = new QuicEndpointRouteLifecycle(connection);
        assertThat(observedLate.register(connection, 1), is(true));
        assertThat(observedLate.beginRemoval(connection), is(true));
        observedLate.completeRemovalExceptionally(lateFailure);

        ExecutionException lateException = assertThrows(ExecutionException.class,
                                                        () -> observedLate.whenRemoved()
                                                                .toCompletableFuture()
                                                                .get(5, TimeUnit.SECONDS));
        assertThat(lateException.getCause(), sameInstance(lateFailure));
    }

    @Test
    void shouldCompleteMultipleObserversExactlyOnce() throws Exception {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
        assertThat(lifecycle.register(connection, 1), is(true));
        AtomicInteger completions = new AtomicInteger();
        CompletableFuture<Void> first = lifecycle.whenRemoved()
                .whenComplete((result, failure) -> completions.incrementAndGet())
                .toCompletableFuture();
        CompletableFuture<Void> second = lifecycle.whenRemoved()
                .whenComplete((result, failure) -> completions.incrementAndGet())
                .toCompletableFuture();

        assertThat(lifecycle.beginRemoval(connection), is(true));
        lifecycle.completeRemoval();

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        assertThat(completions.get(), is(2));
    }

    @Test
    void shouldKeepFirstCompetingRemovalCompletion() throws Exception {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        IllegalStateException failure = new IllegalStateException("competing route failure");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 100; i++) {
                QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
                assertThat(lifecycle.register(connection, 1), is(true));
                assertThat(lifecycle.beginRemoval(connection), is(true));
                AtomicInteger completions = new AtomicInteger();
                CompletableFuture<Void> removed = lifecycle.whenRemoved()
                        .whenComplete((result, throwable) -> completions.incrementAndGet())
                        .toCompletableFuture();
                CountDownLatch start = new CountDownLatch(1);
                Future<?> successful = executor.submit(() -> {
                    start.await();
                    lifecycle.completeRemoval();
                    return null;
                });
                Future<?> exceptional = executor.submit(() -> {
                    start.await();
                    lifecycle.completeRemovalExceptionally(failure);
                    return null;
                });

                start.countDown();
                successful.get(5, TimeUnit.SECONDS);
                exceptional.get(5, TimeUnit.SECONDS);
                try {
                    removed.get(5, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    assertThat(e.getCause(), sameInstance(failure));
                }
                assertThat(completions.get(), is(1));
            }
        }
    }

    @Test
    void shouldResolveConcurrentRegistrationAndPreRegistrationObservation() throws Exception {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 250; i++) {
                QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> registration = executor.submit(() -> {
                    start.await();
                    return lifecycle.register(connection, 1);
                });
                Future<CompletionStage<Void>> observation = executor.submit(() -> {
                    start.await();
                    return lifecycle.whenRemoved();
                });

                start.countDown();
                boolean registered = registration.get(5, TimeUnit.SECONDS);
                CompletableFuture<Void> removed = observation.get(5, TimeUnit.SECONDS).toCompletableFuture();
                if (registered) {
                    assertThat(lifecycle.beginRemoval(connection), is(true));
                    lifecycle.completeRemoval();
                } else {
                    assertThat(lifecycle.owner(), is((QuicPacketReceiver) null));
                }
                removed.get(5, TimeUnit.SECONDS);
                assertThat(lifecycle.register(connection, 1), is(false));
            }
        }
    }

    @Test
    void shouldResolveConcurrentObserverAndCompletionPublication() throws Exception {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 250; i++) {
                QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);
                assertThat(lifecycle.register(connection, 1), is(true));
                assertThat(lifecycle.beginRemoval(connection), is(true));
                IllegalStateException failure = i % 2 == 0
                        ? null
                        : new IllegalStateException("concurrent route failure " + i);
                CountDownLatch start = new CountDownLatch(1);
                Future<CompletionStage<Void>> observation = executor.submit(() -> {
                    start.await();
                    return lifecycle.whenRemoved();
                });
                Future<?> completion = executor.submit(() -> {
                    start.await();
                    if (failure == null) {
                        lifecycle.completeRemoval();
                    } else {
                        lifecycle.completeRemovalExceptionally(failure);
                    }
                    return null;
                });

                start.countDown();
                CompletableFuture<Void> removed = observation.get(5, TimeUnit.SECONDS).toCompletableFuture();
                completion.get(5, TimeUnit.SECONDS);
                if (failure == null) {
                    removed.get(5, TimeUnit.SECONDS);
                } else {
                    ExecutionException exception = assertThrows(ExecutionException.class,
                                                                () -> removed.get(5, TimeUnit.SECONDS));
                    assertThat(exception.getCause(), sameInstance(failure));
                }
            }
        }
    }

    @Test
    void shouldCompleteRemovalBeforeRegistration() {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);

        CompletableFuture<Void> removed = lifecycle.whenRemoved().toCompletableFuture();

        assertThat(removed.isDone(), is(true));
        assertThat(lifecycle.owner(), is((QuicPacketReceiver) null));
        assertThat(lifecycle.register(connection, 1), is(false));
    }

    @Test
    void shouldIgnorePredecessorRemovalAfterTransfer() {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        QuicPacketReceiver closing = mock(QuicPacketReceiver.class);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);

        assertThat(lifecycle.register(connection, 1), is(true));
        assertThat(lifecycle.transfer(connection, closing), is(true));
        assertThat(lifecycle.beginRemoval(connection), is(false));
        CompletableFuture<Void> removed = lifecycle.whenRemoved().toCompletableFuture();
        assertThat(removed.isDone(), is(false));

        assertThat(lifecycle.beginRemoval(closing), is(true));
        lifecycle.completeRemoval();

        assertThat(removed.isDone(), is(true));
        assertThat(lifecycle.owner(), is((QuicPacketReceiver) null));
        assertThat(lifecycle.beginRemoval(closing), is(false));
    }

    @Test
    void shouldRetainFinalConnectionIdRoute() {
        QuicPacketReceiver connection = mock(QuicPacketReceiver.class);
        QuicEndpointRouteLifecycle lifecycle = new QuicEndpointRouteLifecycle(connection);

        assertThat(lifecycle.register(connection, 2), is(true));
        assertThat(lifecycle.retireConnectionId(connection), is(true));
        assertThat(lifecycle.retireConnectionId(connection), is(false));

        lifecycle.connectionIdAdded(connection);

        assertThat(lifecycle.retireConnectionId(connection), is(true));
        assertThat(lifecycle.retireConnectionId(connection), is(false));
    }
}
