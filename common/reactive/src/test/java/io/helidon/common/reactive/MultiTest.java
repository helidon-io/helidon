/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

import org.junit.jupiter.api.Test;

/**
 * {@link MultiTest} test.
 */
public class MultiTest {

    @Test
    public void testMultiJust() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Multi.<String>just("foo", "bar").subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("foo", "bar"));
    }

    @Test
    public void testMultiEmpty() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Multi.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testMultiError() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Multi.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(notNullValue()));
        assertThat(subscriber.error.getMessage(), is(equalTo("foo")));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testMultiNever() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Multi.<Object>never().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testMultiMapper() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Multi.just("foo", "bar").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("FOO", "BAR"));
    }

    @Test
    public void testMultiErrorCollect() {
        TestSubscriber<List<String>> subscriber = new TestSubscriber<>();
        Multi.<String>error(new Exception("foo")).collectList().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(notNullValue()));
        assertThat(subscriber.error.getMessage(), is(equalTo("foo")));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testMultiCollectList() {
        TestSubscriber<List<String>> subscriber = new TestSubscriber<>();
        Multi.just("foo", "bar").collectList().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items.get(0), hasItems("foo", "bar"));
    }

    @Test
    public void testMultiCollectString() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Multi.just("foo", "bar").collectString().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("foobar"));
    }

    private static final class TestSubscriber<T> implements Subscriber<T> {

        private boolean completed;
        private Throwable error;
        private List<T> items = new LinkedList<>();

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable ex) {
            error = ex;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }
}
