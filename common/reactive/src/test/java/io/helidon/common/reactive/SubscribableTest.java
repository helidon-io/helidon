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

import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link Subscribable} test.
 */
public class SubscribableTest {

    @Test
    public void testSubscriberFunctionalConsumer() {
        TestConsumer<String> consumer = new TestConsumer<>();
        Multi.singleton("foo").subscribe(consumer);
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
        Multi.singleton("foo").subscribe(consumer, errorConsumer);
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
        TestConsumer<Flow.Subscription> subscriptionConsumer = new TestConsumer<Flow.Subscription>() {
            @Override
            public void accept(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
        };
        TestConsumer<String> consumer = new TestConsumer<>();
        Multi.singleton("foo").subscribe(consumer, null, null, subscriptionConsumer);
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
        TestConsumer<Flow.Subscription> subscriptionConsumer = new TestConsumer<Flow.Subscription>() {
            @Override
            public void accept(Flow.Subscription subscription) {
                throw new IllegalStateException("foo!");
            }
        };
        Multi.<String>singleton("foo").subscribe(null, errorConsumer, null, subscriptionConsumer);
        assertThat(errorConsumer.item, is(instanceOf(IllegalStateException.class)));
    }

    @Test
    public void testSubscriberFunctionalSubscriptionConsumerDoubleOnSubscribe() {
        final TestSubscription subscription1 = new TestSubscription();
        final TestSubscription subscription2 = new TestSubscription();
        Multi<String> multi = new Multi<String>() {
            @Override
            public void subscribe(Flow.Subscriber<? super String> subscriber) {
                subscriber.onSubscribe(subscription1);
                subscriber.onSubscribe(subscription2);
            }
        };
        TestConsumer<Flow.Subscription> subscriptionConsumer = new TestConsumer<Flow.Subscription>() {
            @Override
            public void accept(Flow.Subscription subscription) {
                subscription.request(15);
            }
        };
        multi.subscribe(null, null, null, subscriptionConsumer);
        assertThat(subscription1.requested, is(equalTo(15L)));
        assertThat(subscription2.requested, is(equalTo(0L)));
    }
}
