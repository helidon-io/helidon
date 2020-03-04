/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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


import java.util.concurrent.Flow.Subscription;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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
        processor.onSubscribe(EmptySubscription.INSTANCE);
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
        processor.onSubscribe(EmptySubscription.INSTANCE);
        assertThat(subscriber.isComplete(), is(equalTo(false)));
        assertThat(subscriber.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testOnNextAfterOnComplete() {
        TestProcessor<String> processor = new TestProcessor<>();
        TestSubscriber<String> subscriber = new TestSubscriber<>();
        processor.onSubscribe(EmptySubscription.INSTANCE);
        processor.subscribe(subscriber);
        subscriber.request1();
        processor.onComplete();
        assertThrows(IllegalStateException.class, () -> processor.onNext("foo"));
        assertThat(subscriber.isComplete(), is(equalTo(true)));
        assertThat(subscriber.getItems(), is(empty()));
    }

    @Test
    public void testDoOnNextError() {
        TestProcessor<String> processor = new TestProcessor<String>() {
            @Override
            public void onNext(String item) throws IllegalStateException {
                super.onError(new IllegalStateException("foo!"));
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
        processor.onSubscribe(EmptySubscription.INSTANCE);
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
        TestSubscriber<String> subscriber = new TestSubscriber<String>(1L);
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

        subscriber.assertResult("foo");
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
        processor.onSubscribe(EmptySubscription.INSTANCE);
        processor.subscribe(subscriber1);
        TestSubscriber<String> subscriber2 = new TestSubscriber<>();
        processor.subscribe(subscriber2);
        assertThat(subscriber1.getSubcription(), is(not(nullValue())));
        assertThat(subscriber1.getLastError(), is(nullValue()));
        assertThat(subscriber2.getSubcription(), is(EmptySubscription.INSTANCE));
        assertThat(subscriber2.getLastError(), is(instanceOf(IllegalStateException.class)));
    }

    private static class TestProcessor<T> extends BaseProcessor<T, T> {

        boolean complete;
        Throwable error;

        @Override
        protected void submit(T item) {
            getSubscriber().onNext(item);
        }

        @Override
        public void onComplete() {
            complete = true;
            super.onComplete();
        }

        @Override
        public void onError(Throwable ex) {
            error = ex;
            super.onError(ex);
        }
    }
}
