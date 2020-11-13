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
import java.util.concurrent.SubmissionPublisher;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MultiSwitchIfEmptyTest {

    @Test
    public void normal() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.just(1)
                .switchIfEmpty(Multi.singleton(2))
                .subscribe(ts);

        ts.assertResult(1);
    }

    @Test
    public void fallback() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.<Integer>empty()
                .switchIfEmpty(Multi.singleton(2))
                .subscribe(ts);

        ts.assertResult(2);
    }

    @Test
    public void fallbackBackpressured() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Multi.<Integer>empty()
                .switchIfEmpty(Multi.singleton(2))
                .subscribe(ts);

        ts
                .assertEmpty()
                .request(1)
                .assertResult(2);
    }

    @Test
    public void error() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.<Integer>error(new IOException())
                .switchIfEmpty(Multi.singleton(2))
                .subscribe(ts);

        ts.assertFailure(IOException.class);
    }

    @Test
    public void fallbackError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.<Integer>empty()
                .switchIfEmpty(Multi.error(new IOException()))
                .subscribe(ts);

        ts.assertFailure(IOException.class);
    }

    @Test
    public void cancel() {
        SubmissionPublisher<Integer> sp = new SubmissionPublisher<>(Runnable::run, 128);
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.create(sp)
                .switchIfEmpty(Multi.singleton(2))
                .subscribe(ts);

        assertTrue(sp.hasSubscribers());

        ts.assertEmpty();

        ts.cancel();

        assertFalse(sp.hasSubscribers());
    }

    @Test
    public void cancelFallback() {
        SubmissionPublisher<Integer> sp = new SubmissionPublisher<>(Runnable::run, 128);
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Multi.<Integer>empty()
                .switchIfEmpty(Multi.create(sp))
                .subscribe(ts);

        assertTrue(sp.hasSubscribers());

        ts.assertEmpty();

        ts.cancel();

        assertFalse(sp.hasSubscribers());
    }

}
