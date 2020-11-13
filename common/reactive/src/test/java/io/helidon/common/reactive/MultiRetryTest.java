/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MultiRetryTest {

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void negativeCount() {
        Multi.just(1).retry(-1);
    }

    @Test
    public void predicate() {
        AtomicInteger count = new AtomicInteger();
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.defer(() ->
                count.incrementAndGet() < 5
                        ? Multi.error(new IOException()) : Multi.error(new IllegalArgumentException()))
                .retry((e, n) -> e instanceof IOException)
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(count.get(), 5);
    }

    @Test
    public void whenFunctionNull() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IOException())
                .retryWhen((e, n) -> null)
                .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
        assertTrue(ts.getLastError().getSuppressed()[0] instanceof IOException, "" + ts.getLastError());
    }

    @Test
    public void whenFunctionCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IOException())
                .retryWhen((e, n) -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertTrue(ts.getLastError().getSuppressed()[0] instanceof IOException, "" + ts.getLastError());
    }

    @Test
    public void whenFunctionNoSelfSuppression() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IllegalArgumentException())
                .retryWhen((e, n) -> { throw (RuntimeException)e; })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(ts.getLastError().getSuppressed().length, 0,"" + ts.getLastError());
    }

    @Test
    public void whenFunctionComplete() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IOException())
                .retryWhen((e, n) -> Multi.empty())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void whenFunctionComplete2() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IOException())
                .retryWhen((e, n) -> Multi.empty())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void whenFunctionError() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IOException())
                .retryWhen((e, n) -> Multi.error(new IllegalArgumentException()))
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(ts.getLastError().getSuppressed().length, 0,"" + ts.getLastError());
    }

    @Test
    public void whenFunctionCompleteHidden() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IOException())
                .retryWhen((e, n) -> Multi.empty().map(v -> v))
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void whenFunctionErrorHidden() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.error(new IOException())
                .retryWhen((e, n) -> Multi.error(new IllegalArgumentException())
                        .map(v -> v)
                )
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(ts.getLastError().getSuppressed().length, 0,"" + ts.getLastError());
    }

    @Test
    public void timer() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        try {
            AtomicInteger count = new AtomicInteger();
            TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

            Multi.defer(() -> count.incrementAndGet() < 5
                    ? Multi.error(new IOException()) : Multi.just(1)
            )
            .retryWhen((e, n) -> Multi.timer(100, TimeUnit.MILLISECONDS, executor))
            .subscribe(ts);

            ts.awaitDone(5, TimeUnit.SECONDS)
                    .assertResult(1);

            assertEquals(count.get(), 5);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void normal() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.range(1, 10)
                .retry(2)
                .subscribe(ts);

        ts
                .assertEmpty()
                .request(5)
                .assertValuesOnly(1, 2, 3, 4, 5)
                .request(5)
                .assertResult(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    public void someItemsAndFailure() {
        AtomicInteger count = new AtomicInteger();
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Multi.defer(() -> {
            int c = count.incrementAndGet();
            return c < 6
                    ? Multi.concat(Multi.just(c), Multi.error(new IOException()))
                    : Multi.range(6, 5);
        })
        .retry((e, n) -> e instanceof IOException)
        .subscribe(ts);

        ts
                .assertEmpty()
                .request(2)
                .assertValuesOnly(1, 2)
                .request(3)
                .assertValuesOnly(1, 2, 3, 4, 5)
                .request(5)
                .assertResult(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }
}
