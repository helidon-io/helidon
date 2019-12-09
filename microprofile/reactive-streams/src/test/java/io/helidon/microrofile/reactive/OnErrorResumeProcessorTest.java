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

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

public class OnErrorResumeProcessorTest {

    @Test
    void onErrorResume() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(Collections.singletonList(4),
                ReactiveStreams
                        .generate(() -> 1)
                        .limit(3L)
                        .peek(i -> {
                            throw new TestRuntimeException();
                        })
                        .onErrorResume(throwable -> 4)
                        .toList()
                        .run()
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS));
    }

    @Test
    void onErrorResumeWith() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(Arrays.asList(1, 2, 3),
                ReactiveStreams
                        .generate(() -> 1)
                        .limit(3L)
                        .peek(i -> {
                            throw new TestRuntimeException();
                        })
                        .onErrorResumeWith(throwable -> ReactiveStreams.of(1, 2, 3))
                        .toList()
                        .run()
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS));
    }


    @Test
    void onErrorResume2() throws InterruptedException, ExecutionException, TimeoutException {
        ReactiveStreams.failed(new TestThrowable())
                .onErrorResumeWith(
                        t -> ReactiveStreams.of(1, 2, 3)
                )
                .forEach(System.out::println).run().toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    @Test
    void onErrorResume3() throws InterruptedException, ExecutionException, TimeoutException {
        ReactiveStreams.failed(new TestThrowable())
                .onErrorResumeWith(
                        t -> ReactiveStreams.of(1, 2, 3)
                )
                .toList().run().toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    @Test
    void requestCount() {
        Publisher<Long> pub = ReactiveStreams.<Long>failed(new TestThrowable())
                .onErrorResumeWith(
                        t -> ReactiveStreams.fromIterable(() -> LongStream.rangeClosed(1, 3).boxed().iterator())
                )
                .buildRs();
        CountingSubscriber sub = new CountingSubscriber();
        ReactiveStreams.fromPublisher(pub).buildRs().subscribe(HybridSubscriber.from(sub));

        sub.request(1);
        sub.expectRequestCount(1);
        sub.expectSum(1);
        sub.request(2);
        sub.expectSum(6);
        sub.expectRequestCount(3);
        sub.expectOnComplete();
    }

    @Test
    void requestCount2() {
        AtomicLong seq = new AtomicLong(0);
        Publisher<Long> pub = ReactiveStreams.<Long>failed(new TestThrowable())
                .onErrorResumeWith(
                        t -> ReactiveStreams.generate(seq::incrementAndGet)
                )
                .buildRs();
        CountingSubscriber sub = new CountingSubscriber();
        ReactiveStreams.fromPublisher(pub).buildRs().subscribe(HybridSubscriber.from(sub));

        sub.cancelAfter(100_000L);
        sub.request(Long.MAX_VALUE - 1);
        sub.request(Long.MAX_VALUE - 1);
    }
}
