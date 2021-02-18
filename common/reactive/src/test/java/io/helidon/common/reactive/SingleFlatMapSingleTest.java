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
import java.util.concurrent.SubmissionPublisher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNull.nullValue;

public class SingleFlatMapSingleTest {

    @Test
    public void emptySource() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.empty()
                .flatMapSingle(v -> Single.just(2))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));
    }

    @Test
    public void emptyInner() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1)
                .flatMapSingle(v -> Single.<Integer>empty())
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), is(nullValue()));
        assertThat(ts.isComplete(), is(true));
    }

    @Test
    public void errorSource() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.error(new IOException())
                .flatMapSingle(v -> Single.just(2))
                .subscribe(ts);

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IOException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void errorInner() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1)
                .flatMapSingle(v -> Single.<Integer>error(new IOException()))
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IOException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void cancelMain() {
        SubmissionPublisher<Integer> sp = new SubmissionPublisher<>(Runnable::run, 32);
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.create(sp)
                .flatMapSingle(v -> Single.just(2))
                .subscribe(ts);

        assertThat(sp.hasSubscribers(), is(true));

        ts.getSubcription().cancel();

        assertThat(sp.hasSubscribers(), is(false));
    }


    @Test
    public void cancelInner() {
        SubmissionPublisher<Integer> sp = new SubmissionPublisher<>(Runnable::run, 32);
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1)
                .flatMapSingle(v -> Single.create(sp))
                .subscribe(ts);

        ts.request1();

        assertThat(sp.hasSubscribers(), is(true));

        ts.getSubcription().cancel();

        assertThat(sp.hasSubscribers(), is(false));
    }

    @Test
    public void mapperCrash() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1)
                .<Integer>flatMapSingle(v -> { throw new IllegalArgumentException(); })
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(IllegalArgumentException.class));
        assertThat(ts.isComplete(), is(false));
    }

    @Test
    public void mapperNull() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();

        Single.just(1)
                .<Integer>flatMapSingle(v -> null)
                .subscribe(ts);

        ts.request1();

        assertThat(ts.getItems().isEmpty(), is(true));
        assertThat(ts.getLastError(), instanceOf(NullPointerException.class));
        assertThat(ts.isComplete(), is(false));
    }
}
