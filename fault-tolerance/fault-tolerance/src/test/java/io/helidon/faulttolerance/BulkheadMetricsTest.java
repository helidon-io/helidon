/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static io.helidon.faulttolerance.Bulkhead.FT_BULKHEAD_CALLS_TOTAL;
import static io.helidon.faulttolerance.Bulkhead.FT_BULKHEAD_EXECUTIONSREJECTED;
import static io.helidon.faulttolerance.Bulkhead.FT_BULKHEAD_EXECUTIONSRUNNING;
import static io.helidon.faulttolerance.Bulkhead.FT_BULKHEAD_EXECUTIONSWAITING;
import static io.helidon.faulttolerance.Bulkhead.FT_BULKHEAD_WAITINGDURATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@Testing.Test
class BulkheadMetricsTest extends BulkheadBaseTest {

    @Test
    void testBulkhead() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        // Create bulkhead of 1 with queue length 1
        String name = "unit:testBulkhead";
        CountDownLatch enqueuedSubmitted = new CountDownLatch(1);
        Bulkhead bulkhead = BulkheadConfig.builder()
                .limit(1)
                .queueLength(1)
                .name(name)
                .addQueueListener(new Bulkhead.QueueListener() {
                    @Override
                    public <T> void enqueueing(Supplier<? extends T> supplier) {
                        enqueuedSubmitted.countDown();
                    }
                })
                .build();

        // Submit first inProgress task
        Task inProgress = new Task(0);
        CompletableFuture<Integer> inProgressResult = Async.invokeStatic(
                () -> bulkhead.invoke(inProgress::run));

        // Wait until started before submitting enqueued
        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        // Check metrics
        Tag nameTag = Tag.create("name", bulkhead.name());
        Counter callsTotal = MetricsUtils.counter(FT_BULKHEAD_CALLS_TOTAL, nameTag);
        assertThat(callsTotal.count(), is(1L));
        Gauge<Long> running = MetricsUtils.gauge(FT_BULKHEAD_EXECUTIONSRUNNING, nameTag);
        assertThat(running.value(), is(1L));

        // Submit new task that should be queued
        Task enqueued = new Task(1);
        CompletableFuture<Integer> enqueuedResult = Async.invokeStatic(
                () -> bulkhead.invoke(enqueued::run));

        // Wait until previous task is queued
        if (!enqueuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Task enqueued never submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1, WAIT_TIMEOUT_MILLIS);

        // Check metrics
        assertThat(callsTotal.count(), is(2L));
        Gauge<Long> waiting = MetricsUtils.gauge(FT_BULKHEAD_EXECUTIONSWAITING, nameTag);
        assertThat(waiting.value(), is(1L));

        // Submit new task that should be rejected
        Task rejectedTask = new Task(2);
        assertThrows(BulkheadException.class, () -> bulkhead.invoke(rejectedTask::run));

        // Check metrics
        assertThat(callsTotal.count(), is(3L));
        Gauge<Long> rejected = MetricsUtils.gauge(FT_BULKHEAD_EXECUTIONSREJECTED, nameTag);
        assertThat(rejected.value(), is(1L));
        Timer waitingDuration = MetricsUtils.timer(FT_BULKHEAD_WAITINGDURATION, nameTag);
        assertThat(waitingDuration.count(), is(0L));

        // Unblock inProgress task and get result to free bulkhead
        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        // Wait for enqueued task to start and check state
        if (!enqueued.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task enqueued not started");
        }

        // Check metrics
        assertThat(running.value(), is(1L));
        assertThat(waiting.value(), is(0L));
        assertThat(waitingDuration.count(), is(1L));
        assertThat(waitingDuration.totalTime(TimeUnit.MILLISECONDS), greaterThan(0D));

        // Unblock enqueued task and get result
        enqueued.unblock();
        enqueuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        // Check metrics
        assertThat(running.value(), is(0L));
        assertThat(waiting.value(), is(0L));
    }

    @Test
    void testWaitingMetricTracksBlockedQueuedCall()
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        String name = "unit:testWaitingMetricTracksBlockedQueuedCall";
        CountDownLatch queuedSubmitted = new CountDownLatch(1);
        AtomicReference<Thread> queuedThread = new AtomicReference<>();
        Bulkhead bulkhead = BulkheadConfig.builder()
                .limit(1)
                .queueLength(1)
                .name(name)
                .addQueueListener(new Bulkhead.QueueListener() {
                    @Override
                    public <T> void enqueueing(Supplier<? extends T> supplier) {
                        queuedSubmitted.countDown();
                    }
                })
                .build();

        Task inProgress = new Task(0);
        CompletableFuture<Integer> inProgressResult = Async.invokeStatic(
                () -> bulkhead.invoke(inProgress::run));

        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Tag nameTag = Tag.create("name", bulkhead.name());
        Gauge<Long> waiting = MetricsUtils.gauge(FT_BULKHEAD_EXECUTIONSWAITING, nameTag);
        Timer waitingDuration = MetricsUtils.timer(FT_BULKHEAD_WAITINGDURATION, nameTag);
        assertThat(waiting.value(), is(0L));
        assertThat(waitingDuration.count(), is(0L));

        Task queued = new Task(1);
        CompletableFuture<Integer> queuedResult = Async.invokeStatic(
                () -> {
                    queuedThread.set(Thread.currentThread());
                    return bulkhead.invoke(queued::run);
                });

        if (!queuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Task queued never submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1
                && waitingOnBarrier(queuedThread.get()), WAIT_TIMEOUT_MILLIS);

        assertThat(queued.isStarted(), is(false));
        assertThat(waiting.value(), is(1L));
        assertThat(waitingDuration.count(), is(0L));

        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        if (!queued.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task queued not started");
        }
        assertThat(waiting.value(), is(0L));
        assertThat(waitingDuration.count(), is(1L));
        assertThat(waitingDuration.totalTime(TimeUnit.MILLISECONDS), greaterThan(0D));

        queued.unblock();
        queuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    void testWaitingMetricsCleanupOnQueuedCancellation()
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        String name = "unit:testWaitingMetricsCleanupOnQueuedCancellation";
        CountDownLatch queuedSubmitted = new CountDownLatch(1);
        AtomicReference<Thread> queuedThread = new AtomicReference<>();
        Bulkhead bulkhead = BulkheadConfig.builder()
                .limit(1)
                .queueLength(1)
                .name(name)
                .addQueueListener(new Bulkhead.QueueListener() {
                    @Override
                    public <T> void enqueueing(Supplier<? extends T> supplier) {
                        queuedSubmitted.countDown();
                    }
                })
                .build();

        Task inProgress = new Task(0);
        CompletableFuture<Integer> inProgressResult = Async.invokeStatic(
                () -> bulkhead.invoke(inProgress::run));

        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Tag nameTag = Tag.create("name", bulkhead.name());
        Gauge<Long> waiting = MetricsUtils.gauge(FT_BULKHEAD_EXECUTIONSWAITING, nameTag);
        Timer waitingDuration = MetricsUtils.timer(FT_BULKHEAD_WAITINGDURATION, nameTag);

        Task queued = new Task(1);
        Supplier<Integer> queuedSupplier = queued::run;
        CompletableFuture<Integer> queuedResult = Async.invokeStatic(
                () -> {
                    queuedThread.set(Thread.currentThread());
                    return bulkhead.invoke(queuedSupplier);
                });

        if (!queuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Task queued never submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1
                && waitingOnBarrier(queuedThread.get()), WAIT_TIMEOUT_MILLIS);

        assertThat(waiting.value(), is(1L));
        assertThat(waitingDuration.count(), is(0L));

        assertThat(bulkhead.cancelSupplier(queuedSupplier), is(true));
        assertThat(queuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(nullValue()));
        assertThat(queued.isStarted(), is(false));
        assertThat(waiting.value(), is(0L));
        assertThat(waitingDuration.count(), is(1L));
        assertThat(waitingDuration.totalTime(TimeUnit.MILLISECONDS), greaterThan(0D));
        assertThat(bulkhead.stats().waitingQueueSize(), is(0L));

        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static boolean waitingOnBarrier(Thread thread) {
        if (thread == null) {
            return false;
        }
        for (StackTraceElement element : thread.getStackTrace()) {
            if (element.getClassName().equals(BulkheadImpl.class.getName() + "$Barrier")
                    && element.getMethodName().equals("waitOn")) {
                return true;
            }
        }
        return false;
    }
}
