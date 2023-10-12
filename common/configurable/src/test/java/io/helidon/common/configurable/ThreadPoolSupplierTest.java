/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.common.configurable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.context.ContextAwareExecutorService;
import io.helidon.config.Config;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * Unit test for {@link ThreadPoolSupplier}.
 */
class ThreadPoolSupplierTest {
    private static ThreadPoolExecutor builtInstance;
    private static ThreadPoolExecutor configuredInstance;
    private static ThreadPoolExecutor defaultInstance;

    private static ThreadPoolSupplier builtSupplier;
    private static ThreadPoolSupplier configuredSupplier;
    private static ThreadPoolSupplier defaultSupplier;


    @BeforeAll
    static void initClass() {
        defaultSupplier = ThreadPoolSupplier.create("test-thread-pool");
        defaultInstance = ensureOurExecutor(defaultSupplier.getThreadPool());

        builtSupplier = ThreadPoolSupplier.builder()
                .threadNamePrefix("thread-pool-unit-test-")
                .corePoolSize(2)
                .daemon(true)
                .shouldPrestart(true)
                .queueCapacity(10)
                .build();
        builtInstance = ensureOurExecutor(builtSupplier.getThreadPool());

        configuredSupplier = ThreadPoolSupplier.create(Config.create().get("unit.thread-pool"), "test-thread-pool");
        configuredInstance = ensureOurExecutor(configuredSupplier.getThreadPool());
    }

    private static ThreadPoolExecutor ensureOurExecutor(ExecutorService threadPool) {
        // thread pool should be our implementation, unless Loom virtual threads are used
        assertThat(threadPool, instanceOf(ThreadPoolExecutor.class));
        return (ThreadPoolExecutor) threadPool;
    }

    @Test
    void testDefaultInstance() throws ExecutionException, InterruptedException {
        testInstance(defaultInstance,
                     "helidon-test-thread-pool-",
                     10,
                     10000,
                     10,
                     true);
        checkObserver(defaultSupplier, defaultInstance, "helidon-");
    }

    @Test
    void testConfiguredInstance() throws ExecutionException, InterruptedException {
        testInstance(configuredInstance,
                     "thread-pool-config-unit-test-",
                     3,
                     15,
                     0,
                     false);
        checkObserver(configuredSupplier, configuredInstance, "thread-pool-config-unit-test-");
    }

    @Test
    void testBuiltInstance() throws ExecutionException, InterruptedException {
        testInstance(builtInstance,
                     "thread-pool-unit-test-",
                     2,
                     10,
                     2,
                     true);
        checkObserver(builtSupplier, builtInstance, "thread-pool-unit-test-");
    }

    @Test
    void testNameAndThreadPrefixName() throws ExecutionException, InterruptedException {
        // Name      | threadNamePrefix| Derived Name           | Derived threadNamePrefix |
        // --------- |-----------------| ---------------------- | ------------------------ |
        // none      | none            | helidon-thread-pool-N  | helidon-                 |
        // "mypool"  | none            | mypool                 | helidon-mypool-          |
        // none      | "mythread-"     | mythread-thread-pool-N | mythread-                |
        // "mypool"  | "mythread-"     | mypool                 | mythread-                |

        ThreadPoolSupplier supplier = ThreadPoolSupplier.builder().build();
        ThreadPool pool = (ThreadPool)ensureOurExecutor(supplier.getThreadPool());
        testNaming(pool, "helidon-thread-pool-", "helidon-");

        supplier = ThreadPoolSupplier.builder().name("mypool").build();
        pool = (ThreadPool)ensureOurExecutor(supplier.getThreadPool());
        testNaming(pool, "mypool", "helidon-mypool-");

        supplier = ThreadPoolSupplier.builder().threadNamePrefix("mythread-").build();
        pool = (ThreadPool)ensureOurExecutor(supplier.getThreadPool());
        testNaming(pool, "mythread-thread-pool-", "mythread-");

        supplier = ThreadPoolSupplier.builder().name("mypool").threadNamePrefix("mythread-").build();
        pool = (ThreadPool)ensureOurExecutor(supplier.getThreadPool());
        testNaming(pool, "mypool", "mythread-");
    }

    private void testNaming(ThreadPool pool, String name, String threadNamePrefix) throws ExecutionException, InterruptedException {
        AtomicReference<String> threadName = new AtomicReference<>();
        pool.submit(() -> threadName.set(Thread.currentThread().getName())).get();
        assertThat(pool.getName(), startsWith(name));
        assertThat(threadName.get(), startsWith(threadNamePrefix));
    }

    private void testInstance(ThreadPoolExecutor theInstance,
                              String namePrefix,
                              int corePoolSize,
                              int queueCapacity,
                              int poolSize,
                              boolean shouldBeDaemon) throws ExecutionException, InterruptedException {
        // test that we did indeed create an executor service
        assertThat(theInstance, notNullValue());
        // test that pool size is configured correctly
        assertThat("core pool size", theInstance.getCorePoolSize(), is(corePoolSize));
        // test that queue-capacity is configured correctly
        assertThat("queue remaining capacity", theInstance.getQueue().remainingCapacity(), is(queueCapacity));
        // test that prestart is configured correctly
        assertThat("pool size", theInstance.getPoolSize(), is(poolSize));

        AtomicReference<String> threadName = new AtomicReference<>();
        theInstance
            .submit(() -> threadName.set(Thread.currentThread().getName()))
            .get();

        assertThat(threadName.get(), startsWith(namePrefix));

        AtomicReference<Boolean> isDaemon = new AtomicReference<>();
        theInstance
            .submit(() -> isDaemon.set(Thread.currentThread().isDaemon()))
            .get();

        assertThat(isDaemon.get(), is(shouldBeDaemon));
    }

    private void checkObserver(ThreadPoolSupplier supplier, ExecutorService theInstance, String name) {
        assertThat("Supplier collection",
                   ObserverForTesting.instance.suppliers(),
                   hasKey(supplier));
        ObserverForTesting.SupplierInfo supplierInfo = ObserverForTesting.instance.suppliers().get(supplier);
        assertThat("ExecutorService",
                   supplierInfo.context().executorServices(),
                   hasItem(theInstance instanceof ContextAwareExecutorService
                                   ? ((ContextAwareExecutorService) theInstance).unwrap()
                                   : theInstance));
        ObserverForTesting.Context ctx = supplierInfo.context();
        assertThat("Count of non-scheduled executor services registered", ctx.threadPoolCount(), CoreMatchers.is(not(0)));
        assertThat("Count of scheduled executor services registered", ctx.scheduledCount(), CoreMatchers.is(0));
    }
}
