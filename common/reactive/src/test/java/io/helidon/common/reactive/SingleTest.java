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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;
import java.util.concurrent.atomic.AtomicReference;

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
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<>();
        try {
            Single.just("foo").map(null).subscribe(subscriber);
            fail("IllegalArgumentException should be thrown");
        } catch(IllegalArgumentException ex) {
        }
    }

    @Test
    public void testMapDoubleOnNext() {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onNext("foo");
                subscriber.onNext("bar");
            }
        };
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        single.map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("FOO"));
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
    public void testMapDoubleOnError() {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onError(new IllegalStateException("foo!"));
                subscriber.onError(new IllegalStateException("bar!"));
            }
        };
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        single.map(String::toUpperCase).subscribe(subscriber);
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
    public void testMapDoubleSubscribe() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single<String> single = Single.just("foo").map(String::toUpperCase);
        single.subscribe(subscriber);
        single.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("FOO"));
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
    public void testMapperSubscriptionNotCanceled() {
        TestPublisher<String> publisher = new TestPublisher<>("foo", "bar");
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.from(publisher).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testFrom() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.from(Single.<String>just("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testFromCanceledSubscription() {
        SingleTestSubscriber<Object> subscriber = new SingleTestSubscriber<Object>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
                subscription.request(Long.MAX_VALUE);
            }
        };
        Single.from(new TestPublisher<>("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFromDoubleOnError() {
        Publisher<String> publisher = new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onError(new IllegalStateException("foo!"));
                subscriber.onError(new IllegalStateException("bar!"));
            }
        };
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.from(publisher).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFromNegativeSubscription() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(-1);
            }
        };
        Single.from(new TestPublisher<>("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testFromDeferredOnSubscribe() {
        // setup a multi that does not invoke onSubscribe upstream
        AtomicReference<Subscriber<? super String>> upstreamRef = new AtomicReference<>();
        Publisher<String> publisher = new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                upstreamRef.set(subscriber);
            }
        };
        // subscribe downstream
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.from(publisher).subscribe(subscriber);

        // invoke onSubscribe upstream
        Subscriber<? super String> upstream = upstreamRef.get();
        upstream.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                upstream.onNext("foo");
                upstream.onComplete();
            }

            @Override
            public void cancel() {
            }
        });

        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testFromDoubleOnSubscribe() {
        final TestSubscription subscription1 = new TestSubscription();
        final TestSubscription subscription2 = new TestSubscription();
        Publisher<String> publisher = new Publisher<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(subscription1);
                subscriber.onSubscribe(subscription2);
            }
        };
        TestSubscriber<String> subscriber = new TestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(15L);
            }
        };
        Single.from(publisher).subscribe(subscriber);
        assertThat(subscription1.requested, is(equalTo(Long.MAX_VALUE)));
        assertThat(subscription2.requested, is(equalTo(0L)));
    }

    @Test
    public void testMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.just("f.o.o")
                .mapMany((str) -> Multi.just(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("f", "o", "o"));
    }

    @Test
    public void testEmptyMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>empty()
                .mapMany((str) -> Multi.just(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testErrorMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>error(new IllegalStateException("foo!"))
                .mapMany((str) -> Multi.just(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapManyDoubleOnNext() {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onNext("foo");
                subscriber.onNext("bar");
            }
        };
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        single.mapMany((str) -> Multi.just(str.split("\\."))).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testMapManyDoubleOnError() {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onError(new IllegalStateException("foo!"));
                subscriber.onError(new IllegalStateException("bar!"));
            }
        };
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        single.mapMany((str) -> Multi.just(str.split("\\."))).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapManyDoubleSubscribe() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Multi<String> single = Single.just("foo").mapMany((str) -> Multi.just(str.split("\\.")));
        single.subscribe(subscriber);
        single.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testNeverMapMany() {
        SingleTestSubscriber<String> subscriber = new SingleTestSubscriber<>();
        Single.<String>never()
                .mapMany((str) -> Multi.just(str.split("\\.")))
                .subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testSubscriberFunctionalNullConsumer() {
        try {
            Single.just("foo").subscribe((Consumer<String>) null);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    @Test
    public void testSubscriberFunctionalConsumer() {
        TestConsumer<String> consumer = new TestConsumer<>();
        Single.just("foo").subscribe(consumer);
        assertThat(consumer.item, is(equalTo("foo")));
    }

    @Test
    public void testSubscriberFunctionalErrorConsumer() {
        TestConsumer<Throwable> errorConsumer = new TestConsumer<>();
        Single.<String>error(new IllegalStateException("foo!")).subscribe(null, errorConsumer);
        assertThat(errorConsumer.item, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSubscriberBadFunctionalConsumer() {
        TestConsumer<Throwable> errorConsumer = new TestConsumer<>();
        TestConsumer<String> consumer = new TestConsumer<String>() {
            @Override
            public void accept(String t) {
                throw new IllegalStateException("foo!");
            }
        };
        Single.just("foo").subscribe(consumer, errorConsumer);
        assertThat(consumer.item, is(nullValue()));
        assertThat(errorConsumer.item, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSubscriberFunctionalNullErrorConsumer() {
        try {
            Single.<String>error(new IllegalStateException("foo!")).subscribe(null, null);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    @Test
    public void testSubscriberFunctionalCompleteConsumer() {
        TestRunnable completeConsumer = new TestRunnable();
        Single.<String>empty().subscribe(null, null, completeConsumer);
        assertThat(completeConsumer.invoked, is(equalTo(true)));
    }

    @Test
    public void testSubscriberFunctionalNullCompleteConsumer() {
        try {
            Single.<String>empty().subscribe(null, null, null);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    @Test
    public void testSubscriberFunctionalBadCompleteConsumer() {
        TestConsumer<Throwable> errorConsumer = new TestConsumer<>();
        TestRunnable completeConsumer = new TestRunnable() {
            @Override
            public void run() {
                throw new IllegalStateException("foo!");
            }
        };
        Single.<String>empty().subscribe(null, errorConsumer, completeConsumer);
        assertThat(completeConsumer.invoked, is(equalTo(false)));
        assertThat(errorConsumer.item, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSubscriberFunctionalSubscriptionConsumer() {
        TestConsumer<Subscription> subscriptionConsumer = new TestConsumer<Subscription>() {
            @Override
            public void accept(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
        };
        TestConsumer<String> consumer = new TestConsumer<>();
        Single.just("foo").subscribe(consumer, null, null, subscriptionConsumer);
        assertThat(consumer.item, is(equalTo("foo")));
    }

    @Test
    public void testSubscriberFunctionalNullSubscriptionConsumer() {
        try {
            Single.<String>empty().subscribe(null, null, null, null);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    @Test
    public void testSubscriberFunctionalBadSubscriptionConsumer() {
        TestConsumer<Throwable> errorConsumer = new TestConsumer<>();
        TestConsumer<Subscription> subscriptionConsumer = new TestConsumer<Subscription>() {
            @Override
            public void accept(Subscription subscription) {
                throw new IllegalStateException("foo!");
            }
        };
        Single.<String>just("foo").subscribe(null, errorConsumer, null, subscriptionConsumer);
        assertThat(errorConsumer.item, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSubscriberFunctionalSubscriptionConsumerDoubleOnSubscribe() {
        final TestSubscription subscription1 = new TestSubscription();
        final TestSubscription subscription2 = new TestSubscription();
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(subscription1);
                subscriber.onSubscribe(subscription2);
            }
        };
        TestConsumer<Subscription> subscriptionConsumer = new TestConsumer<Subscription>() {
            @Override
            public void accept(Subscription subscription) {
                subscription.request(15);
            }
        };
        single.subscribe(null, null, null, subscriptionConsumer);
        assertThat(subscription1.requested, is(equalTo(15L)));
        assertThat(subscription2.requested, is(equalTo(0L)));
    }

    @Test
    public void testBadSingleToFuture() {
        Single<String> single = new Single<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                throw new IllegalStateException("foo!");
            }
        };
        CompletableFuture<String> future = single.toFuture();
        assertThat(future.isCompletedExceptionally(), is(equalTo(true)));
        future.exceptionally((ex) -> {
            assertThat(ex, is(instanceOf(IllegalStateException.class)));
            return null;
        });
    }

    @Test
    public void testEmptyToFuture() {
        CompletableFuture<Object> future = Single.<Object>empty().toFuture();
        assertThat(future.isCompletedExceptionally(), is(equalTo(true)));
        future.exceptionally((ex) -> {
           assertThat(ex, is(instanceOf(IllegalStateException.class)));
           return null;
        });
    }

    @Test
    public void testToFutureDoubleOnError() throws InterruptedException, ExecutionException {
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
        CompletableFuture<String> future = single.toFuture();
        assertThat(future.isCompletedExceptionally(), is(equalTo(true)));
        future.exceptionally((ex) -> {
            assertThat(ex, is(instanceOf(IllegalStateException.class)));
           return null;
        });
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
        CompletableFuture<String> future = single.toFuture();
        assertThat(future.isDone(), is(equalTo(true)));
        assertThat(future.get(), is(equalTo("foo")));
    }

    @Test
    public void testToFutureCancel() throws InterruptedException, ExecutionException {
        CompletableFuture<String> future = Single.just("foo").toFuture();
        assertThat(future.cancel(true), is(equalTo(false)));
        assertThat(future.get(), is(equalTo("foo")));
    }

    @Test
    public void testNeverToFutureCancel() throws InterruptedException, ExecutionException {
        CompletableFuture<String> future = Single.<String>never().toFuture();
        assertThat(future.cancel(true), is(equalTo(true)));
        assertThat(future.isCancelled(), is(equalTo(true)));
    }

    @Test
    public void testNeverToFutureDoubleCancel() throws InterruptedException, ExecutionException {
        CompletableFuture<String> future = Single.<String>never().toFuture();
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
        CompletableFuture<String> future = single.toFuture();
        assertThat(future.isDone(), is(equalTo(false)));
        assertThat(future.isCompletedExceptionally(), is(equalTo(false)));
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
