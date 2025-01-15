/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
import java.util.function.Supplier;

import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
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
}
