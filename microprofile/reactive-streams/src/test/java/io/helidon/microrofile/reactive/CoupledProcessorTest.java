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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.LongStream;

import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

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

        System.out.println(result);
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

        System.out.println(result);
    }
}
