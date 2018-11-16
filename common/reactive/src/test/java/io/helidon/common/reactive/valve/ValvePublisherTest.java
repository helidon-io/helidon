/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.ReactiveStreamsAdapter;

import org.hamcrest.core.IsCollectionContaining;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The ValvePublisherTest.
 */
class ValvePublisherTest {

    @Test
    void simpleTest() {
        List<Integer> list = ReactiveStreamsAdapter.publisherFromFlow(Valves.from(1, 2, 3, 4)
                                                                              .toPublisher())
                .collect(Collectors.toList())
                .block();

        assertThat(list, IsCollectionContaining.hasItems(1, 2, 3, 4));
    }

    @Test
    void continuous() {
        StringBuffer sb = new StringBuffer();
        Tank<Integer> integerTank = new Tank<>(10);

        ReactiveStreamsAdapter.publisherFromFlow(integerTank.toPublisher())
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
        final StringBuffer sb = new StringBuffer();
        Tank<Integer> integerTank = new Tank<>(10);

        final AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();

        integerTank.toPublisher()
                .subscribe(new Flow.Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
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

        final AtomicReference<Flow.Subscription> subscriptionRef = new AtomicReference<>();

        integerTank.toPublisher()
                .subscribe(new Flow.Subscriber<Integer>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
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

        assertThat(exception.get().getMessage(), StringContains.containsString("Valve to Publisher in an error"));
    }

    @Test
    void multipleSubscribers() {
        Tank<String> stringTank = new Tank<>(10);

        Flux<String> flux = ReactiveStreamsAdapter.publisherFromFlow(stringTank.toPublisher());
        stringTank.addAll(CollectionsHelper.listOf("1", "2", "3"));
        stringTank.close();

        assertThat(flux.collect(Collectors.joining()).block(), is("123"));

        try {
            flux.collect(Collectors.joining()).block();
            fail("Should have thrown an exception!");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), StringContains.containsString("Multiple subscribers aren't allowed"));
        }
    }

    @Test
    void multiplePublishers() {
        Tank<String> stringTank = new Tank<>(10);

        stringTank.addAll(CollectionsHelper.listOf("1", "2", "3"));
        stringTank.close();

        assertThat(ReactiveStreamsAdapter.publisherFromFlow(stringTank.toPublisher())
                .collect(Collectors.joining()).block(), is("123"));
        try {
            ReactiveStreamsAdapter.publisherFromFlow(stringTank.toPublisher()).collect(Collectors.joining()).block();
            fail("Should have thrown an exception!");
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), StringContains.containsString("Handler is already registered"));
        }
    }
}
