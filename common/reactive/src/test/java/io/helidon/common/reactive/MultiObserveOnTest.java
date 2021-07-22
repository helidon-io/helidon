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

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class MultiObserveOnTest {

    private static ScheduledExecutorService executor;

    @BeforeAll
    public static void beforeClass() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @AfterAll
    public static void afterClass() {
        executor.shutdown();
    }

    @Test
    public void longSequence() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.range(1, 1000)
                .observeOn(executor)
                .subscribe(ts);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertItemCount(1000)
                .assertComplete();
    }

    @Test
    public void longSequence2() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.range(1, 1000)
                .observeOn(executor, Flow.defaultBufferSize(), false)
                .subscribe(ts);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertItemCount(1000)
                .assertComplete();
    }

    @Test
    public void longSequence3() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.range(1, 1_000_000)
                .observeOn(executor, Flow.defaultBufferSize(), false)
                .subscribe(ts);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertItemCount(1_000_000)
                .assertComplete();
    }

    @Test
    public void oneByOne() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.range(1, 1000)
                .observeOn(executor, 1, false)
                .subscribe(ts);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertItemCount(1000)
                .assertComplete();
    }

    @Test
    public void delayError() {
        TestSubscriber<Object> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.concat(Multi.range(1, 1000), Multi.error(new IOException()))
                .observeOn(executor, Flow.defaultBufferSize(), true)
                .subscribe(ts);

        ts.awaitDone(5, TimeUnit.SECONDS)
                .assertItemCount(1000)
                .assertError(IOException.class);
    }

    @Test
    public void zeroBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> Multi.range(1, 5).observeOn(executor, 0, false));
    }

    @Test
    public void negativeBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> Multi.range(1, 5).observeOn(executor, -1, false));
    }
}
