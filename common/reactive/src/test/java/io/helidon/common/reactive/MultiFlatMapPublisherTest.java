/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.common.reactive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

public class MultiFlatMapPublisherTest {

    Multi<Integer> items(int count) {
        return Multi.create(() -> IntStream.range(0, count).boxed().iterator());
    }

    void crossMap(int count) {
        crossMap(count, 32, 32);
    }

    void crossMap(int count, long maxConcurrent, long prefetch) {
        int inner = 1_000_000 / count;
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        items(count)
                .flatMap(v -> items(inner), maxConcurrent, false, prefetch)
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().size(), is(1_000_000));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }

    void crossMapUnbounded(int count) {
        int inner = 1_000_000 / count;
        TestSubscriber<Integer> ts = new TestSubscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };

        items(count)
                .flatMap(v -> items(inner))
                .subscribe(ts);

        assertThat(ts.getItems().size(), is(1_000_000));
        assertThat(ts.isComplete(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
    }

    @Test
    public void crossMap1() {
        crossMap(1);
    }

    @Test
    public void crossMap10() {
        crossMap(10);
    }

    @Test
    public void crossMap100() {
        crossMap(100);
    }

    @Test
    public void crossMap1000() {
        crossMap(1000);
    }

    @Test
    public void crossMap10000() {
        crossMap(10000);
    }

    @Test
    public void crossMap100000() {
        crossMap(100000);
    }

    @Test
    public void crossMap1000000() {
        crossMap(100000);
    }

    @Test
    public void crossMapMaxConcurrent1() {
        crossMap(1, 1, 1);
    }

    @Test
    public void crossMapMaxConcurrent10() {
        crossMap(10, 1, 1);
    }

    @Test
    public void crossMapMaxConcurrent100() {
        crossMap(100, 1, 1);
    }

    @Test
    public void crossMap100_10_1() {
        crossMap(100, 10, 1);
    }

    @Test
    public void crossMap100_10_10() {
        crossMap(100, 10, 10);
    }

    @Test
    public void delayError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        items(3)
                .map(v -> 6 / (1 - v))
                .flatMap(Single::just, 32, true, 32)
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Arrays.asList(6));
        assertThat(ts.getLastError(), instanceOf(ArithmeticException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void delayErrorInner() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        items(3)
                .flatMap(v -> Single.just(v).map(w -> 6 / (1 - w)),
                        32, true, 32)
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Arrays.asList(6, -6));
        assertThat(ts.getLastError(), instanceOf(ArithmeticException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void cancel() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        SubmissionPublisher<Integer> sp1 = new SubmissionPublisher<>(Runnable::run, 32);
        SubmissionPublisher<Integer> sp2 = new SubmissionPublisher<>(Runnable::run, 32);

        Multi.create(sp1)
                .flatMap(v -> sp2)
                .subscribe(ts);

        assertThat(sp1.hasSubscribers(), is(true));
        assertThat(sp2.hasSubscribers(), is(false));

        sp1.submit(1);

        assertThat(sp1.hasSubscribers(), is(true));
        assertThat(sp2.hasSubscribers(), is(true));

        ts.getSubcription().cancel();

        assertThat(sp1.hasSubscribers(), is(false));
        assertThat(sp2.hasSubscribers(), is(false));
    }

    @Test
    public void empty() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>empty()
                .flatMap(Single::just)
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));
    }

    @Test
    public void crossMapUnbounded1() {
        crossMapUnbounded(1);
    }

    @Test
    public void crossMapUnbounded10() {
        crossMapUnbounded(10);
    }

    @Test
    public void crossMapUnbounded100() {
        crossMapUnbounded(100);
    }

    @Test
    public void crossMapUnbounded1000() {
        crossMapUnbounded(1000);
    }

    @Test
    public void crossMapUnbounded10000() {
        crossMapUnbounded(10_000);
    }

    @Test
    public void crossMapUnbounded100000() {
        crossMapUnbounded(100_000);
    }

    @Test
    public void crossMapUnbounded1000000() {
        crossMapUnbounded(1_000_000);
    }

    @Test
    public void justJust() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Multi.singleton(1)
                .flatMap(Single::just)
                .subscribe(ts);

        ts.request1();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));
    }

    @Test
    public void justJustUnbounded() {
        TestSubscriber<Integer> ts = new TestSubscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        Multi.singleton(1)
                .flatMap(Single::just)
                .subscribe(ts);

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));
    }

    @Test
    public void flatMapCompletionStage() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.just(CompletableFuture.completedFuture(1), CompletableFuture.completedFuture(2))
                .flatMap(Multi::create, 1, false, 1)
                .subscribe(ts);

        ts.assertEmpty();

        ts.request(1)
                .assertValuesOnly(1)
                .request(1)
                .assertResult(1, 2);
    }

    static final int UPSTREAM_ITEM_COUNT = 100;
    static final int ASYNC_MULTIPLY = 10;
    static final int EXPECTED_EMISSION_COUNT = 1000;
    static final int MAX_CONCURRENCY = 128;
    static final int PREFETCH = 128;
    static final List<Integer> TEST_DATA = IntStream.rangeClosed(1, UPSTREAM_ITEM_COUNT)
            .boxed()
            .collect(Collectors.toList());

    @RepeatedTest(500)
    public void multi() throws ExecutionException, InterruptedException {
        assertEquals(EXPECTED_EMISSION_COUNT, Multi.create(TEST_DATA)
                .flatMap(MultiFlatMapPublisherTest::asyncFlowPublisher, MAX_CONCURRENCY, false, PREFETCH)
                .distinct()
                .collectList()
                .await(800, TimeUnit.MILLISECONDS)
                .size());
    }

    private static Flow.Publisher<? extends String> asyncFlowPublisher(Integer i) {
        BufferedEmittingPublisher<String> pub = BufferedEmittingPublisher.create();
        new Thread(() -> {
            for (int o = 0; o < ASYNC_MULTIPLY; o++) {
                pub.emit(i + "#" + o);
            }
            pub.complete();
        }).start();
        return pub;
    }

    @Test
    public void innerSourceOrderPreserved() {
        ExecutorService executor1 = Executors.newSingleThreadExecutor();
        ExecutorService executor2 = Executors.newSingleThreadExecutor();
        try {
            for (int p = 1; p < 256; p *= 2) {
                for (int i = 0; i < 1000; i++) {
                    TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

                    Multi.just(
                            Multi.range(1, 100).observeOn(executor1),
                            Multi.range(200, 100).observeOn(executor2)
                    )
                            .flatMap(v -> v, 3, false, p)
                            .subscribe(ts);

                    ts.awaitDone(5, TimeUnit.SECONDS)
                            .assertItemCount(200)
                            .assertComplete();

                    int last1 = 0;
                    int last2 = 199;
                    for (Integer v : ts.getItems()) {
                        if (v < 200) {
                            if (last1 + 1 != v) {
                                fail("Out of order items: " + last1 + " -> " + v + " (p: " + p + ")");
                            }
                            last1 = v;
                        } else {
                            if (last2 + 1 != v) {
                                fail("Out of order items: " + last2 + " -> " + v + " (p: " + p + ")");
                            }
                            last2 = v;
                        }
                    }
                }
            }
        } finally {
            executor1.shutdown();
            executor2.shutdown();
        }
    }
}
