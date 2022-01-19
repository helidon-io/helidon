/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * Unit test for {@link ScheduledThreadPoolSupplier}.
 */
class ScheduledThreadPoolSupplierTest {
    private static ScheduledThreadPoolExecutor builtInstance;
    private static ScheduledThreadPoolExecutor configuredInstance;
    private static ScheduledThreadPoolExecutor defaultInstance;

    private static ScheduledThreadPoolSupplier builtSupplier;
    private static ScheduledThreadPoolSupplier configuredSupplier;
    private static ScheduledThreadPoolSupplier defaultSupplier;


    @BeforeAll
    static void initClass() {
        ObserverForTesting.clear();
        defaultSupplier = ScheduledThreadPoolSupplier.create();
        defaultInstance = defaultSupplier.getThreadPool();

        builtSupplier = ScheduledThreadPoolSupplier.builder()
                .threadNamePrefix("scheduled-thread-pool-unit-test-")
                .corePoolSize(2)
                .daemon(true)
                .prestart(true)
                .build();
        builtInstance = builtSupplier.getThreadPool();

        configuredSupplier = ScheduledThreadPoolSupplier.create(Config.create()
                                                                        .get("unit.scheduled-thread-pool"));
        configuredInstance = configuredSupplier.getThreadPool();
    }

    @AfterAll
    static void shutdown() {
        defaultInstance.shutdown();
        configuredInstance.shutdown();
    }

    @Test
    void testDefaultInstance() throws ExecutionException, InterruptedException {
        testInstance(defaultInstance,
                "helidon-",
                16,
                true);
        checkObserver(defaultSupplier, defaultInstance, "scheduled");
    }

    @Test
    void testConfiguredInstance() throws ExecutionException, InterruptedException {
        testInstance(configuredInstance,
                "scheduled-thread-pool-config-unit-test-",
                3,
                false);
        checkObserver(configuredSupplier, configuredInstance, "scheduled");
    }

    @Test
    void testBuiltInstance() throws ExecutionException, InterruptedException {
        testInstance(builtInstance,
                "scheduled-thread-pool-unit-test-",
                2,
                true);
        checkObserver(builtSupplier, builtInstance, "scheduled");
    }

    private void testInstance(ScheduledThreadPoolExecutor theInstance,
                              String namePrefix,
                              int corePoolSize,
                              boolean shouldBeDaemon) throws ExecutionException, InterruptedException {
        // test that we did indeed create an executor service
        assertThat(theInstance, notNullValue());

        // test that pool size is configured correctly
        assertThat(theInstance.getCorePoolSize(), is(corePoolSize));

        AtomicReference<String> threadName = new AtomicReference<>();
        theInstance.submit(() -> threadName.set(Thread.currentThread().getName())).get();

        assertThat(threadName.get(), startsWith(namePrefix));

        AtomicReference<Boolean> isDaemon = new AtomicReference<>();
        theInstance.submit(() -> isDaemon.set(Thread.currentThread().isDaemon())) .get();

        assertThat(isDaemon.get(), is(shouldBeDaemon));
    }

    private void checkObserver(ScheduledThreadPoolSupplier supplier, ScheduledThreadPoolExecutor theInstance, String supplierCategory) {
        assertThat("Supplier collection",
                   ObserverForTesting.instance.suppliers(),
                   hasKey(supplier));
        ObserverForTesting.SupplierInfo supplierInfo = ObserverForTesting.instance.suppliers().get(supplier);
        assertThat("Observer supplier category",
                   supplierInfo.supplierCategory(),
                   is(supplierCategory));
        assertThat("ExecutorService",
                   supplierInfo.context().executorServices(),
                   hasItem(theInstance));
        ObserverForTesting.Context ctx = supplierInfo.context();
        assertThat("Count of scheduled executor services registered", ctx.scheduledCount(), is(not(0)));
        assertThat("Count of non-scheduled executor services registered", ctx.threadPoolCount(), is(0));
    }
}