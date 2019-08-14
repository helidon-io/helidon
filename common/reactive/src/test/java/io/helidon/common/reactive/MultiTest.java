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

import java.util.List;
import java.util.function.Consumer;

import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

import org.junit.jupiter.api.Test;

import static io.helidon.common.CollectionsHelper.listOf;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link MultiTest} test.
 */
public class MultiTest {

    @Test
    public void testJust() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testJustCollection() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just(listOf("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testEmpty() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>empty().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testError() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>error(new Exception("foo")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNever() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<>();
        Multi.<Object>never().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testMapper() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").map(String::toUpperCase).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("FOO", "BAR"));
    }

    @Test
    public void testErrorCollect() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>error(new Exception("foo")).collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(notNullValue()));
        assertThat(subscriber.getLastError().getMessage(), is(equalTo("foo")));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectList() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems().get(0), hasItems("foo", "bar"));
    }

    @Test
    public void testFromPublisher() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.from(new TestPublisher<>("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testFromMulti() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.from(Multi.<String>just("foo", "bar")).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo", "bar"));
    }

    @Test
    public void testFirst() {
        MultiTestSubscriber<String> subscriber = new MultiTestSubscriber<>();
        Multi.just("foo", "bar").first().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems().get(0), is(equalTo("foo")));
    }

    @Test
    public void testCollectDeferredOnSubscribe() {
        // setup a multi that does not invoke onSubscribe upstream
        AtomicReference<Subscriber<? super String>> upstreamRef = new AtomicReference<>();
        Multi<String> multi = new Multi<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                upstreamRef.set(subscriber);
            }
        };
        // subscribe downstream
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        multi.collectList().subscribe(subscriber);

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
        assertThat(subscriber.getItems(), is(not(empty())));
        assertThat(subscriber.getItems().get(0), hasItems("foo"));
    }

    @Test
    public void testCollectCanceledSubscription() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<List<String>>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
                subscription.request(1);
            }
        };
        Multi.from(new TestPublisher<>("foo")).collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectNegativeSubscription() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<List<String>>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(-1);
            }
        };
        Multi.from(new TestPublisher<>("foo")).collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectDoubleSubscriptionRequest() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<List<String>>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
                subscription.request(1);
            }
        };
        Multi.from(new TestPublisher<>("foo")).collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(not(empty())));
        assertThat(subscriber.getItems().get(0), hasItems("foo"));
    }

    @Test
    public void testCollectorBadCollectMethod() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
                throw new IllegalStateException("foo!");
            }

            @Override
            public List<String> value() {
                return Collections.emptyList();
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadValueMethod() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
            }

            @Override
            public List<String> value() {
                throw new IllegalStateException("foo!");
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorBadNullValue() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi.<String>just("foo", "bar").collect(new Collector<String, List<String>>() {
            @Override
            public void collect(String item) {
            }

            @Override
            public List<String> value() {
                return null;
            }
        }).subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testNeverCollect() {
        MultiTestSubscriber<Object> subscriber = new MultiTestSubscriber<Object>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
                subscription.request(1);
            }
        };
        Multi.<Object>never().collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectorDoubleOnError() {
        MultiTestSubscriber<List<String>> subscriber = new MultiTestSubscriber<>();
        Multi<String> multi = new Multi<String>() {
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
        multi.collectList().subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCollectDoubleOnSubscribe() {
        final TestSubscription subscription1 = new TestSubscription();
        final TestSubscription subscription2 = new TestSubscription();
        Multi<String> multi = new Multi<String>() {
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(subscription1);
                subscriber.onSubscribe(subscription2);
            }
        };
        TestSubscriber<List<String>> subscriber = new TestSubscriber<List<String>>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }
        };
        multi.collectList().subscribe(subscriber);
        assertThat(subscription1.requested, is(equalTo(Long.MAX_VALUE)));
        assertThat(subscription2.requested, is(equalTo(0L)));
    }

    @Test
    public void testSubscriberFunctionalNullConsumer() {
        try {
            Multi.just("foo").subscribe((Consumer<String>) null);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    @Test
    public void testSubscriberFunctionalConsumer() {
        TestConsumer<String> consumer = new TestConsumer<>();
        Multi.just("foo").subscribe(consumer);
        assertThat(consumer.item, is(equalTo("foo")));
    }

    @Test
    public void testSubscriberFunctionalErrorConsumer() {
        TestConsumer<Throwable> errorConsumer = new TestConsumer<>();
        Multi.<String>error(new IllegalStateException("foo!")).subscribe(null, errorConsumer);
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
        Multi.just("foo").subscribe(consumer, errorConsumer);
        assertThat(consumer.item, is(nullValue()));
        assertThat(errorConsumer.item, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSubscriberFunctionalNullErrorConsumer() {
        try {
            Multi.<String>error(new IllegalStateException("foo!")).subscribe(null, null);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    @Test
    public void testSubscriberFunctionalCompleteConsumer() {
        TestRunnable completeConsumer = new TestRunnable();
        Multi.<String>empty().subscribe(null, null, completeConsumer);
        assertThat(completeConsumer.invoked, is(equalTo(true)));
    }

    @Test
    public void testSubscriberFunctionalNullCompleteConsumer() {
        try {
            Multi.<String>empty().subscribe(null, null, null);
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
        Multi.<String>empty().subscribe(null, errorConsumer, completeConsumer);
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
        Multi.just("foo").subscribe(consumer, null, null, subscriptionConsumer);
        assertThat(consumer.item, is(equalTo("foo")));
    }

    @Test
    public void testSubscriberFunctionalNullSubscriptionConsumer() {
        try {
            Multi.<String>empty().subscribe(null, null, null, null);
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
        Multi.<String>just("foo").subscribe(null, errorConsumer, null, subscriptionConsumer);
        assertThat(errorConsumer.item, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSubscriberFunctionalSubscriptionConsumerDoubleOnSubscribe() {
        final TestSubscription subscription1 = new TestSubscription();
        final TestSubscription subscription2 = new TestSubscription();
        Multi<String> multi = new Multi<String>() {
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
        multi.subscribe(null, null, null, subscriptionConsumer);
        assertThat(subscription1.requested, is(equalTo(15L)));
        assertThat(subscription2.requested, is(equalTo(0L)));
    }

    private static class MultiTestSubscriber<T> extends TestSubscriber<T> {

        @Override
        public void onSubscribe(Subscription subscription) {
            super.onSubscribe(subscription);
            requestMax();
        }
    }
}
