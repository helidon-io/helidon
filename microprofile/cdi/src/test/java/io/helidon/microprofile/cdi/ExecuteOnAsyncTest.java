/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Named;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.cdi.ExecuteOn.ThreadType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class ExecuteOnAsyncTest {

    public static final int SHORT_TIMEOUT = 500;
    public static final int LONG_TIMEOUT = 10000;

    static SeContainer seContainer;
    static OnNewThreadBean bean;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void startCdi() {
        seContainer = SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addExtensions(ExecuteOnExtension.class)
                .addBeanClasses(OnNewThreadBean.class)
                .initialize();
        bean = CDI.current().select(OnNewThreadBean.class).get();
    }

    @AfterAll
    static void stopCdi() {
        seContainer.close();
    }

    static class OnNewThreadBean {

        @ExecuteOn(ThreadType.PLATFORM)
        CompletionStage<Thread> cpuIntensive() {
            return CompletableFuture.completedFuture(Thread.currentThread());
        }

        @ExecuteOn(value = ThreadType.PLATFORM)
        CompletableFuture<Thread> evenMoreCpuIntensive() {
            return CompletableFuture.completedFuture(Thread.currentThread());
        }

        @ExecuteOn(ThreadType.VIRTUAL)
        CompletionStage<Thread> onVirtualThread() {
            return CompletableFuture.completedFuture(Thread.currentThread());
        }

        @ExecuteOn(value = ThreadType.EXECUTOR, executorName = "my-executor")
        CompletableFuture<Thread> onMyExecutor() {
            return CompletableFuture.completedFuture(Thread.currentThread());
        }

        @ExecuteOn(ThreadType.VIRTUAL)
        CompletionStage<Optional<String>> verifyContextVirtual() {
            return CompletableFuture.completedFuture(
                    Contexts.context().flatMap(context -> context.get("hello", String.class)));
        }

        @ExecuteOn(ThreadType.PLATFORM)
        CompletableFuture<Optional<String>> verifyContextPlatform() {
            return CompletableFuture.completedFuture(
                    Contexts.context().flatMap(context -> context.get("hello", String.class)));
        }

        @ExecuteOn(ThreadType.VIRTUAL)
        CompletableFuture<Thread> eternallyBlocked() throws BrokenBarrierException, InterruptedException {
            CyclicBarrier barrier = new CyclicBarrier(2);
            barrier.await();
            return CompletableFuture.completedFuture(Thread.currentThread());
        }

        @ExecuteOn(ThreadType.VIRTUAL)
        CompletableFuture<Thread> alwaysFails() {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("Not supported"));
        }

        @Produces
        @Named("my-executor")
        ExecutorService myExecutor() {
            return Executors.newFixedThreadPool(2);
        }
    }

    @Test
    void cpuIntensiveTest() throws ExecutionException, InterruptedException, TimeoutException {
        CompletionStage<Thread> completionStage = bean.cpuIntensive();
        Thread thread = completionStage.toCompletableFuture().get(LONG_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat(thread.isVirtual(), is(false));
        assertThat(thread.getName().startsWith("my-platform-thread"), is(true));
    }

    @Test
    void evenMoreCpuIntensiveTest() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Thread> completableFuture = bean.evenMoreCpuIntensive();
        Thread thread = completableFuture.get(LONG_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat(thread.isVirtual(), is(false));
        assertThat(thread.getName().startsWith("my-platform-thread"), is(true));
    }

    @Test
    void onVirtualThread() throws ExecutionException, InterruptedException, TimeoutException {
        CompletionStage<Thread> completionStage = bean.onVirtualThread();
        Thread thread = completionStage.toCompletableFuture().get(LONG_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat(thread.isVirtual(), is(true));
        assertThat(thread.getName().startsWith("my-virtual-thread"), is(true));
    }

    @Test
    void onMyExecutor() throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<Thread> completableFuture = bean.onMyExecutor();
        Thread thread = completableFuture.get(LONG_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat(thread.isVirtual(), is(false));
        assertThat(thread.getName().startsWith("pool"), is(true));
    }

    @Test
    void verifyContextVirtual() throws ExecutionException, InterruptedException, TimeoutException {
        Context context = Contexts.globalContext();
        context.register("hello", "world");
        CompletionStage<Optional<String>> completionStage = Contexts.runInContext(context, bean::verifyContextVirtual);
        Optional<String> optional = completionStage.toCompletableFuture().get(LONG_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat(optional.orElseThrow(), is("world"));
    }

    @Test
    void verifyContextPlatform() throws ExecutionException, InterruptedException, TimeoutException {
        Context context = Contexts.globalContext();
        context.register("hello", "world");
        CompletableFuture<Optional<String>> completableFuture = Contexts.runInContext(context, bean::verifyContextPlatform);
        Optional<String> optional = completableFuture.get(LONG_TIMEOUT, TimeUnit.MILLISECONDS);
        assertThat(optional.orElseThrow(), is("world"));
    }

    @Test
    void testEternallyBlocked() throws Exception {
        CompletableFuture<Thread> completableFuture = bean.eternallyBlocked();
        assertThrows(TimeoutException.class,
                     () -> completableFuture.get(SHORT_TIMEOUT, TimeUnit.MILLISECONDS));
        completableFuture.cancel(true);
        assertThrows(CancellationException.class,
                     () -> completableFuture.get(LONG_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    void testAlwaysFails() {
        CompletableFuture<Thread> completableFuture = bean.alwaysFails();
        try {
            completableFuture.get(LONG_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(instanceOf(UnsupportedOperationException.class)));
        } catch (Exception e) {
            fail();
        }
    }
}
