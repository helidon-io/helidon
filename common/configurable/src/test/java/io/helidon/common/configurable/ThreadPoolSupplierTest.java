/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ThreadPoolSupplier}.
 */
class ThreadPoolSupplierTest {
    private static ThreadPoolExecutor builtInstance;
    private static ThreadPoolExecutor configuredInstance;
    private static ThreadPoolExecutor defaultInstance;

    @BeforeAll
    static void initClass() {
        defaultInstance = ThreadPoolSupplier.create().getThreadPool();

        builtInstance = ThreadPoolSupplier.builder()
                .threadNamePrefix("thread-pool-unit-test-")
                .corePoolSize(2)
                .daemon(true)
                .prestart(true)
                .queueCapacity(10)
                .build()
                .getThreadPool();

        configuredInstance = ThreadPoolSupplier.create(Config.create().get("unit.thread-pool"))
                .getThreadPool();
    }

    @Test
    void testDefaultInstance() throws ExecutionException, InterruptedException {
        testInstance(defaultInstance,
                     "helidon-",
                     10,
                     10000,
                     10,
                     true);
    }

    @Test
    void testConfiguredInstance() throws ExecutionException, InterruptedException {
        testInstance(configuredInstance,
                     "thread-pool-config-unit-test-",
                     3,
                     15,
                     0,
                     false);
    }

    @Test
    void testBuiltInstance() throws ExecutionException, InterruptedException {
        testInstance(builtInstance,
                     "thread-pool-unit-test-",
                     2,
                     10,
                     2,
                     true);
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
        assertThat(theInstance.getCorePoolSize(), is(corePoolSize));
        // test that queue-capacity is configured correctly
        assertThat(theInstance.getQueue().remainingCapacity(), is(queueCapacity));
        // test that prestart is configured correctly
        assertThat(theInstance.getPoolSize(), is(poolSize));

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
}