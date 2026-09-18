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

package io.helidon.service.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.types.ResolvedType;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventManagerImplTest {
    private static final ResolvedType EVENT_TYPE = ResolvedType.create(String.class);
    private static final long TIMEOUT_SECONDS = 5;

    @Test
    void reentrantRegistrationDuringEmit() throws Exception {
        assertReentrantRegistration(false);
    }

    @Test
    void reentrantRegistrationDuringEmitAsync() throws Exception {
        assertReentrantRegistration(true);
    }

    @Test
    void reentrantAsyncRegistrationDuringEmit() throws Exception {
        assertReentrantAsyncRegistration(false);
    }

    @Test
    void reentrantAsyncRegistrationDuringEmitAsync() throws Exception {
        assertReentrantAsyncRegistration(true);
    }

    @Test
    void queuedEmissionRetainsListenerSnapshot() throws Exception {
        var dispatchAllowed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> await(dispatchAllowed));
            var manager = new EventManagerImpl(List::of, Optional.of(executor));
            List<String> delivered = new ArrayList<>();
            manager.<String>register(EVENT_TYPE, event -> delivered.add("original:" + event), Set.of());

            try {
                var emission = manager.emitAsync(EVENT_TYPE, "first", Set.of());
                manager.<String>register(EVENT_TYPE, event -> delivered.add("late:" + event), Set.of());
                dispatchAllowed.countDown();
                assertThat(emission.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS), is("first"));
                assertThat(delivered, contains("original:first"));

                manager.emitAsync(EVENT_TYPE, "second", Set.of())
                        .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThat(delivered, contains("original:first", "original:second", "late:second"));
            } finally {
                dispatchAllowed.countDown();
            }
        }
    }

    @Test
    void ownedExecutorShutdownAllowsOutstandingTasksToFinish() throws Exception {
        var dispatchStarted = new CountDownLatch(1);
        var dispatchAllowed = new CountDownLatch(1);
        var manager = new EventManagerImpl(List::of, Optional.empty());
        manager.register(EVENT_TYPE, _ -> {
            dispatchStarted.countDown();
            await(dispatchAllowed);
        }, Set.of());

        var emission = manager.emitAsync(EVENT_TYPE, "first", Set.of());
        try {
            assertThat("dispatch started", dispatchStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(true));
            manager.shutdown();
            assertThat("outstanding event is still running", emission.toCompletableFuture().isDone(), is(false));
            assertThrows(RejectedExecutionException.class, () -> manager.emitAsync(EVENT_TYPE, "second", Set.of()));
        } finally {
            dispatchAllowed.countDown();
            manager.shutdown();
        }
        assertThat(emission.toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS), is("first"));
    }

    @Test
    void ownedExecutorRejectsAsyncObserversAfterShutdown() {
        var registryManager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                   .discoverServices(false)
                                                                   .discoverServicesFromServiceLoader(false)
                                                                   .interceptionEnabled(false)
                                                                   .addServiceDescriptor(EventManagerImpl__ServiceDescriptor.INSTANCE)
                                                                   .build());
        try {
            var manager = registryManager.registry().get(EventManager.class);
            manager.registerAsync(EVENT_TYPE, _ -> { }, Set.of());

            registryManager.shutdown();

            assertThrows(RejectedExecutionException.class, () -> manager.emit(EVENT_TYPE, "event", Set.of()));
        } finally {
            registryManager.shutdown();
        }
    }

    @Test
    void suppliedExecutorRemainsUsableAfterShutdown() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var manager = new EventManagerImpl(List::of, Optional.of(executor));
            List<String> delivered = new ArrayList<>();
            manager.<String>register(EVENT_TYPE, delivered::add, Set.of());

            manager.shutdown();

            assertThat("supplied executor remains open", executor.isShutdown(), is(false));
            assertThat(manager.emitAsync(EVENT_TYPE, "event", Set.of())
                               .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS), is("event"));
            assertThat(delivered, contains("event"));
        }
    }

    private static void assertReentrantRegistration(boolean async) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var manager = new EventManagerImpl(List::of, Optional.of(executor));
            List<String> delivered = new ArrayList<>();
            manager.<String>register(EVENT_TYPE, event -> {
                delivered.add("first:" + event);
                if (event.equals("one")) {
                    manager.<String>register(EVENT_TYPE, next -> delivered.add("late:" + next), Set.of());
                }
            }, Set.of());
            manager.<String>register(EVENT_TYPE, event -> delivered.add("second:" + event), Set.of());

            emit(manager, "one", async);
            assertThat(delivered, contains("first:one", "second:one"));

            emit(manager, "two", async);
            assertThat(delivered, contains("first:one", "second:one", "first:two", "second:two", "late:two"));
        }
    }

    private static void assertReentrantAsyncRegistration(boolean async) throws Exception {
        // Inline execution makes observer registration happen while the current observer snapshot is being submitted.
        try (var executor = new InlineExecutorService()) {
            var manager = new EventManagerImpl(List::of, Optional.of(executor));
            List<String> delivered = new ArrayList<>();
            manager.<String>registerAsync(EVENT_TYPE, event -> {
                delivered.add("first:" + event);
                if (event.equals("one")) {
                    manager.<String>registerAsync(EVENT_TYPE, next -> delivered.add("late:" + next), Set.of());
                }
            }, Set.of());
            manager.<String>registerAsync(EVENT_TYPE, event -> delivered.add("second:" + event), Set.of());

            emit(manager, "one", async);
            assertThat(delivered, contains("first:one", "second:one"));

            emit(manager, "two", async);
            assertThat(delivered, contains("first:one", "second:one", "first:two", "second:two", "late:two"));
        }
    }

    private static void emit(EventManager manager, String event, boolean async) throws Exception {
        if (async) {
            assertThat(manager.emitAsync(EVENT_TYPE, event, Set.of())
                               .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS), is(event));
        } else {
            manager.emit(EVENT_TYPE, event, Set.of());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to continue event dispatch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class InlineExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown();
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            if (shutdown) {
                throw new RejectedExecutionException();
            }
            command.run();
        }
    }
}
