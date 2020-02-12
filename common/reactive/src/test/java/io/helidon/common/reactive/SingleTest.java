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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link Single} test.
 */
public class SingleTest {

    @Test
    public void testJust() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testJustCanceledSubscription() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
                subscription.request(Long.MAX_VALUE);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testJustNegativeSubscription() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(-1);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testJustNegativeCanceledSubscription() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
                subscription.request(-1);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testJustDoubleSubscriptionRequest() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
                subscription.request(1);
            }
        };
        Single.<String>just("foo").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testEmpty() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<>();
        Single.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testEmptyCanceledSubscription() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<Object>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
            }
        };
        Single.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testError() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<>();
        Single.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNever() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<>();
        Single.<Object>never().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("foo").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("FOO"));
    }

    @Test
    public void testMapNullMapper() {
        try {
            Single.just("foo").map(null);
            fail("NullPointerException should be thrown");
        } catch(NullPointerException ex) {
        }
    }

    @Test
    public void testMapBadMapperNullValue() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("bar").map((s) -> (String)null).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testErrorMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>error(new IllegalStateException("foo!")).map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testEmptyMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>empty().map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverMap() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>never().map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFromPublisher() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.from(new TestPublisher<>("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testFromPublisherMoreThanOne() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.from(new TestPublisher<>("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFromSingle() {
        Single<String> single = Single.just("foo");
        assertThat(Single.from(single), is(equalTo(single)));
    }

    @Test
    public void testMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("f.o.o")
                .mapMany((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("f", "o", "o"));
    }

    @Test
    public void testEmptyMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>empty()
                .mapMany((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testErrorMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>error(new IllegalStateException("foo!"))
                .mapMany((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>never()
                .mapMany((str) -> new TestPublisher<>(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapManyNullMapper() {
        try {
            Single.just("bar").map(null);
            fail("NullPointerException should have been thrown");
        } catch(NullPointerException ex) {
        }
    }

    @Test
    public void testMapManyMapperNullValue() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("bar").mapMany((s) -> (Multi<String>) null).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testBadSingleToFuture() throws InterruptedException, TimeoutException {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                throw new IllegalStateException("foo!");
            }
        };
        try {
            single.get(1, TimeUnit.SECONDS);
        } catch(ExecutionException ex) {
           assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    public void testEmptyToFuture() throws InterruptedException, TimeoutException {
        try {
            Single.<Object>empty().get(1, TimeUnit.SECONDS);
        } catch(ExecutionException ex) {
           assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    public void testToFutureDoubleOnError() throws InterruptedException, TimeoutException {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscriber.onError(new IllegalStateException("foo!"));
                        subscriber.onError(new IllegalStateException("foo!"));
                    }

                    @Override
                    public void cancel() {
                    }
                });
            }
        };
        try {
            single.get(1, TimeUnit.SECONDS);
        } catch(ExecutionException ex) {
           assertThat(ex.getCause(), is(instanceOf(IllegalStateException.class)));
        }
    }

    @Test
    public void testToFutureDoubleOnNext() throws InterruptedException, ExecutionException {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscriber.onNext("foo");
                        subscriber.onNext("bar");
                        subscriber.onComplete();
                    }

                    @Override
                    public void cancel() {
                    }
                });
            }
        };
        Future<String> future = single.toStage().toCompletableFuture();
        assertThat(future.isDone(), is(equalTo(true)));
        assertThat(future.get(), is(equalTo("foo")));
    }

    @Test
    public void testToFutureCancel() throws InterruptedException, ExecutionException {
        Future<String> future = Single.just("foo").toStage().toCompletableFuture();
        assertThat(future.cancel(true), is(equalTo(false)));
        assertThat(future.get(), is(equalTo("foo")));
    }

    @Test
    public void testNeverToFutureCancel() throws InterruptedException, ExecutionException {
        Future<String> future = Single.<String>never().toStage().toCompletableFuture();
        assertThat(future.cancel(true), is(equalTo(true)));
        assertThat(future.isCancelled(), is(equalTo(true)));
    }

    @Test
    public void testNeverToFutureDoubleCancel() throws InterruptedException, ExecutionException {
        Future<String> future = Single.<String>never().toStage().toCompletableFuture();
        assertThat(future.cancel(true), is(equalTo(true)));
        assertThat(future.cancel(true), is(equalTo(true)));
        assertThat(future.isCancelled(), is(equalTo(true)));
    }

    @Test
    public void testToFutureDoubleOnSubscribe() throws InterruptedException, ExecutionException {
        TestSubscription subscription1 = new TestSubscription();
        TestSubscription subscription2 = new TestSubscription();
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(subscription1);
                subscriber.onSubscribe(subscription2);
            }
        };
        Future<String> future = single.toStage().toCompletableFuture();
        assertThat(future.isDone(), is(equalTo(false)));
        assertThat(subscription1.canceled, is(equalTo(true)));
        assertThat(subscription2.canceled, is(equalTo(true)));
    }

    private static class SingleTestSubscriber<T> extends TestSubscriber<T> {

        @Override
        public void onSubscribe(Subscription subscription) {
            super.onSubscribe(subscription);
            requestMax();
        }
    }

}
