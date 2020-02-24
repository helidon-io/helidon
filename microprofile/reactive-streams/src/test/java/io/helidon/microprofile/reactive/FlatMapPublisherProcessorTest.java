/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class FlatMapPublisherProcessorTest extends AbstractProcessorTest {
    @Override
    protected Processor<Long, Long> getProcessor() {
        return ReactiveStreams.<Long>builder().flatMap(ReactiveStreams::of).buildRs();
    }

    @Override
    protected Processor<Long, Long> getFailedProcessor(RuntimeException t) {
        return ReactiveStreams.<Long>builder().<Long>flatMap(i -> {
            throw t;
        }).buildRs();
    }

    @Test
    void onePublisherAtTime() throws InterruptedException, ExecutionException, TimeoutException {
        AtomicInteger counter = new AtomicInteger();
        List<MockPublisher> pubs = Arrays.asList(new MockPublisher(), new MockPublisher());
        List<PublisherBuilder<Long>> builders = pubs.stream()
                .peek(mockPublisher -> mockPublisher.observeSubscribe(s -> {
                    assertEquals(1, counter.incrementAndGet(),
                            "Another publisher already subscribed to!!");
                }))
                .map(ReactiveStreams::fromPublisher)
                .collect(Collectors.toList());

        CompletionStage<List<Long>> result =
                ReactiveStreams.of(0, 1)
                        .flatMap(builders::get)
                        .toList()
                        .run();

        counter.decrementAndGet();
        pubs.get(0).sendOnComplete();
        counter.decrementAndGet();
        pubs.get(1).sendOnComplete();

        result.toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void innerProcessorSecondSubscriptionTest() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        Subscription secondSubscription = new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.complete(null);
            }
        };

        MockPublisher publisher = new MockPublisher();
        final AtomicReference<Subscriber<Long>> subs = new AtomicReference<>();
        publisher.observeSubscribe(subscriber -> subs.set((Subscriber<Long>) subscriber));

        CompletionStage<List<Long>> result =
                ReactiveStreams.of(0)
                        .flatMap(integer -> ReactiveStreams.fromPublisher(publisher))
                        .toList()
                        .run();

        subs.get().onSubscribe(secondSubscription);
        publisher.sendOnComplete();

        cancelled.get(1, TimeUnit.SECONDS);
    }
}
