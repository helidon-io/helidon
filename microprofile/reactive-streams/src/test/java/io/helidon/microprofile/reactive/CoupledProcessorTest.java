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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import io.helidon.common.reactive.MultiTappedProcessor;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class CoupledProcessorTest extends AbstractProcessorTest {

    @Override
    protected Publisher<Long> getPublisher(long items) {
        return ReactiveStreams.coupled(ReactiveStreams.builder().ignore(), ReactiveStreams.fromIterable(
                () -> LongStream.rangeClosed(1, items).boxed().iterator()
        )).buildRs();
    }

    @Override
    protected Processor<Long, Long> getFailedProcessor(RuntimeException t) {
        return ReactiveStreams.coupled(
                ReactiveStreams.<Long>builder().ignore(),
                ReactiveStreams.<Long>failed(new TestRuntimeException()))
                .buildRs();
    }

    @Test
    void coupledProcessorAsProcessor() throws InterruptedException, ExecutionException, TimeoutException {
        ProcessorBuilder<Object, Long> processorBuilder = ReactiveStreams.coupled(ReactiveStreams.builder().ignore(), ReactiveStreams.fromIterable(
                () -> LongStream.rangeClosed(1, 5).boxed().iterator()
        ));

        List<Long> result = ReactiveStreams.of(3L, 2L, 3L)
                .via(processorBuilder)
                .toList().run().toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), result);
    }

    @Test
    void coupledProcessorAsPublisher() throws InterruptedException, ExecutionException, TimeoutException {
        Processor<Object, Long> processor = ReactiveStreams
                .coupled(
                        ReactiveStreams.builder().ignore(),
                        ReactiveStreams.fromIterable(() -> LongStream.rangeClosed(1, 3).boxed().iterator())
                )
                .buildRs();

        List<Long> result = ReactiveStreams.fromPublisher(processor)
                .toList()
                .run()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);

        assertEquals(List.of(1L, 2L, 3L), result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void spec317() throws InterruptedException, ExecutionException, TimeoutException {

        MockPublisher mp = new MockPublisher();
        Processor<Long, Long> sub = new Processor<Long, Long>() {

            private Optional<Subscription> subscription = Optional.empty();
            AtomicInteger counter = new AtomicInteger(10);

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = Optional.of(subscription);
            }

            @Override
            public void subscribe(Subscriber s) {
                //mock request
                System.out.printf("Requesting %d counter: %d%n", 1, counter.get());
                subscription.get().request(1);
            }

            @Override
            public void onNext(Long o) {
                subscription.ifPresent(s -> {
                    if (counter.getAndDecrement() > 0) {
                        System.out.printf("Requesting %d counter: %d%n", Long.MAX_VALUE - 1, counter.get());
                        s.request(Long.MAX_VALUE - 1);
                    } else {
                        s.cancel();
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                fail(t);
            }

            @Override
            public void onComplete() {

            }
        };
        HybridProcessor tappedProcessor = HybridProcessor.from(MultiTappedProcessor.create());

        Processor<Long, Long> processor = ReactiveStreams
                .coupled(
                        tappedProcessor,
                        tappedProcessor
                )
                .buildRs();

        CompletionStage<Void> completionStage = ReactiveStreams
                .fromPublisher(new IntSequencePublisher())
                .map(Long::valueOf)
                .via(processor)
                .to(sub)
                .run();

        //signal request 1 to kickoff overflow simulation
        sub.subscribe(null);

        //is cancelled afe
        assertThrows(CancellationException.class, () -> completionStage.toCompletableFuture().get(3, TimeUnit.SECONDS));
    }

    @Override
    void cancelOnError() {
    }
}
