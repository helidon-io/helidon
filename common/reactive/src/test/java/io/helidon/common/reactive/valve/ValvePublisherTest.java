/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Collector;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;
import io.helidon.common.reactive.Multi;

import org.junit.jupiter.api.Test;

import static io.helidon.common.CollectionsHelper.listOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The ValvePublisherTest.
 */
class ValvePublisherTest {

    @Test
    void simpleTest() throws Exception {
        List<Integer> list = Multi.from(Valves.from(1, 2, 3, 4).toPublisher())
                .collectList()
                .get(10, TimeUnit.SECONDS);

        assertThat(list, hasItems(1, 2, 3, 4));
    }

    @Test
    void continuous() {
        StringBuilder sb = new StringBuilder();
        Tank<Integer> integerTank = new Tank<>(10);

        Multi.from(integerTank.toPublisher())
                .subscribe(sb::append);

        integerTank.add(1);
        integerTank.add(2);
        assertThat(sb.toString(), is("12"));

        integerTank.add(3);
        integerTank.add(4);
        assertThat(sb.toString(), is("1234"));
    }

    @Test
    void publisher() {
        final StringBuilder sb = new StringBuilder();
        Tank<Integer> integerTank = new Tank<>(10);

        final AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();

        integerTank.toPublisher().subscribe(new Subscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(Integer item) {
                sb.append(item);
            }

            @Override
            public void onError(Throwable throwable) {
                fail("Not expected: " + throwable);
            }

            @Override
            public void onComplete() {
                sb.append("$");
            }
        });

        integerTank.add(1);
        integerTank.add(2);

        assertThat(sb.toString(), is(""));

        subscriptionRef.get().request(1);
        assertThat(sb.toString(), is("1"));

        subscriptionRef.get().request(2);
        integerTank.add(3);
        integerTank.add(4);
        assertThat(sb.toString(), is("123"));

        integerTank.add(5);
        assertThat(sb.toString(), is("123"));
        subscriptionRef.get().request(2);
        assertThat(sb.toString(), is("12345"));

        // request additional 2 more ahead
        subscriptionRef.get().request(2);
        assertThat(sb.toString(), is("12345"));
        integerTank.add(6);
        assertThat(sb.toString(), is("123456"));
        integerTank.add(7);
        assertThat(sb.toString(), is("1234567"));

        // TODO webserver#22 close itself doesn't complete the subscriber; change the test once the issue is solved
        integerTank.close();
        assertThat(sb.toString(), is("1234567"));
        subscriptionRef.get().request(1);
        assertThat(sb.toString(), is("1234567$"));
    }

    @Test
    void onNextThrowsException() {
        final AtomicReference<Throwable> exception = new AtomicReference<>();
        Tank<Integer> integerTank = new Tank<>(10);

        final AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();

        integerTank.toPublisher().subscribe(new Subscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscriptionRef.set(subscription);
            }

            @Override
            public void onNext(Integer item) {
                throw new RuntimeException("Exception in onNext()");
            }

            @Override
            public void onError(Throwable throwable) {
                exception.set(throwable);
            }

            @Override
            public void onComplete() {
                fail("onComplete not expected");
            }
        });

        integerTank.add(1);
        subscriptionRef.get().request(1);

        assertThat(exception.get().getMessage(), containsString("Valve to Publisher in an error"));
    }

    @Test
    void multipleSubscribers() throws Exception {
        Tank<String> stringTank = new Tank<>(10);

        stringTank.addAll(listOf("1", "2", "3"));
        stringTank.close();

        Multi<String> multi = Multi.from(stringTank.toPublisher());
        assertThat(multi.collect(new StringCollector<>()).get(10, TimeUnit.SECONDS), is("123"));

        try {
            multi.collect(new StringCollector<>()).get(10, TimeUnit.SECONDS);
            fail("Should have thrown an exception!");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(notNullValue()));
            assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
            assertThat(e.getCause().getMessage(), containsString("Multiple subscribers aren't allowed"));
        }
    }

    @Test
    void multiplePublishers() throws Exception {
        Tank<String> stringTank = new Tank<>(10);

        stringTank.addAll(listOf("1", "2", "3"));
        stringTank.close();

        assertThat(Multi.from(stringTank.toPublisher()).collect(new StringCollector<>()).get(10, TimeUnit.SECONDS), is("123"));

        try {
            Multi.from(stringTank.toPublisher()).collect(new StringCollector<>()).get(10, TimeUnit.SECONDS);
            fail("Should have thrown an exception!");
        } catch (ExecutionException e) {
            assertThat(e.getCause(), is(notNullValue()));
            assertThat(e.getCause(), is(instanceOf(IllegalStateException.class)));
            assertThat(e.getCause().getMessage(), containsString("Handler is already registered"));
        }
    }

    private static final class StringCollector<T extends Object> implements Collector<T, String> {

        private final StringBuilder sb;

        StringCollector() {
            this.sb = new StringBuilder();
        }

        @Override
        public String value() {
            return sb.toString();
        }

        @Override
        public void collect(T item) {
            sb.append(item.toString());
        }
    }
}
