/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.nima.webserver;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.nima.webserver.task.spi.HelidonTaskExecutor;
import io.helidon.nima.webserver.task.spi.InterruptableTask;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class ThreadPerTaskExecutorTest {

    @Test
    void testTerminate() {
        HelidonTaskExecutor executor = newExecutor();
        assertThat(executor.isTerminated(), is(false));
        executor.terminate(1, TimeUnit.SECONDS);
        assertThat(executor.isTerminated(), is(true));
    }

    @Test
    void testTerminateInterruptableTask() throws Exception {
        HelidonTaskExecutor executor = newExecutor();

        // Submit task and ensure it is waiting
        Task task = new Task(true);
        executor.execute(task);
        task.waitUntilStarted();
        assertThatWithRetry(task.thread()::getState, is(Thread.State.WAITING));

        // There should be no need to force termination
        assertThat(executor.isTerminated(), is(false));
        executor.terminate(1, TimeUnit.SECONDS);
        assertThat(executor.isTerminated(), is(true));
    }

    @Test
    void testTerminateNonInterruptableTask() throws Exception {
        HelidonTaskExecutor executor = newExecutor();

        // Submit task and ensure it is waiting
        Task task = new Task(false);
        executor.execute(task);
        task.waitUntilStarted();
        assertThatWithRetry(task.thread()::getState, is(Thread.State.WAITING));

        // Force termination should be required
        assertThat(executor.isTerminated(), is(false));
        executor.terminate(1, TimeUnit.SECONDS);
        assertThat(executor.isTerminated(), is(false));
        executor.forceTerminate();
        assertThat(executor.isTerminated(), is(true));
    }

    private static HelidonTaskExecutor newExecutor() {
        return ThreadPerTaskExecutor.create(Thread.ofVirtual()
                .allowSetThreadLocals(true)
                .inheritInheritableThreadLocals(false)
                .factory());
    }

    /**
     * An interruptable task.
     */
    private static class Task implements InterruptableTask<Void> {

        private Thread thread;
        private final boolean canInterrupt;
        private final Barrier barrier;
        private final CountDownLatch started = new CountDownLatch(1);

        Task(boolean canInterrupt) {
            this.canInterrupt = canInterrupt;
            this.barrier = new Barrier();
        }

        @Override
        public boolean canInterrupt() {
            return canInterrupt;
        }

        void waitUntilStarted() throws InterruptedException {
            started.await();
        }

        @Override
        public void run() {
            try {
                thread = Thread.currentThread();
                started.countDown();
                barrier.waitOn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Thread thread() {
            return thread;
        }

        Barrier barrier() {
            return barrier;
        }
    }

    /**
     * A barrier is used to force a thread to wait (block) until it is retracted.
     */
    private static class Barrier {
        private final CompletableFuture<Void> future = new CompletableFuture<>();

        void waitOn() throws ExecutionException, InterruptedException {
            future.get();
        }

        void retract() {
            future.complete(null);
        }
    }
}
