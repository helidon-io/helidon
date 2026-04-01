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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class BulkheadTest extends BulkheadBaseTest {

    @BeforeAll
    static void setupTest() {
        LogConfig.configureRuntime();
    }

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

        // Submit new task that should be queued
        Task enqueued = new Task(1);
        CompletableFuture<Integer> enqueuedResult = Async.invokeStatic(
                () -> bulkhead.invoke(enqueued::run));

        // Wait until previous task is queued
        if (!enqueuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Task enqueued never submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1, WAIT_TIMEOUT_MILLIS);

        // Submit new task that should be rejected
        Task rejected = new Task(2);
        CompletableFuture<Async> asyncRejected = new CompletableFuture<>();
        CompletableFuture<Integer> rejectedResult = Async.invokeStatic(
                () -> bulkhead.invoke(rejected::run),
                asyncRejected);
        asyncRejected.get();        // waits for async to start

        assertThat(inProgress.isStarted(), is(true));
        assertThat(inProgress.isBlocked(), is(true));
        assertThat(enqueued.isStarted(), is(false));
        assertThat(enqueued.isBlocked(), is(true));
        assertThat(rejected.isStarted(), is(false));
        assertThat(rejected.isBlocked(), is(true));

        // Verify rejected task was indeed rejected
        ExecutionException executionException = assertThrows(ExecutionException.class,
                () -> rejectedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        Throwable cause = executionException.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(BulkheadException.class));
        assertThat(cause.getMessage(), is("Bulkhead queue \"" + name + "\" is full"));

        // Unblock inProgress task and get result to free bulkhead
        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        // Wait for enqueued task to start and check state
        if (!enqueued.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task enqueued not started");
        }
        assertThat(enqueued.isStarted(), is(true));
        assertThat(enqueued.isBlocked(), is(true));
        assertThat(rejected.isStarted(), is(false));
        assertThat(rejected.isBlocked(), is(true));

        // Unblock enqueued task and get result
        enqueued.unblock();
        enqueuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    void testBulkheadQueue() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        // Create bulkhead of 1 with a queue of 1000
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1000)
                .build();

        // Submit request to bulkhead of limit 1
        Task first = new Task(0);
        CompletableFuture<?> firstFuture = Async.invokeStatic(() -> bulkhead.invoke(first::run));

        // Wait until started before submitting additional tasks
        if (!first.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task first not started");
        }

        // Submit additional request to fill up queue
        Task[] tasks = new Task[999];
        for (int i = 0; i < tasks.length; i++) {
            Task task = new Task(i + 1);
            tasks[i] = task;
            CompletableFuture<?> f = Async.invokeStatic(() -> bulkhead.invoke(task::run));
            tasks[i].future(f);
        }

        // Verify all tasks are queued and unblock them
        for (Task task : tasks) {
            assertFalse(task.isStarted());
            task.unblock();
        }

        // Let first complete operation and free bulkhead
        assertTrue(first.isBlocked());
        first.unblock();
        firstFuture.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        // Get all results
        for (Task task : tasks) {
            task.future().get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    void testCancelQueuedSupplier() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        CountDownLatch queuedSubmitted = new CountDownLatch(1);
        AtomicInteger dequeued = new AtomicInteger();
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1)
                .addQueueListener(new Bulkhead.QueueListener() {
                    @Override
                    public <T> void enqueueing(Supplier<? extends T> supplier) {
                        queuedSubmitted.countDown();
                    }

                    @Override
                    public <T> void dequeued(Supplier<? extends T> supplier) {
                        dequeued.incrementAndGet();
                    }
                })
                .build();

        Task inProgress = new Task(0);
        CompletableFuture<Integer> inProgressResult = Async.invokeStatic(() -> bulkhead.invoke(inProgress::run));

        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Task queued = new Task(1);
        Supplier<Integer> queuedSupplier = queued::run;
        CompletableFuture<Integer> queuedResult = Async.invokeStatic(() -> bulkhead.invoke(queuedSupplier));

        if (!queuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Task queued never submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1, WAIT_TIMEOUT_MILLIS);

        assertThat(bulkhead.cancelSupplier(queuedSupplier), is(true));
        assertThat(queuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(nullValue()));
        assertThat(queued.isStarted(), is(false));
        assertThat(dequeued.get(), is(0));
        assertThat(bulkhead.stats().waitingQueueSize(), is(0L));

        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    void testCancelQueuedSupplierRequiresSameInstance()
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        CountDownLatch queuedSubmitted = new CountDownLatch(1);
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1)
                .addQueueListener(new Bulkhead.QueueListener() {
                    @Override
                    public <T> void enqueueing(Supplier<? extends T> supplier) {
                        queuedSubmitted.countDown();
                    }
                })
                .build();

        Task inProgress = new Task(0);
        CompletableFuture<Integer> inProgressResult = Async.invokeStatic(() -> bulkhead.invoke(inProgress::run));

        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Task queued = new Task(1);
        Supplier<Integer> queuedSupplier = new EqualSupplier<>(1, queued::run);
        Supplier<Integer> equalButDistinctSupplier = new EqualSupplier<>(1, () -> 99);
        CompletableFuture<Integer> queuedResult = Async.invokeStatic(() -> bulkhead.invoke(queuedSupplier));

        if (!queuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Task queued never submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1, WAIT_TIMEOUT_MILLIS);

        assertFalse(bulkhead.cancelSupplier(equalButDistinctSupplier));
        assertThat(bulkhead.stats().waitingQueueSize(), is(1L));

        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        if (!queued.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task queued not started");
        }
        queued.unblock();
        assertThat(queuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(1));
    }

    @Test
    void testCancelQueuedSupplierWithSameInstanceQueuedTwice()
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        CountDownLatch queuedSubmitted = new CountDownLatch(2);
        CountDownLatch sharedStarted = new CountDownLatch(1);
        CountDownLatch allowSharedToFinish = new CountDownLatch(1);
        AtomicInteger sharedRuns = new AtomicInteger();
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(2)
                .addQueueListener(new Bulkhead.QueueListener() {
                    @Override
                    public <T> void enqueueing(Supplier<? extends T> supplier) {
                        queuedSubmitted.countDown();
                    }
                })
                .build();

        Task inProgress = new Task(0);
        CompletableFuture<Integer> inProgressResult = Async.invokeStatic(() -> bulkhead.invoke(inProgress::run));

        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Supplier<Integer> sharedSupplier = () -> {
            sharedRuns.incrementAndGet();
            sharedStarted.countDown();
            try {
                allowSharedToFinish.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            return 1;
        };

        CompletableFuture<Integer> firstQueuedResult = Async.invokeStatic(() -> bulkhead.invoke(sharedSupplier));
        CompletableFuture<Integer> secondQueuedResult = Async.invokeStatic(() -> bulkhead.invoke(sharedSupplier));

        if (!queuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Queued tasks were not submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 2, WAIT_TIMEOUT_MILLIS);

        assertTrue(bulkhead.cancelSupplier(sharedSupplier));
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1, WAIT_TIMEOUT_MILLIS);
        assertFalse(sharedStarted.await(200, TimeUnit.MILLISECONDS));

        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        if (!sharedStarted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Shared supplier not started");
        }
        allowSharedToFinish.countDown();

        Integer firstResult = firstQueuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        Integer secondResult = secondQueuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        assertThat(sharedRuns.get(), is(1));
        assertThat((firstResult == null) ^ (secondResult == null), is(true));
        assertThat(firstResult == null ? secondResult : firstResult, is(1));
        assertThat(bulkhead.stats().waitingQueueSize(), is(0L));
    }

    @RepeatedTest(100)
    void testInterruptedQueuedSupplierDoesNotLeakPermit()
            throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        String name = "unit:testInterruptedQueuedSupplierDoesNotLeakPermit";
        CountDownLatch queuedSubmitted = new CountDownLatch(2);
        AtomicReference<Thread> interruptedQueuedThread = new AtomicReference<>();
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(2)
                .name(name)
                .addQueueListener(new Bulkhead.QueueListener() {
                    @Override
                    public <T> void enqueueing(Supplier<? extends T> supplier) {
                        queuedSubmitted.countDown();
                    }
                })
                .build();

        Task inProgress = new Task(0);
        CompletableFuture<Integer> inProgressResult = Async.invokeStatic(() -> bulkhead.invoke(inProgress::run));

        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress not started");
        }

        Task interruptedQueued = new Task(1);
        Supplier<Integer> interruptedSupplier = interruptedQueued::run;
        CompletableFuture<Integer> interruptedQueuedResult = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            interruptedQueuedThread.set(Thread.currentThread());
            try {
                interruptedQueuedResult.complete(bulkhead.invoke(interruptedSupplier));
            } catch (Throwable t) {
                interruptedQueuedResult.completeExceptionally(t);
            }
        });

        Task nextQueued = new Task(2);
        CompletableFuture<Integer> nextQueuedResult = Async.invokeStatic(() -> bulkhead.invoke(nextQueued::run));

        if (!queuedSubmitted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            fail("Queued tasks were not submitted");
        }
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 2
                && waitingOnBarrier(interruptedQueuedThread.get()), WAIT_TIMEOUT_MILLIS);

        interruptedQueuedThread.get().interrupt();

        ExecutionException executionException = assertThrows(ExecutionException.class,
                () -> interruptedQueuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        Throwable cause = executionException.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(BulkheadException.class));
        assertThat(cause.getMessage(), is("Bulkhead \"" + name + "\" interrupted while acquiring"));
        assertThat(interruptedQueued.isStarted(), is(false));
        assertEventually(() -> bulkhead.stats().waitingQueueSize() == 1, WAIT_TIMEOUT_MILLIS);

        inProgress.unblock();
        inProgressResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        if (!nextQueued.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task nextQueued not started");
        }
        nextQueued.unblock();
        assertThat(nextQueuedResult.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(2));
        assertThat(bulkhead.stats().waitingQueueSize(), is(0L));
    }

    @RepeatedTest(100)
    void testBulkheadWithError() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        // Create bulkhead of 1 with a queue of 1
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1)
                .build();

        // First check exception throw using synchronous call
        assertThrows(IllegalStateException.class,
                () -> bulkhead.invoke(() -> { throw new IllegalStateException(); }));

        // Send 2 tasks to bulkhead, one that fails
        Task inProgress = new Task(0);
        CompletableFuture<?> inProgressFuture = Async.invokeStatic(
                () -> bulkhead.invoke(inProgress::run));

        // Verify completion of inProgress task
        if (!inProgress.waitUntilStarted(WAIT_TIMEOUT_MILLIS)) {
            fail("Task inProgress never started");
        }

        // as we use an async to submit to bulkhead, we should wait until the first task is submitted
        CompletableFuture<?> failedFuture = Async.invokeStatic(
                () -> bulkhead.invoke(() -> { throw new IllegalStateException(); }));

        inProgress.unblock();
        inProgressFuture.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        // Verify failure of other task
        ExecutionException executionException = assertThrows(ExecutionException.class,
                () -> failedFuture.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        Throwable cause = executionException.getCause();
        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(IllegalStateException.class));
    }

    private static final class EqualSupplier<T> implements Supplier<T> {
        private final int id;
        private final Supplier<T> delegate;

        private EqualSupplier(int id, Supplier<T> delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        @Override
        public T get() {
            return delegate.get();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof EqualSupplier<?> that)) {
                return false;
            }
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }
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
