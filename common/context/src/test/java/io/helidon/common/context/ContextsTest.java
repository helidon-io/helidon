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

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for {@link Contexts}.
 */
class ContextsTest {
    private static final String TEST_STRING = "yes!";
    private static final Optional<String> TEST_STRING_OPTIONAL = Optional.of(TEST_STRING);

    private static ExecutorService service;

    @BeforeAll
    static void initClass() {
        service = Contexts.wrap(new ThreadPoolExecutor(2,
                                                       2,
                                                       10,
                                                       TimeUnit.SECONDS,
                                                       new ArrayBlockingQueue<>(
                                                               10),
                                                       (ThreadFactory) Thread::new));
    }

    @Test
    void testPlainExecuteRunnable() throws InterruptedException {
        AtomicReference<String> ref = new AtomicReference<>();
        CountDownLatch cdl = new CountDownLatch(1);

        Runnable runnable = () -> {
            ref.set(TEST_STRING);
            cdl.countDown();
        };

        service.execute(runnable);

        cdl.await();

        assertThat(ref.get(), is(TEST_STRING));
    }

    @Test
    void testPlainSubmitRunnable() throws InterruptedException, ExecutionException {
        AtomicReference<String> ref = new AtomicReference<>();

        Runnable runnable = () -> ref.set(TEST_STRING);

        Future<?> future = service.submit(runnable);
        future.get();

        assertThat(ref.get(), is(TEST_STRING));
    }

    @Test
    void testPlainSubmitRunnableWithResult() throws InterruptedException, ExecutionException {
        AtomicReference<String> ref = new AtomicReference<>();

        Runnable runnable = () -> ref.set(TEST_STRING);

        Future<String> future = service.submit(runnable, "Hello");
        String result = future.get();

        assertThat(result, is("Hello"));
        assertThat(ref.get(), is(TEST_STRING));
    }

    @Test
    void testPlainSubmitCallable() throws InterruptedException, ExecutionException {
        Callable<String> callable = () -> TEST_STRING;

        Future<String> future = service.submit(callable);

        assertThat(future.get(), is(TEST_STRING));
    }

    @Test
    void testContextExecuteRunnable() throws InterruptedException {
        AtomicReference<String> ref = new AtomicReference<>();
        CountDownLatch cdl = new CountDownLatch(1);
        Context ctx = Context.create();
        ctx.register("message", TEST_STRING);

        Runnable runnable = () -> {
            ref.set(Contexts.context().get().get("message", String.class).orElse("No context found"));
            cdl.countDown();
        };

        Contexts.runInContext(ctx, () -> service.execute(runnable));

        cdl.await();

        assertThat(ref.get(), is(TEST_STRING));
    }

    @Test
    void testContextSubmitRunnable() throws InterruptedException, ExecutionException {
        AtomicReference<String> ref = new AtomicReference<>();
        Context ctx = Context.create();
        ctx.register("message", TEST_STRING + "_2");

        Runnable runnable = () -> ref.set(Contexts.context().get().get("message", String.class).orElse("No context found"));

        Future<?> future = Contexts.runInContext(ctx, () -> service.submit(runnable));
        future.get();

        assertThat(ref.get(), is(TEST_STRING + "_2"));
    }

    @Test
    void testContextSubmitRunnableWithResult() throws InterruptedException, ExecutionException {
        AtomicReference<String> ref = new AtomicReference<>();
        Context ctx = Context.create();
        ctx.register("message", TEST_STRING + "_3");

        Runnable runnable = () -> ref.set(Contexts.context().get().get("message", String.class).orElse("No context found"));

        Future<String> future = Contexts.runInContext(ctx, () -> service.submit(runnable, "Hello"));
        String result = future.get();

        assertThat(result, is("Hello"));
        assertThat(ref.get(), is(TEST_STRING + "_3"));
    }

    @Test
    void testContextSubmitCallable() throws ExecutionException, InterruptedException {
        Callable<String> callable = () -> Contexts.context().get().get("message", String.class).orElse("No context found");

        Context ctx = Context.create();
        ctx.register("message", TEST_STRING + "_1");

        Future<String> future = Contexts.runInContext(ctx, () -> service.submit(callable));

        assertThat(future.get(), is(TEST_STRING + "_1"));
    }

    @Test
    void testContextSubmitCallableWithThrow() throws Exception {
        Callable<String> callable = () -> Contexts.context().get().get("message", String.class).orElse("No context found");

        Context ctx = Context.create();
        ctx.register("message", TEST_STRING + "_1");

        Future<String> future = Contexts.runInContextWithThrow(ctx, () -> service.submit(callable));

        assertThat(future.get(), is(TEST_STRING + "_1"));
    }

    @Test
    void testMultipleContexts() {
        Context global = Contexts.globalContext();
        global.register("global", TEST_STRING);

        Context topLevel = Context.create();
        topLevel.register("topLevel", TEST_STRING);
        topLevel.register("first", TEST_STRING);

        Context firstLevel = Context.create(topLevel);
        firstLevel.register("first", TEST_STRING + "_1");
        firstLevel.register("second", TEST_STRING);

        Contexts.runInContext(topLevel, () -> {
            Context myContext = Contexts.context().get();
            assertThat(myContext.get("global", String.class), is(TEST_STRING_OPTIONAL));
            assertThat(myContext.get("topLevel", String.class), is(TEST_STRING_OPTIONAL));
            assertThat(myContext.get("first", String.class), is(TEST_STRING_OPTIONAL));

            Contexts.runInContext(firstLevel, () -> {
                Context firstLevelContext = Contexts.context().get();
                assertThat(myContext.get("global", String.class), is(TEST_STRING_OPTIONAL));
                assertThat(firstLevelContext.get("topLevel", String.class), is(TEST_STRING_OPTIONAL));
                assertThat(firstLevelContext.get("first", String.class), is(Optional.of(TEST_STRING + "_1")));
                assertThat(firstLevelContext.get("second", String.class), is(TEST_STRING_OPTIONAL));
            });

            assertThat(myContext.get("global", String.class), is(TEST_STRING_OPTIONAL));
            assertThat(myContext.get("topLevel", String.class), is(TEST_STRING_OPTIONAL));
            assertThat(myContext.get("first", String.class), is(TEST_STRING_OPTIONAL));
        });
    }

    @Test
    void testClear() {
        Context topLevel = Context.create();
        topLevel.register("topLevel", TEST_STRING);
        topLevel.register("first", TEST_STRING);

        Contexts.push(topLevel);

        Context myContext = Contexts.context().get();
        assertThat(myContext.get("topLevel", String.class), is(TEST_STRING_OPTIONAL));
        assertThat(myContext.get("first", String.class), is(TEST_STRING_OPTIONAL));

        Contexts.clear();

        assertThat(Contexts.context(), is(Optional.empty()));
    }
}