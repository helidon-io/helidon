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

package io.helidon.webserver.quic;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.quic.QuicConnection;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;

class QuicAcceptLoopTest {

    @Test
    void shouldDrainCompletedAcceptsAndOffloadPendingCompletion() {
        int connectionCount = 10_000;
        QuicConnection connection = mock(QuicConnection.class);
        CompletableFuture<QuicConnection> completed = CompletableFuture.completedFuture(connection);
        CompletableFuture<QuicConnection> pending = new CompletableFuture<>();
        CompletableFuture<QuicConnection> nextPending = new CompletableFuture<>();
        Queue<CompletableFuture<QuicConnection>> accepts = new ArrayDeque<>();
        for (int i = 0; i < connectionCount; i++) {
            accepts.add(completed);
        }
        accepts.add(pending);

        Queue<Runnable> scheduled = new ArrayDeque<>();
        AtomicInteger dispatched = new AtomicInteger();
        AtomicInteger discarded = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        QuicAcceptLoop acceptLoop = new QuicAcceptLoop(accepts::remove,
                                                       _ -> dispatched.incrementAndGet(),
                                                       _ -> discarded.incrementAndGet(),
                                                       failure::set,
                                                       scheduled::add);

        acceptLoop.start();
        assertThat(scheduled.size(), is(1));
        scheduled.remove().run();

        assertThat(dispatched.get(), is(connectionCount));
        assertThat(accepts.isEmpty(), is(true));
        assertThat(scheduled.isEmpty(), is(true));
        assertThat(failure.get(), nullValue());

        accepts.add(nextPending);
        assertThat(pending.complete(connection), is(true));

        assertThat(dispatched.get(), is(connectionCount));
        assertThat(scheduled.size(), is(1));
        scheduled.remove().run();

        assertThat(dispatched.get(), is(connectionCount + 1));
        assertThat(accepts.isEmpty(), is(true));
        assertThat(scheduled.isEmpty(), is(true));
        assertThat(failure.get(), nullValue());

        assertThat(nextPending.complete(connection), is(true));
        assertThat(scheduled.size(), is(1));
        acceptLoop.stop();

        assertThat(discarded.get(), is(1));
        scheduled.remove().run();
        assertThat(dispatched.get(), is(connectionCount + 1));
        assertThat(scheduled.isEmpty(), is(true));
    }

    @Test
    void shouldReportCompletionSchedulingFailure() {
        QuicConnection connection = mock(QuicConnection.class);
        CompletableFuture<QuicConnection> pending = new CompletableFuture<>();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        AtomicBoolean reject = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        QuicAcceptLoop acceptLoop = new QuicAcceptLoop(() -> pending,
                                                       _ -> { },
                                                       _ -> { },
                                                       failure::set,
                                                       task -> {
                                                           if (reject.get()) {
                                                               throw new RejectedExecutionException("test rejection");
                                                           }
                                                           scheduled.set(task);
                                                       });

        acceptLoop.start();
        scheduled.getAndSet(null).run();
        reject.set(true);
        assertThat(pending.complete(connection), is(true));

        assertThat(failure.get().getMessage(), is("test rejection"));
        assertThat(scheduled.get(), nullValue());
    }

    @Test
    void shouldNotBlockStopOnDispatch() throws Exception {
        QuicConnection connection = mock(QuicConnection.class);
        Queue<CompletableFuture<QuicConnection>> accepts = new ArrayDeque<>();
        accepts.add(CompletableFuture.completedFuture(connection));
        accepts.add(new CompletableFuture<>());
        CountDownLatch dispatchEntered = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        CountDownLatch dispatchFinished = new CountDownLatch(1);
        AtomicBoolean stopping = new AtomicBoolean();
        AtomicInteger discarded = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            QuicAcceptLoop acceptLoop = new QuicAcceptLoop(accepts::remove,
                                                           _ -> {
                                                               dispatchEntered.countDown();
                                                               try {
                                                                   releaseDispatch.await();
                                                                   if (stopping.get()) {
                                                                       discarded.incrementAndGet();
                                                                   }
                                                               } catch (InterruptedException e) {
                                                                   Thread.currentThread().interrupt();
                                                                   throw new IllegalStateException(e);
                                                               } finally {
                                                                   dispatchFinished.countDown();
                                                               }
                                                           },
                                                           _ -> discarded.incrementAndGet(),
                                                           failure::set,
                                                           executor);
            acceptLoop.start();
            assertThat(dispatchEntered.await(5, TimeUnit.SECONDS), is(true));

            stopping.set(true);
            CompletableFuture<Void> stopped = CompletableFuture.runAsync(acceptLoop::stop, executor);
            try {
                stopped.get(1, TimeUnit.SECONDS);
            } finally {
                releaseDispatch.countDown();
            }

            assertThat(dispatchFinished.await(5, TimeUnit.SECONDS), is(true));
            assertThat(discarded.get(), is(1));
            assertThat(failure.get(), nullValue());
        }
    }
}
