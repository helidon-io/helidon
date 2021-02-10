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

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class SingleRetryTest {

    @Test
    public void negativeCount() {
        assertThrows(IllegalArgumentException.class, () -> Single.just(1).retry(-1));
    }

    @Test
    public void predicate() {
        AtomicInteger count = new AtomicInteger();
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.defer(() ->
                count.incrementAndGet() < 5
                        ? Single.error(new IOException()) : Single.error(new IllegalArgumentException()))
                .retry((e, n) -> e instanceof IOException)
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(count.get(), 5);
    }

    @Test
    public void whenFunctionNull() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .retryWhen((e, n) -> null)
                .subscribe(ts);

        ts.assertFailure(NullPointerException.class);
        assertTrue(ts.getLastError().getSuppressed()[0] instanceof IOException, "" + ts.getLastError());
    }

    @Test
    public void whenFunctionCrash() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .retryWhen((e, n) -> {
                    throw new IllegalArgumentException();
                })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertTrue(ts.getLastError().getSuppressed()[0] instanceof IOException, "" + ts.getLastError());
    }

    @Test
    public void whenFunctionNoSelfSuppression() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IllegalArgumentException())
                .retryWhen((e, n) -> {
                    throw (RuntimeException) e;
                })
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(ts.getLastError().getSuppressed().length, 0, "" + ts.getLastError());
    }

    @Test
    public void whenFunctionComplete() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .retryWhen((e, n) -> Single.empty())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void whenFunctionComplete2() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .retryWhen((e, n) -> Multi.empty())
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void whenFunctionError() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .retryWhen((e, n) -> Multi.error(new IllegalArgumentException()))
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(ts.getLastError().getSuppressed().length, 0, "" + ts.getLastError());
    }

    @Test
    public void whenFunctionCompleteHidden() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .retryWhen((e, n) -> Single.empty().map(v -> v))
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void whenFunctionErrorHidden() {
        TestSubscriber<Object> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .retryWhen((e, n) -> Multi.error(new IllegalArgumentException())
                        .map(v -> v)
                )
                .subscribe(ts);

        ts.assertFailure(IllegalArgumentException.class);
        assertEquals(ts.getLastError().getSuppressed().length, 0, "" + ts.getLastError());
    }

    @Test
    public void timer() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        try {
            AtomicInteger count = new AtomicInteger();
            TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

            Single.defer(() -> count.incrementAndGet() < 5
                    ? Single.error(new IOException()) : Single.just(1)
            )
                    .retryWhen((e, n) -> Single.timer(100, TimeUnit.MILLISECONDS, executor))
                    .subscribe(ts);

            ts.awaitDone(5, TimeUnit.SECONDS)
                    .assertResult(1);

            assertEquals(count.get(), 5);
        } finally {
            executor.shutdown();
        }
    }
}
