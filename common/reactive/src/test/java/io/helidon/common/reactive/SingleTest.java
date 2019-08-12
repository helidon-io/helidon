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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * {@link Single} test.
 */
public class SingleTest {

    @Test
    public void testSingleJust() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("foo"));
    }

    @Test
    public void testSingleEmpty() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Single.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testSingleEmptyToFuture() {
        CompletableFuture<Object> future = Single.<Object>empty().toFuture();
        assertThat(future.isCompletedExceptionally(), is(equalTo(true)));
        future.exceptionally((ex) -> {
            assertThat(ex, is(instanceOf(IllegalStateException.class)));
           return null;
        });
    }

    @Test
    public void testSingleError() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Single.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(notNullValue()));
        assertThat(subscriber.error.getMessage(), is(equalTo("foo")));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testSingleNever() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Single.<Object>never().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testSingleMap() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Single.just("foo").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("FOO"));
    }

    @Test
    public void testSingleMapperSubscriptionNotCanceled() {
        TestPublisher<String> publisher = new TestPublisher<>("foo", "bar");
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Single.from(publisher).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSingleMapMany() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Single.just("f.o.o").mapMany((str) -> Multi.just(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("f", "o", "o"));
    }

    private static final class TestPublisher<T> implements Publisher<T> {

        private TestSubscription<T> subscription;
        private final T[] items;

        @SafeVarargs
        TestPublisher(T... items) {
            this.items = items;
        }

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            subscription = new TestSubscription<>(subscriber, items);
            subscriber.onSubscribe(subscription);
        }
    }

    private static final class TestSubscription<T> implements Subscription {

        private final Queue<T> items;
        private final Subscriber<? super T> subscriber;
        private boolean canceled;

        @SafeVarargs
        TestSubscription(Subscriber<? super T> subscriber, T ... items) {
            this.items = new LinkedList<>(Arrays.asList(items));
            this.subscriber = subscriber;
            this.canceled = false;
        }

        @Override
        public void request(long n) {
            if (n > 0) {
                for (; n > 0 && !items.isEmpty(); n--) {
                    subscriber.onNext(items.poll());
                }
                if (items.isEmpty()) {
                    subscriber.onComplete();
                }
            }
        }

        @Override
        public void cancel() {
            canceled = true;
        }
    }

    private static final class TestSubscriber<T> implements Subscriber<T> {

        private boolean completed;
        private Throwable error;
        private List<T> items = new LinkedList<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
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
