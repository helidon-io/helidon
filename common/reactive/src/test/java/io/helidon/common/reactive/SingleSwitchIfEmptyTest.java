/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.common.reactive;

import java.io.IOException;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SingleSwitchIfEmptyTest {

    @Test
    public void normal() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.just(1)
                .switchIfEmpty(Single.just(2))
                .subscribe(ts);

        ts.assertResult(1);
    }

    @Test
    public void fallback() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.<Integer>empty()
                .switchIfEmpty(Single.just(2))
                .subscribe(ts);

        ts.assertResult(2);
    }

    @Test
    public void fallbackEmpty() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);
        AtomicInteger count = new AtomicInteger();

        Single.<Integer>empty()
                .switchIfEmpty(Single.<Integer>empty().onComplete(count::getAndIncrement))
                .subscribe(ts);

        ts.assertResult();
    }

    @Test
    public void fallbackBackpressured() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.<Integer>empty()
                .switchIfEmpty(Single.just(2))
                .subscribe(ts);

        ts
                .assertEmpty()
                .request(1)
                .assertResult(2);
    }

    @Test
    public void error() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.<Integer>error(new IOException())
                .switchIfEmpty(Single.just(2))
                .subscribe(ts);

        ts.assertFailure(IOException.class);
    }

    @Test
    public void fallbackError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.<Integer>empty()
                .switchIfEmpty(Single.error(new IOException()))
                .subscribe(ts);

        ts.assertFailure(IOException.class);
    }

    @Test
    public void cancel() {
        SubmissionPublisher<Integer> sp = new SubmissionPublisher<>(Runnable::run, 128);
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.create(sp)
                .switchIfEmpty(Single.just(2))
                .subscribe(ts);

        assertThat(sp.hasSubscribers(), is(true));

        ts.assertEmpty();

        ts.cancel();

        assertThat(sp.hasSubscribers(), is(false));
    }

    @Test
    public void cancelOther() {
        SubmissionPublisher<Integer> sp = new SubmissionPublisher<>(Runnable::run, 128);
        TestSubscriber<Integer> ts = new TestSubscriber<>(Long.MAX_VALUE);

        Single.<Integer>empty()
                .switchIfEmpty(Single.create(sp))
                .subscribe(ts);

        assertThat(sp.hasSubscribers(), is(true));

        ts.assertEmpty();

        ts.cancel();

        assertThat(sp.hasSubscribers(), is(false));
    }

}
