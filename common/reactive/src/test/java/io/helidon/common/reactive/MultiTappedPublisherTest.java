/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultiTappedPublisherTest {

    @Test
    public void onSubscribeCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        new MultiTappedPublisher<>(
                Multi.<Integer>empty(),
                s -> { throw new IllegalArgumentException(); },
                null,
                null,
                null,
                null,
                null
        )
        .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }


    @Test
    public void onSubscribeNormal() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        new MultiTappedPublisher<>(
                Multi.<Integer>empty(),
                s -> { calls.getAndIncrement(); },
                null,
                null,
                null,
                null,
                null
        )
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(1));
    }

    @Test
    public void onNextCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.singleton(1)
        .peek(v -> { throw new IllegalArgumentException(); })
        .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void onErrorCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>error(new IOException())
                .onError(v -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IOException.class));
        assertThat(ts.getLastError().getSuppressed()[0], instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void onCompleteCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>empty()
                .onComplete(() -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void onTerminateCrashWhenCompleted() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>empty()
                .onTerminate(() -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void onTerminateCrashWhenError() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>error(new IOException())
                .onTerminate(() -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IOException.class));
        assertThat(ts.getLastError().getSuppressed()[0], instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void onRequestCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        new MultiTappedPublisher<>(
                Multi.singleton(1),
                null,
                null,
                null,
                null,
                r -> {
                    calls.getAndIncrement();
                    throw new IllegalArgumentException();
                },
                null
        )
        .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(1));
    }


    @Test
    public void onRequestPass() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        new MultiTappedPublisher<>(
                Multi.singleton(1),
                null,
                null,
                null,
                null,
                r -> {
                    calls.getAndIncrement();
                },
                null
        )
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(1));
    }

    @Test
    public void onRequestCrashAndThenOnErrorPass() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        new MultiTappedPublisher<>(
                Multi.singleton(1),
                null,
                null,
                e -> {
                    calls.getAndIncrement();
                },
                null,
                r -> {
                    calls.getAndIncrement();
                    throw new IllegalArgumentException();
                },
                null
        )
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void onRequestCrashAndThenOnErrorCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        new MultiTappedPublisher<>(
                Multi.singleton(1),
                null,
                null,
                e -> {
                    calls.getAndIncrement();
                    throw new IllegalArgumentException();
                },
                null,
                r -> {
                    calls.getAndIncrement();
                    throw new IllegalArgumentException();
                },
                null
        )
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void onCancelCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        Multi.singleton(1).onCancel(
        () -> {
            calls.getAndIncrement();
            throw new IllegalArgumentException();
        })
        .subscribe(ts);

        ts.getSubcription().cancel();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(false));

        assertThat(calls.get(), is(1));
    }

    @Test
    public void onCancelCrashThenOnErrorPass() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        new MultiTappedPublisher<>(
                Multi.singleton(1),
                null,
                null,
                e -> {
                    calls.getAndIncrement();
                },
                null,
                null,
                () -> {
                    calls.getAndIncrement();
                    throw new IllegalArgumentException();
                }
        )
                .subscribe(ts);

        ts.getSubcription().cancel();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(false));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void onCancelCrashThenOnErrorCrash() {

        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();

        new MultiTappedPublisher<>(
                Multi.singleton(1),
                null,
                null,
                e -> {
                    calls.getAndIncrement();
                    throw new IllegalArgumentException();
                },
                null,
                null,
                () -> {
                    calls.getAndIncrement();
                    throw new IllegalArgumentException();
                }
        )
                .subscribe(ts);

        ts.getSubcription().cancel();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(false));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void peakTwice() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.singleton(1)
                .peek(v -> calls.getAndIncrement())
                .peek(v -> calls.getAndIncrement())
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void onCompleteTwice() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.singleton(1)
                .onComplete(calls::getAndIncrement)
                .onComplete(calls::getAndIncrement)
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void onTerminateTwice() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.singleton(1)
                .onTerminate(calls::getAndIncrement)
                .onTerminate(calls::getAndIncrement)
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void onErrorTwice() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.singleton(1)
                .onError(e -> calls.getAndIncrement())
                .onError(e -> calls.getAndIncrement())
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(0));
    }

    @Test
    public void onErrorTwiceFailure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.<Integer>error(new IOException())
                .onError(e -> calls.getAndIncrement())
                .onError(e -> calls.getAndIncrement())
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IOException.class));
        assertThat(ts.isComplete(), is(false));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void onTerminateTwiceFailure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.<Integer>error(new IOException())
                .onTerminate(calls::getAndIncrement)
                .onTerminate(calls::getAndIncrement)
                .subscribe(ts);

        ts.requestMax();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IOException.class));
        assertThat(ts.isComplete(), is(false));

        assertThat(calls.get(), is(2));
    }

    @Test
    public void allAppliedMultipleTimes() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.singleton(1)
                .peek(v -> calls.getAndIncrement())
                .peek(v -> calls.getAndIncrement())
                .peek(v -> calls.getAndIncrement())
                .onComplete(calls::getAndIncrement)
                .onComplete(calls::getAndIncrement)
                .onComplete(calls::getAndIncrement)
                .onError(v -> calls.getAndIncrement())
                .onError(v -> calls.getAndIncrement())
                .onError(v -> calls.getAndIncrement())
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(6));
    }

    @Test
    public void allAppliedMultipleTimes2() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        AtomicInteger calls = new AtomicInteger();
        Multi.singleton(1)
                .peek(v -> calls.getAndIncrement())
                .onComplete(calls::getAndIncrement)
                .onError(v -> calls.getAndIncrement())
                .peek(v -> calls.getAndIncrement())
                .onComplete(calls::getAndIncrement)
                .onError(v -> calls.getAndIncrement())
                .peek(v -> calls.getAndIncrement())
                .onComplete(calls::getAndIncrement)
                .onError(v -> calls.getAndIncrement())
                .subscribe(ts);

        ts.requestMax();

        assertEquals(ts.getItems(), Collections.singletonList(1));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));

        assertThat(calls.get(), is(6));
    }
}
