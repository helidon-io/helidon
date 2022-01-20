/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import io.helidon.common.context.ContextAwareExecutorService;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for class {@link ServerThreadPoolSupplier}.
 */
class ServerThreadPoolSupplierTest {

    @BeforeAll
    static void initClass() {
        ObserverForTesting.clear();
    }

    @Test
    void testRejection() throws Exception {
        ThreadPoolSupplier supplier = ServerThreadPoolSupplier.builder()
                .name("test")
                .corePoolSize(1)
                .maxPoolSize(1)
                .queueCapacity(1)
                .build();
        ExecutorService pool = supplier.get();
        List<Task> tasks = new ArrayList<>();

        // Submit one task to consume the single thread and wait for it to be running

        Task t1 = new Task();
        tasks.add(t1);
        pool.submit(t1);
        t1.awaitRunning();

        // Submit one more task to fill the queue

        Task t2 = new Task();
        tasks.add(t2);
        pool.submit(t2);

        // Ensure that another task is rejected with the expected exception

        try {
            pool.submit(new Task());
            fail("should have failed");
        } catch (RejectedExecutionException e) {
            MatcherAssert.assertThat(e.getLocalizedMessage(), containsString("rejected by ThreadPool 'test'"));
        }

        tasks.forEach(Task::finish);
        pool.shutdown();
        assertThat(pool.awaitTermination(10, SECONDS), is(true));

        checkObserver(supplier, pool, "test", false);
    }

    static class Task implements Runnable {
        private final CountDownLatch running;
        private final CountDownLatch finish;

        Task() {
            this.running = new CountDownLatch(1);
            this.finish = new CountDownLatch(1);
        }

        void finish() {
            finish.countDown();
        }

        @Override
        public void run() {
            try {
                running.countDown();
                finish.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        void awaitRunning() throws InterruptedException {
            running.await();
        }
    }

    private void checkObserver(ThreadPoolSupplier supplier, ExecutorService theInstance, String name) {
        checkObserver(supplier, theInstance, name, true);
    }

    private void checkObserver(ThreadPoolSupplier supplier, ExecutorService theInstance, String supplierCategory, boolean expectExecutorService) {
        assertThat("Supplier collection",
                   ObserverForTesting.instance.suppliers(),
                   hasKey(supplier));
        ObserverForTesting.SupplierInfo supplierInfo = ObserverForTesting.instance.suppliers().get(supplier);
        assertThat("Observer supplier category",
                   supplierInfo.supplierCategory(),
                   CoreMatchers.is(supplierCategory));
        if (expectExecutorService) {
            assertThat("ExecutorService",
                       supplierInfo.context().executorServices(),
                       hasItem(theInstance instanceof ContextAwareExecutorService
                                       ? ((ContextAwareExecutorService) theInstance).unwrap()
                                       : theInstance));
            ObserverForTesting.Context ctx = supplierInfo.context();
            assertThat("Count of non-scheduled executor services registered", ctx.threadPoolCount(), is(not(0)));
            assertThat("Count of scheduled executor services registered", ctx.scheduledCount(), is(0));
        }
    }
}
