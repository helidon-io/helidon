/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microrofile.reactive;

import org.eclipse.microprofile.reactive.streams.operators.CompletionSubscriber;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EngineTest {
    @Test
    void fixedItemsWithMap() {
        AtomicInteger sum = new AtomicInteger();
        ReactiveStreams
                .of("10", "20", "30")
                .map(a -> a.replaceAll("0", ""))
                .map(Integer::parseInt)
                .buildRs()
                .subscribe(new ConsumableSubscriber<>(sum::addAndGet));
        assertEquals(1 + 2 + 3, sum.get());
    }

    @Test
    void fixedItemsWithFilter() {
        AtomicInteger sum = new AtomicInteger();
        ReactiveStreams.of(1, 2, 3, 4, 5)
                .filter(x -> (x % 2) == 0)
                .buildRs()
                .subscribe(new ConsumableSubscriber<>(sum::addAndGet));
        assertTrue((sum.get() % 2) == 0);
    }

    @Test
    void publisherWithMapAndPeekAndFilter() {
        AtomicInteger peekSum = new AtomicInteger();
        AtomicInteger sum = new AtomicInteger();
        IntSequencePublisher intSequencePublisher = new IntSequencePublisher();

        ReactiveStreams.fromPublisher(intSequencePublisher)
                .limit(10)
                .filter(x -> (x % 2) == 0)
                .peek(peekSum::addAndGet)
                .map(String::valueOf)
                .map(s -> s + "0")
                .map(Integer::parseInt)
                .buildRs()
                .subscribe(new ConsumableSubscriber<>(sum::addAndGet));

        assertEquals(2 + 4 + 6 + 8 + 10, peekSum.get());
        assertEquals(20 + 40 + 60 + 80 + 100, sum.get());
    }

    @Test
    void fromTo() throws ExecutionException, InterruptedException, TimeoutException {
        AtomicInteger sum = new AtomicInteger();
        IntSequencePublisher publisher = new IntSequencePublisher();
        StringBuilder beforeFilter = new StringBuilder();
        StringBuilder afterFilter = new StringBuilder();
        ReactiveStreams
                .fromPublisher(publisher)
                .map(String::valueOf)
                .map(i -> i + "-")
                .peek(beforeFilter::append)
                .map(s -> s.replaceAll("-", ""))
                .map(Integer::parseInt)
                .filter(i -> i <= 5)
                .peek(afterFilter::append)
                .to(ReactiveStreams.fromSubscriber(new ConsumableSubscriber<>(sum::addAndGet, 10)))
                .run();
        assertEquals("1-2-3-4-5-6-7-8-9-10-", beforeFilter.toString());
        assertEquals("12345", afterFilter.toString());
        assertEquals(1 + 2 + 3 + 4 + 5, sum.get());
    }

    @Test
    void limit() {
        AtomicInteger sum = new AtomicInteger();
        IntSequencePublisher publisher = new IntSequencePublisher();
        ConsumableSubscriber<Integer> subscriber = new ConsumableSubscriber<>(sum::addAndGet);
        ReactiveStreams
                .fromPublisher(publisher)
                //TODO: peak clashes with limit, probably because of onComplete is not called in limit processor
//                .peek(System.out::println)
                .limit(5)
//                .peek(System.out::println)
                .buildRs()
                .subscribe(subscriber);
        assertEquals(1 + 2 + 3 + 4 + 5, sum.get());
    }

    @Test
    void subscriberCreation() throws ExecutionException, InterruptedException {
        AtomicInteger sum = new AtomicInteger();
        IntSequencePublisher publisher = new IntSequencePublisher();
        CompletionSubscriber<Integer, Void> subscriber = ReactiveStreams.<Integer>builder()
                .limit(5)
                .peek(System.out::println)
                .forEach(sum::addAndGet)
                .build();
        publisher.subscribe(subscriber);
        assertEquals(1 + 2 + 3 + 4 + 5, sum.get());
    }
}