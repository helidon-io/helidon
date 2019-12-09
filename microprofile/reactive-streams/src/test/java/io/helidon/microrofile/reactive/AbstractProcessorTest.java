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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

public abstract class AbstractProcessorTest {

    protected abstract Processor<Integer, Integer> getProcessor();

    /**
     * https://github.com/reactive-streams/reactive-streams-jvm#1.1
     */
    @Test
    void requestCount() {
        MockPublisher p = new MockPublisher();
        testProcessor(ReactiveStreams.fromPublisher(p).via(getProcessor()).buildRs(), s -> {
            s.expectRequestCount(0);
            s.request(1);
            p.sendNext(4);
            s.expectRequestCount(1);
            s.request(1);
            s.request(2);
            p.sendNext(5);
            p.sendNext(6);
            p.sendNext(7);
            s.expectRequestCount(4);
            s.cancel();
        });
    }

    /**
     * https://github.com/reactive-streams/reactive-streams-jvm#2.8
     */
    @Test
    void nextAfterCancel() {
        MockPublisher p = new MockPublisher();
        testProcessor(ReactiveStreams.fromPublisher(p).via(getProcessor()).buildRs(), s -> {
            s.request(2);
            s.cancel();
            p.sendNext(2);
            p.sendNext(4);
            s.expectSum(6);
        });
    }

    /**
     * https://github.com/reactive-streams/reactive-streams-jvm#2.5
     */
    @Test
    void cancel2ndSubscription() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        MockPublisher p = new MockPublisher();
        Processor<Integer, Integer> processor = getProcessor();
        testProcessor(ReactiveStreams.fromPublisher(p).via(processor).buildRs(), s -> {
            s.request(2);
        });

        processor.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {

            }

            @Override
            public void cancel() {
                cancelled.complete(null);
            }
        });

        cancelled.get(1, TimeUnit.SECONDS);
    }


    @Test
    void onCompletePropagation() {
        testProcessor(ReactiveStreams.of(1, 2, 3).via(getProcessor()).buildRs(), s -> {
            s.request(1);
            s.expectRequestCount(1);
            s.request(2);
            s.expectRequestCount(3);
            s.expectOnComplete();
        });
    }

    @Test
    void requestCountProcessorTest() {
        testProcessor(ReactiveStreams.generate(() -> 4).via(getProcessor()).buildRs(), s -> {
            s.request(15);
            s.expectRequestCount(15);
            s.request(2);
            s.expectRequestCount(17);
            s.expectSum(17 * 4);
        });
    }

    @Test
    void finiteOnCompleteTest() throws InterruptedException, ExecutionException, TimeoutException {
        finiteOnCompleteTest(getProcessor());
    }

    private <T, U> void finiteOnCompleteTest(Processor<Integer, Integer> processor)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        ReactiveStreams.of(1, 2, 3)
                .via(processor)
                .onComplete(() -> completed.complete(null))
                .toList().run().toCompletableFuture().get(1, TimeUnit.SECONDS);

        completed.get(1, TimeUnit.SECONDS);
    }

    protected void testProcessor(Publisher<Integer> publisher,
                               Consumer<CountingSubscriber> testBody) {
        CountingSubscriber subscriber = new CountingSubscriber();
        publisher.subscribe(subscriber);
        testBody.accept(subscriber);
    }
}
