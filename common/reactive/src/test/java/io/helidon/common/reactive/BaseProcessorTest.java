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


import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;

/**
 * {@link BaseProcessor} test.
 */
public class BaseProcessorTest {

    @Test
    public void testOnCompleteWithNoRequest() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        processor.onComplete();
        assertThat(subscriber.isComplete(), is(equalTo(true)));
    }

    @Test
    public void testOnCompleteBeforeSubscribe() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.onComplete();
        processor.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
    }

    @Test
    public void testOnErrorWithNoRequest() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        processor.onError(new IllegalStateException("foo!"));
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testOnErrorBeforeSubscribe() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.onError(new IllegalStateException("foo!"));
        processor.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testOnNextAfterOnComplete() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        subscriber.request1();
        processor.onComplete();
        processor.onNext("foo");
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testDoOnNextError() {
        TestProcessor<String> processor = new TestProcessor<String>() {
            @Override
            protected void hookOnNext(String item) throws IllegalStateException {
                throw new IllegalStateException("foo!");
            }
        };
        new TestPublisher<>("foo").subscribe(processor);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        subscriber.request1();
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testBeforeOnCompleteError() {
        TestProcessor<String> processor = new TestProcessor<String>() {
            @Override
            protected void hookOnComplete() throws IllegalStateException {
                throw new IllegalStateException("foo!");
            }
        };
        new TestPublisher<>("foo").subscribe(processor);
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        subscriber.request1();
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testSubmitError() {
        TestProcessor<String> processor = new TestProcessor<>();
        new TestPublisher<>("foo").subscribe(processor);
        TestSubscriber<String> subscriber = new TestSubscriber<String>() {
            @Override
            public void onNext(String item) {
                throw new IllegalStateException("foo!");
            }
        };
        processor.subscribe(subscriber);
        subscriber.request1();
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testCanceledSubscription() {
        TestSubscriber<Object> subscriber = new TestSubscriber<Object>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
                subscription.request(Long.MAX_VALUE);
            }
        };
        TestProcessor<String> processor = new TestProcessor<>();
        new TestPublisher<>("foo").subscribe(processor);
        processor.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testDoubleOnError() {
        TestProcessor<String> processor = new TestProcessor<>();
        processor.onError(new IllegalStateException("foo!"));
        processor.onError(new UnsupportedOperationException("bar!"));
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testNegativeSubscription() {
        TestSubscriber<String> subscriber = new TestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(-1);
            }
        };
        TestProcessor<String> processor = new TestProcessor<>();
        new TestPublisher<>("foo").subscribe(processor);
        processor.subscribe(subscriber);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalArgumentException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testDeferredOnSubscribe() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<String>(){
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(1);
            }
        };
        processor.subscribe(subscriber);
        processor.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                processor.onNext("foo");
                processor.onComplete();
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
    public void testDoubleOnSubscribe() {
        final TestSubscription subscription1 = new TestSubscription();
        final TestSubscription subscription2 = new TestSubscription();
        TestSubscriber<String> subscriber = new TestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(15L);
            }
        };
        TestProcessor<String> processor = new TestProcessor<>();
        processor.onSubscribe(subscription1);
        processor.onSubscribe(subscription2);
        processor.subscribe(subscriber);
        assertThat(subscription1.requested, is(equalTo(15L)));
        assertThat(subscription2.requested, is(equalTo(0L)));
    }

    @Test
    public void testDoubleSubscribe() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber1 = new TestSubscriber<>();
        processor.subscribe(subscriber1);
        TestSubscriber<String> subscriber2 = new TestSubscriber<>();
        processor.subscribe(subscriber2);
        assertThat(subscriber1.getSubcription(), is(not(nullValue())));
        assertThat(subscriber2.getSubcription(), is(nullValue()));
    }

    @Test
    public void testSubscriptionNotCanceled() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscription subscription = new TestSubscription();
        processor.onSubscribe(subscription);
        TestSubscriber<String> subscriber = new TestSubscriber<String>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.cancel();
            }
        };
        processor.subscribe(subscriber);
        assertThat(subscription.canceled, is(equalTo(false)));
    }

    @Test
    public void testNotEnoughRequestToSubmit() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        processor.submit("foo!");
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testDoSubscribe() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        subscriber.request1();
        processor.doSubscribe(new TestPublisher<>("foo"));
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
    }

    @Test
    public void testDoSubscribeErrorDelegate() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        subscriber.request1();
        processor.doSubscribe(new TestPublisher<String>(){
            @Override
            public void subscribe(Subscriber<? super String> subscriber) {
                subscriber.onError(new IllegalStateException("foo!"));
            }
        });
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testDoSubscribeTwice() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.subscribe(subscriber);
        subscriber.request1();
        processor.doSubscribe(new TestPublisher<>("foo"));
        TestPublisher<String> pub = new TestPublisher<>("bar");
        processor.doSubscribe(pub);
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getLastError(), is(nullValue()));
        assertThat(subscriber.getItems(), hasItems("foo"));
        assertThat(pub.subscribed, is(equalTo(false)));
    }

    private static class TestProcessor<T> extends BaseProcessor<T, T> {

        boolean complete;
        Throwable error;

        @Override
        protected void hookOnNext(T item) throws IllegalStateException {
            super.hookOnNext(item);
            submit(item);
        }

        @Override
        protected void hookOnComplete() throws IllegalStateException {
            complete = true;
            super.hookOnComplete();
        }

        @Override
        protected void hookOnError(Throwable ex) throws IllegalStateException {
            error = ex;
            super.hookOnError(ex);
        }
    }
}
