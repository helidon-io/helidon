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

import io.helidon.common.reactive.Flow.Publisher;
import java.util.List;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * {@link Mono} test.
 */
public class MonoTest {

    @Test
    public void testMonoJust() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Mono.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("foo"));
    }

    @Test
    public void testMonoBlock() {
        String foo = Mono.<String>just("foo").block(Duration.ofSeconds(5));
        assertThat(foo, is(equalTo("foo")));
    }

    @Test
    public void testMonoEmpty() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Mono.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testMonoError() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Mono.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(notNullValue()));
        assertThat(subscriber.error.getMessage(), is(equalTo("foo")));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testMonoNever() {
        TestSubscriber<Object> subscriber = new TestSubscriber<>();
        Mono.<Object>never().subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(false)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, is(empty()));
    }

    @Test
    public void testMonoMapper() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Mono.just("foo").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("FOO"));
    }

    @Test
    public void testMonoMapperSubscriptionNotCanceled() {
        TestPublisher<String> publisher = new TestPublisher<>("foo", "bar");
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Mono.from(publisher).map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.completed, is(equalTo(true)));
        assertThat(subscriber.error, is(nullValue()));
        assertThat(subscriber.items, hasItems("FOO"));
        assertThat(publisher.subscription, is(notNullValue()));
        assertThat(publisher.subscription.canceled, is(equalTo(false)));
    }

    @Test
    public void testMonoMultiMapper() {
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        Mono.just("f.o.o").mapMany((str) -> Multi.just(str.split("\\.")))
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
