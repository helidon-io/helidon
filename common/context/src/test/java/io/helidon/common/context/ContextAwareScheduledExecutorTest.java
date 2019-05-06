/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.common.context;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link ContextAwareExecutorImpl}.
 */
public class ContextAwareScheduledExecutorTest {
    private static final String TEST_STRING = "someStringToTest";

    @Test
    void testSchedule() throws Exception {
        ScheduledExecutorService wrapped = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService wrapper = Contexts.wrap(wrapped);


        CountDownLatch cdl = new CountDownLatch(1);
        Context context = Context.create();
        context.register(cdl);

        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        Contexts.runInContext(context, () -> {
            futureRef.set(wrapper.schedule(() -> {
                try {
                    Contexts.context()
                            .get()
                            .get(CountDownLatch.class)
                            .get()
                            .countDown();
                } catch (Exception e) {
                    exceptionRef.set(e);
                }
            }, 100, TimeUnit.MILLISECONDS));
        });

        try {
            if (exceptionRef.get() != null) {
                throw exceptionRef.get();
            }
            if (cdl.await(1, TimeUnit.SECONDS)) {
                assertThat(futureRef.get().get(), nullValue());
            } else {
                fail("Timed out after 1 second waiting for the scheduled task");
            }
        } finally {
            wrapper.shutdownNow();
        }
    }

    @Test
    void testScheduleCallable() throws Exception {
        ScheduledExecutorService wrapped = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService wrapper = Contexts.wrap(wrapped);


        Context context = Context.create();
        context.register(TEST_STRING);

        AtomicReference<ScheduledFuture<String>> futureRef = new AtomicReference<>();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();

        Contexts.runInContext(context, () -> {
            futureRef.set(wrapper.schedule(() -> {
                try {
                    return Contexts.context().get().get(String.class).get();
                } catch (Exception e) {
                    exceptionRef.set(e);
                    return null;
                }
            }, 100, TimeUnit.MILLISECONDS));
        });

        if (exceptionRef.get() != null) {
            throw exceptionRef.get();
        }

        ScheduledFuture<String> future = futureRef.get();
        assertThat(future.get(1, TimeUnit.SECONDS), is(TEST_STRING));

        wrapper.shutdownNow();
    }

    @Test
    void testScheduleAtFixedRate() throws InterruptedException, ExecutionException {
        ScheduledExecutorService wrapped = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService wrapper = Contexts.wrap(wrapped);


        CountDownLatch cdl = new CountDownLatch(2);

        Context context = Context.create();
        context.register(cdl);

        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        Contexts.runInContext(context, () -> {
            futureRef.set(wrapper.scheduleAtFixedRate(() -> {
                    Contexts.context()
                            .get()
                            .get(CountDownLatch.class)
                            .get()
                            .countDown();
                }, 10, 100, TimeUnit.MILLISECONDS));
        });

        if (cdl.await(1, TimeUnit.SECONDS)) {
            futureRef.get().cancel(true);
        } else {
            fail("Timed out after 1 second waiting for the scheduled task");
        }

        wrapper.shutdownNow();
    }

    @Test
    void testScheduleAtFixedDelay() throws InterruptedException, ExecutionException {
        ScheduledExecutorService wrapped = Executors.newSingleThreadScheduledExecutor();
        ScheduledExecutorService wrapper = Contexts.wrap(wrapped);

        CountDownLatch cdl = new CountDownLatch(2);

        Context context = Context.create();
        context.register(cdl);
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();


        Contexts.runInContext(context, () -> {
            futureRef.set(wrapper.scheduleWithFixedDelay(() -> {
                Contexts.context()
                        .get()
                        .get(CountDownLatch.class)
                        .get()
                        .countDown();
            }, 10, 100, TimeUnit.MILLISECONDS));
        });

        if (cdl.await(1, TimeUnit.SECONDS)) {
            futureRef.get().cancel(true);
        } else {
            fail("Timed out after 1 second waiting for the scheduled task");
        }

        wrapper.shutdownNow();
    }
}
