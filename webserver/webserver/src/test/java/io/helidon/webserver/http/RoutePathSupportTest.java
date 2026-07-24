/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.common.context.Context;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

class RoutePathSupportTest {

    @Test
    void noConsumersDoesNotInvokeSupplier() {
        Context context = Context.create();
        AtomicInteger invocations = new AtomicInteger();

        RoutePathSupport.provideRoute(context, () -> {
            invocations.incrementAndGet();
            return "/test";
        });

        assertThat(invocations.get(), is(0));
    }

    @Test
    void consumerReceivesMemoizedSupplier() {
        Context context = Context.create();
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<Supplier<String>> routeSupplier = new AtomicReference<>();

        RoutePathSupport.requestRoute(context, routeSupplier::set);
        RoutePathSupport.provideRoute(context, () -> {
            invocations.incrementAndGet();
            return "/test";
        });

        assertThat(routeSupplier.get(), notNullValue());
        assertThat(routeSupplier.get().get(), is("/test"));
        assertThat(routeSupplier.get().get(), is("/test"));
        assertThat(invocations.get(), is(1));
    }

    @Test
    void multipleConsumersReceiveSameMemoizedSupplier() {
        Context context = Context.create();
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<Supplier<String>> firstSupplier = new AtomicReference<>();
        AtomicReference<Supplier<String>> secondSupplier = new AtomicReference<>();

        RoutePathSupport.requestRoute(context, firstSupplier::set);
        RoutePathSupport.requestRoute(context, secondSupplier::set);
        RoutePathSupport.provideRoute(context, () -> {
            invocations.incrementAndGet();
            return "/test";
        });

        assertThat(firstSupplier.get(), sameInstance(secondSupplier.get()));
        assertThat(firstSupplier.get().get(), is("/test"));
        assertThat(secondSupplier.get().get(), is("/test"));
        assertThat(invocations.get(), is(1));
    }

    @Test
    void concurrentConsumersComputeRouteOnce() throws InterruptedException {
        Context context = Context.create();
        AtomicInteger invocations = new AtomicInteger();
        AtomicReference<Supplier<String>> routeSupplier = new AtomicReference<>();
        ConcurrentLinkedQueue<String> routes = new ConcurrentLinkedQueue<>();
        int threadCount = 16;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        RoutePathSupport.requestRoute(context, routeSupplier::set);
        RoutePathSupport.provideRoute(context, () -> {
            invocations.incrementAndGet();
            return "/test";
        });

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    routes.add(routeSupplier.get().get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }

        assertThat(ready.await(5, TimeUnit.SECONDS), is(true));
        start.countDown();
        assertThat(done.await(5, TimeUnit.SECONDS), is(true));

        assertThat(routes, hasSize(threadCount));
        assertThat(routes, everyItem(is("/test")));
        assertThat(invocations.get(), is(1));
    }
}
