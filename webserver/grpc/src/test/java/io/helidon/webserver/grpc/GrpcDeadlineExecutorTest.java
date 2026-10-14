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

package io.helidon.webserver.grpc;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(15)
class GrpcDeadlineExecutorTest {
    @Test
    void scheduledFutureWaitsForApplicationCancellationToFinish() throws Exception {
        try (var executor = new GrpcDeadlineExecutor(); var gate = new CancellationGate()) {
            var future = executor.schedule(gate, 0, TimeUnit.NANOSECONDS);
            Thread callbackThread = gate.entered.get(5, TimeUnit.SECONDS);
            assertThat("actual expiry uses a virtual thread", callbackThread.isVirtual(), is(true));
            assertThat("dispatching cancellation does not complete its future", future.isDone(), is(false));
            assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));

            // The shared timer must keep processing while this application's callback is blocked.
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            assertThat("application cancellation remains blocked", gate.finished.getCount(), is(1L));
            gate.release.countDown();
            future.get(5, TimeUnit.SECONDS);
            assertThat("future completes after application cancellation", future.isDone(), is(true));
            assertThat("callback observed its release", gate.released.get(), is(true));
            assertThat("callback was not interrupted", gate.interrupted.get(), is(false));
        }
    }

    @Test
    void cancellationBeforeDeadlineRemovesTimerAndPreventsCallback() throws Exception {
        try (var executor = new GrpcDeadlineExecutor()) {
            var invoked = new AtomicBoolean();
            var future = executor.schedule(() -> invoked.set(true), 1, TimeUnit.HOURS);
            assertThat("deadline timer is queued", executor.getQueue().size(), is(1));

            assertThat("pending deadline can be cancelled", future.cancel(false), is(true));
            assertThat("cancelled deadline is removed from the shared timer", executor.getQueue().isEmpty(), is(true));
            assertThat("future reports cancellation", future.isCancelled(), is(true));
            assertThrows(CancellationException.class, () -> future.get(5, TimeUnit.SECONDS));
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            assertThat("cancelled deadline does not invoke application cancellation", invoked.get(), is(false));
        }
    }

    @Test
    void cancellationWithoutInterruptLeavesRunningCallbackUninterrupted() throws Exception {
        try (var executor = new GrpcDeadlineExecutor(); var gate = new CancellationGate()) {
            var future = executor.schedule(gate, 0, TimeUnit.NANOSECONDS);
            Thread callbackThread = gate.entered.get(5, TimeUnit.SECONDS);

            assertThat("running callback's future can be cancelled", future.cancel(false), is(true));
            assertThat("future reports cancellation immediately", future.isCancelled(), is(true));
            assertThrows(CancellationException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertThat("cancellation does not interrupt the application callback", callbackThread.isInterrupted(), is(false));
            assertThat("application callback remains blocked until released", gate.finished.getCount(), is(1L));
            gate.release.countDown();
            assertThat("application callback finished", gate.finished.await(5, TimeUnit.SECONDS), is(true));
            assertThat("callback observed its release", gate.released.get(), is(true));
            assertThat("callback was not interrupted while blocked", gate.interrupted.get(), is(false));
        }
    }

    @Test
    void scheduledFutureReportsApplicationCancellationFailure() throws Exception {
        try (var executor = new GrpcDeadlineExecutor()) {
            var expected = new IllegalStateException("test application cancellation failure");
            var future = executor.schedule(() -> {
                throw expected;
            }, 0, TimeUnit.NANOSECONDS);

            var failure = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            assertThat("application failure is retained", failure.getCause(), sameInstance(expected));
            assertThat("failed cancellation is complete", future.isDone(), is(true));
            assertThat("application failure is not cancellation", future.isCancelled(), is(false));
        }
    }

    private static final class CancellationGate implements Runnable, AutoCloseable {
        private final CompletableFuture<Thread> entered = new CompletableFuture<>();
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);
        private final AtomicBoolean interrupted = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        @Override
        public void run() {
            entered.complete(Thread.currentThread());
            try {
                released.set(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException _) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            if (entered.isDone()) {
                assertThat("owned expiry callback stopped", finished.await(5, TimeUnit.SECONDS), is(true));
                Thread callbackThread = entered.get(5, TimeUnit.SECONDS);
                callbackThread.join(5_000);
                assertThat("owned expiry thread stopped", callbackThread.isAlive(), is(false));
            }
        }
    }
}
