/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

class AsyncTest {
    private static final long WAIT_TIMEOUT_MILLIS = 2000;

    @Test
    void testDefaultExecutorCreate() {
        Thread thread = testAsync(Async.create());
        assertThat(thread.isVirtual(), is(true));
    }

    @Test
    void testDefaultExecutorBuilder() {
        Async async = Async.create();
        Thread thread = testAsync(async);
        assertThat(thread.isVirtual(), is(true));
    }

    @Test
    void testCustomExecutorBuilder() {
        Async async = AsyncConfig.builder()
                .executor(FaultTolerance.executor().get())
                .build();
        Thread thread = testAsync(async);
        assertThat(thread.isVirtual(), is(true));
    }

    @Test
    void testThreadName() throws Exception {
        String threadName = Async.create()
                .invoke(() -> Thread.currentThread().getName())
                .get(10, TimeUnit.SECONDS);

        assertThat(threadName, startsWith("helidon-ft-"));
        assertThat(threadName, endsWith(": async"));
    }

    @Test
    void testContextPropagation() throws Exception {
        Context context = Context.create();
        CompletableFuture<Context> cf = new CompletableFuture<>();
        Contexts.runInContext(context, () -> {
            try {
                Async async = Async.create();
                async.invoke(() -> {
                    cf.complete(Contexts.context().orElse(null));
                    return null;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(cf.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(context));
    }

    @Test
    void testCancelSettlesResult() throws Exception {
        CountDownLatch executorOccupied = new CountDownLatch(1);
        CountDownLatch releaseExecutor = new CountDownLatch(1);
        AtomicBoolean supplierInvoked = new AtomicBoolean();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.execute(() -> {
                executorOccupied.countDown();
                try {
                    releaseExecutor.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(executorOccupied.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));

            Async async = AsyncConfig.builder()
                    .executor(executor)
                    .build();
            CompletableFuture<String> result = async.invoke(() -> {
                supplierInvoked.set(true);
                return "result";
            });
            CountDownLatch resultCompleted = new CountDownLatch(1);
            result.whenComplete((_, _) -> resultCompleted.countDown());

            assertThat(result.cancel(false), is(true));
            assertThat(result.isDone(), is(true));
            assertThat(result.isCancelled(), is(true));
            assertThat(resultCompleted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
        } finally {
            releaseExecutor.countDown();
            executor.close();
        }
        assertThat(supplierInvoked.get(), is(false));
    }

    @Test
    void testCancelInterruptsBeforeCompletionCallbacks() throws Exception {
        CountDownLatch supplierStarted = new CountDownLatch(1);
        CountDownLatch supplierInterrupted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Async async = AsyncConfig.builder()
                    .executor(executor)
                    .build();
            CompletableFuture<Void> result = async.invoke(() -> {
                supplierStarted.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException _) {
                    supplierInterrupted.countDown();
                    Thread.currentThread().interrupt();
                }
                return null;
            });
            assertThat(supplierStarted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
            result.whenComplete((_, _) -> {
                try {
                    supplierInterrupted.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            });

            CompletableFuture<Boolean> cancellation = CompletableFuture.supplyAsync(() -> result.cancel(true));
            assertThat(supplierInterrupted.await(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
            assertThat(cancellation.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), is(true));
            assertThat(result.isCancelled(), is(true));
        } finally {
            executor.shutdownNow();
            executor.close();
        }
    }

    private Thread testAsync(Async async) {
        try {
            CompletableFuture<Thread> cf = new CompletableFuture<>();
            async.invoke(() -> {
                cf.complete(Thread.currentThread());
                return null;
            });
            return cf.get(WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
