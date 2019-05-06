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

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

/**
 * Unit test for {@link io.helidon.common.context.ContextAwareExecutorImpl}.
 */
public class ContextAwareExecutorTest {
    private static final String TEST_STRING = "someStringToTest";

    @Test
    void testLifecycle() {
        ExecutorService wrapped = Executors.newFixedThreadPool(1);
        ExecutorService wrapper = Contexts.wrap(wrapped);

        assertThat("Wrapped.isShutdown", wrapped.isShutdown(), is(false));
        assertThat("Wrapper.isShutdown", wrapper.isShutdown(), is(false));
        assertThat("Wrapped.isTerminated", wrapped.isTerminated(), is(false));
        assertThat("Wrapper.isTerminated", wrapper.isTerminated(), is(false));

        List<Runnable> runnables = wrapper.shutdownNow();

        assertThat("No runnables when shutting down", runnables, empty());

        assertThat("Wrapped.isShutdown", wrapped.isShutdown(), is(true));
        assertThat("Wrapper.isShutdown", wrapper.isShutdown(), is(true));
        assertThat("Wrapped.isTerminated", wrapped.isTerminated(), is(true));
        assertThat("Wrapper.isTerminated", wrapper.isTerminated(), is(true));
    }

    @Test
    void testLifecycleWithAwaitTermination() throws InterruptedException {
        ExecutorService wrapped = Executors.newFixedThreadPool(1);
        ExecutorService wrapper = Contexts.wrap(wrapped);

        assertThat("Wrapped.isShutdown", wrapped.isShutdown(), is(false));
        assertThat("Wrapper.isShutdown", wrapper.isShutdown(), is(false));
        assertThat("Wrapped.isTerminated", wrapped.isTerminated(), is(false));
        assertThat("Wrapper.isTerminated", wrapper.isTerminated(), is(false));

        wrapper.shutdown();
        wrapper.awaitTermination(1, TimeUnit.SECONDS);


        assertThat("Wrapped.isShutdown", wrapped.isShutdown(), is(true));
        assertThat("Wrapper.isShutdown", wrapper.isShutdown(), is(true));
        assertThat("Wrapped.isTerminated", wrapped.isTerminated(), is(true));
        assertThat("Wrapper.isTerminated", wrapper.isTerminated(), is(true));
    }

    @Test
    void testInvokeAll() {
        List<TestCallable> toCall = new LinkedList<>();

        Context context = Context.create();
        ExecutorService executor = Contexts.wrap(Executors.newFixedThreadPool(1));

        for (int i = 0; i < 10; i++) {
            context.register("key_" + i, TEST_STRING);
            toCall.add(new TestCallable(i));
        }

        Contexts.runInContext(context, () -> {
            try {
                List<Future<String>> futures = executor.invokeAll(toCall);
                for (Future<String> future : futures) {
                    assertThat(future.get(), is(TEST_STRING));
                }
            } catch (Exception e) {
                throw new ExecutorException("failed to execute", e);
            }
        });
    }

    @Test
    void testInvokeAllWithTimeout() {
        List<TestCallable> toCall = new LinkedList<>();

        Context context = Context.create();
        ExecutorService executor = Contexts.wrap(Executors.newFixedThreadPool(1));

        for (int i = 0; i < 10; i++) {
            context.register("key_" + i, TEST_STRING);
            toCall.add(new TestCallable(i));
        }

        Contexts.runInContext(context, () -> {
            try {
                List<Future<String>> futures = executor.invokeAll(toCall, 1, TimeUnit.SECONDS);
                for (Future<String> future : futures) {
                    assertThat(future.get(), is(TEST_STRING));
                }
            } catch (Exception e) {
                throw new ExecutorException("failed to execute", e);
            }
        });
    }

    @Test
    void testInvokeAny() {
        List<TestCallable> toCall = new LinkedList<>();

        Context context = Context.create();
        ExecutorService executor = Contexts.wrap(Executors.newFixedThreadPool(1));

        for (int i = 0; i < 10; i++) {
            context.register("key_" + i, TEST_STRING);
            toCall.add(new TestCallable(i));
        }

        Contexts.runInContext(context, () -> {
            try {
                String result = executor.invokeAny(toCall);
                assertThat(result, is(TEST_STRING));
            } catch (Exception e) {
                throw new ExecutorException("failed to execute", e);
            }
        });
    }

    @Test
    void testInvokeAnyWithTimeout() {
        List<TestCallable> toCall = new LinkedList<>();

        Context context = Context.create();
        ExecutorService executor = Contexts.wrap(Executors.newFixedThreadPool(1));

        for (int i = 0; i < 10; i++) {
            context.register("key_" + i, TEST_STRING);
            toCall.add(new TestCallable(i));
        }

        Contexts.runInContext(context, () -> {
            try {
                String result = executor.invokeAny(toCall, 1, TimeUnit.SECONDS);
                assertThat(result, is(TEST_STRING));
            } catch (Exception e) {
                throw new ExecutorException("failed to execute", e);
            }
        });
    }

    private static final class TestCallable implements Callable<String> {
        private final int index;

        private TestCallable(int index) {
            this.index = index;
        }

        @Override
        public String call() {
            Context context = Contexts.context().get();
            return context.get("key_" + index, String.class).get();
        }
    }
}
