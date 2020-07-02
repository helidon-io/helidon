/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class BulkheadTest {
    @BeforeAll
    static void setupTest() {
        LogConfig.configureRuntime();
    }

    @Test
    void testBulkheadQueue() throws InterruptedException {
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1000)
                .build();

        Request inProgress = new Request(0);
        bulkhead.invoke(inProgress::invoke);

        Request[] aLotRequests = new Request[999];
        Single[] aLotResults = new Single[999];
        for (int i = 0; i < aLotRequests.length; i++) {
            Request req = new Request(i);
            aLotRequests[i] = req;
            aLotResults[i] = bulkhead.invoke(req::invoke);
        }

        for (Request req : aLotRequests) {
            req.releaseCdl.countDown();
        }

        inProgress.releaseCdl.countDown();
        if (inProgress.invokedCdl.await(1, TimeUnit.SECONDS)) {
            for (Single result : aLotResults) {
                result.await(1, TimeUnit.SECONDS);
            }
        } else {
            fail("Should have invoked the first");
        }
    }

    @Test
    void testBulkhead() throws InterruptedException {
        String name = "unit:testBulkhead";
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1)
                .name(name)
                .build();

        Request inProgress = new Request(0);
        Request enqueued = new Request(1);
        Request rejected = new Request(2);

        Single<Integer> inProgressResult = bulkhead.invoke(inProgress::invoke);
        Single<Integer> enqueuedResult = bulkhead.invoke(enqueued::invoke);
        Single<Integer> rejectedResult = bulkhead.invoke(rejected::invoke);

        if (!inProgress.invokedCdl.await(1, TimeUnit.SECONDS)) {
            fail("Invoke method of inProgress was not called");
        }
        assertThat(inProgress.invoked.get(), is(true));
        assertThat(inProgress.indexed.get(), is(false));
        assertThat(enqueued.invoked.get(), is(false));
        assertThat(enqueued.indexed.get(), is(false));
        assertThat(rejected.invoked.get(), is(false));
        assertThat(rejected.indexed.get(), is(false));

        inProgress.releaseCdl.countDown();
        if (!enqueued.invokedCdl.await(1, TimeUnit.SECONDS)) {
            fail("Invoke method of enqueued was not called");
        }
        assertThat(inProgress.indexed.get(), is(true));
        assertThat(enqueued.invoked.get(), is(true));
        assertThat(enqueued.indexed.get(), is(false));
        assertThat(rejected.invoked.get(), is(false));
        assertThat(rejected.indexed.get(), is(false));

        enqueued.releaseCdl.countDown();

        assertThat(inProgressResult.await(1, TimeUnit.SECONDS), is(0));
        assertThat(enqueuedResult.await(1, TimeUnit.SECONDS), is(1));
        CompletionException completionException = assertThrows(CompletionException.class,
                                                               () -> rejectedResult.await(1, TimeUnit.SECONDS));
        Throwable cause = completionException.getCause();

        assertThat(cause, notNullValue());
        assertThat(cause, instanceOf(BulkheadException.class));
        assertThat(cause.getMessage(), is("Bulkhead queue \"" + name + "\" is full"));
    }

    @Test
    void testBulkheadWithError() {
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1)
                .build();
        Single<Object> result = bulkhead.invoke(() -> Single.error(new IllegalStateException()));
        FaultToleranceTest.completionException(result, IllegalStateException.class);

        Request inProgress = new Request(0);
        bulkhead.invoke(inProgress::invoke);

        // queued
        result = bulkhead.invoke(() -> Single.error(new IllegalStateException()));
        inProgress.releaseCdl.countDown();

        FaultToleranceTest.completionException(result, IllegalStateException.class);
    }

    @Test
    void testBulkheadWithMulti() {
        Bulkhead bulkhead = Bulkhead.builder()
                .limit(1)
                .queueLength(1)
                .build();
        Single<Object> result = bulkhead.invoke(() -> Single.error(new IllegalStateException()));
        FaultToleranceTest.completionException(result, IllegalStateException.class);

        MultiRequest inProgress = new MultiRequest(0, 5);
        Multi<Integer> multi = bulkhead.invokeMulti(inProgress::invoke);

        // queued
        result = bulkhead.invoke(() -> Single.error(new IllegalStateException()));
        inProgress.releaseCdl.countDown();
        List<Integer> allInts = multi.collectList()
                .await(1, TimeUnit.SECONDS);
        assertThat(allInts, contains(0, 1, 2, 3, 4));

        FaultToleranceTest.completionException(result, IllegalStateException.class);
    }

    private static class MultiRequest {
        private final CountDownLatch releaseCdl = new CountDownLatch(1);
        private final CountDownLatch invokedCdl = new CountDownLatch(1);
        private final AtomicBoolean invoked = new AtomicBoolean();
        private final AtomicBoolean indexed = new AtomicBoolean();

        private final int index;
        private int count;

        MultiRequest(int index, int count) {
            this.index = index;
            this.count = count;
        }

        Flow.Publisher<Integer> invoke() {
            invoked.set(true);
            invokedCdl.countDown();
            return Async.create().invoke(this::index)
                    .flatMap(it -> Multi.create(IntStream.range(it, it + count).boxed()));
        }

        private int index() {
            try {
                releaseCdl.await();
            } catch (InterruptedException e) {
            }
            indexed.set(true);
            return index;
        }
    }

    private static class Request {
        private final CountDownLatch releaseCdl = new CountDownLatch(1);
        private final CountDownLatch invokedCdl = new CountDownLatch(1);
        private final AtomicBoolean invoked = new AtomicBoolean();
        private final AtomicBoolean indexed = new AtomicBoolean();

        private final int index;

        Request(int index) {
            this.index = index;
        }

        CompletionStage<Integer> invoke() {
            invoked.set(true);
            invokedCdl.countDown();
            return Async.create().invoke(this::index);
        }

        private int index() {
            try {
                releaseCdl.await();
            } catch (InterruptedException e) {
            }
            indexed.set(true);
            return index;
        }
    }
}
