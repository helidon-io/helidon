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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.TappedProcessor;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

public abstract class AbstractProcessorTest {

    @SuppressWarnings("unchecked")
    protected Processor<Long, Long> getProcessor() {
        Flow.Processor<Long, Long> processor = (Flow.Processor) TappedProcessor.create();
        return HybridProcessor.from(processor);
    }

    protected abstract Processor<Long, Long> getFailedProcessor(RuntimeException t);

    protected Publisher<Long> getPublisher(long items) {
        if (items < 1) {
            return new MockPublisher();
        } else {
            return ReactiveStreams.fromIterable(LongStream.range(0, items).boxed().collect(Collectors.toList())).buildRs();
        }
    }

    private MockPublisher getMockPublisher() {
        Publisher<Long> pub = getPublisher(-5);
        assumeTrue(pub instanceof MockPublisher);
        return (MockPublisher) pub;
    }

    /**
     * https://github.com/reactive-streams/reactive-streams-jvm#1.1
     */
    @Test
    void requestCount() {
        MockPublisher p = getMockPublisher();
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
        MockPublisher p = getMockPublisher();
        testProcessor(ReactiveStreams.fromPublisher(p).via(getProcessor()).buildRs(), s -> {
            s.request(4);
            p.sendNext(2);
            s.cancel();
            p.sendNext(4);
            s.expectSum(2);
        });
    }

    /**
     * https://github.com/reactive-streams/reactive-streams-jvm#2.5
     */
    @Test
    void cancel2ndSubscription() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        Publisher<Long> p = getPublisher(4);
        Processor<Long, Long> processor = getProcessor();
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
        testProcessor(ReactiveStreams.fromPublisher(getPublisher(3)).via(getProcessor()).buildRs(), s -> {
            s.request(1);
            s.expectRequestCount(1);
            s.request(2);
            s.expectRequestCount(3);
            s.expectOnComplete();
        });
    }

    @Test
    void requestCountProcessorTest() {
        testProcessor(ReactiveStreams.fromPublisher(getPublisher(1_000_400L)).via(getProcessor()).buildRs(), s -> {
            s.request(15);
            s.expectRequestCount(15);
            s.request(3);
            s.expectRequestCount(18);
        });
    }

    @Test
    void longOverFlow() {
        testProcessor(ReactiveStreams.fromPublisher(getPublisher(1_000_400L)).via(getProcessor()).buildRs(), s -> {
            s.cancelAfter(1_000_0L);
            s.request(Long.MAX_VALUE - 1);
            s.request(Long.MAX_VALUE - 1);
        });
    }

    @Test
    void cancelOnError() throws InterruptedException, ExecutionException, TimeoutException {
        Processor<Long, Long> failedProcessor = getFailedProcessor(new TestRuntimeException());
        assumeTrue(Objects.nonNull(failedProcessor));
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Long>> result = ReactiveStreams.fromPublisher(getPublisher(1_000_000L))
                .onTerminate(() -> cancelled.complete(null))
                .via(failedProcessor)
                .toList()
                .run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
    }

    @Test
    void finiteOnCompleteTest() throws InterruptedException, ExecutionException, TimeoutException {
        finiteOnCompleteTest(getProcessor());
    }

    private <T, U> void finiteOnCompleteTest(Processor<Long, Long> processor)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        ReactiveStreams.fromPublisher(getPublisher(3))
                .via(processor)
                .onComplete(() -> completed.complete(null))
                .toList().run().toCompletableFuture().get(1, TimeUnit.SECONDS);

        completed.get(1, TimeUnit.SECONDS);
    }

    protected void testProcessor(Publisher<Long> publisher,
                                 Consumer<CountingSubscriber> testBody) {
        CountingSubscriber subscriber = new CountingSubscriber();
        publisher.subscribe(subscriber);
        testBody.accept(subscriber);
    }
}
