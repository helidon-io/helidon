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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;

public class FlatMapCompletionStageProcessorTest extends AbstractProcessorTest {
    @Override
    protected Processor<Long, Long> getProcessor() {
        return ReactiveStreams.<Long>builder().flatMapCompletionStage(CompletableFuture::completedFuture).buildRs();
    }

    @Override
    protected Processor<Long, Long> getFailedProcessor(RuntimeException t) {
        return ReactiveStreams.<Long>builder().<Long>flatMapCompletionStage(i -> {
            throw t;
        }).buildRs();
    }

    @Test
    @SuppressWarnings("unchecked")
    void futuresMapping() throws InterruptedException, TimeoutException, ExecutionException {
        CompletableFuture<Integer> one = new CompletableFuture<>();
        CompletableFuture<Integer> two = new CompletableFuture<>();
        CompletableFuture<Integer> three = new CompletableFuture<>();

        CompletionStage<List<Integer>> result = ReactiveStreams.of(one, two, three)
                .flatMapCompletionStage(i -> i)
                .toList()
                .run();

        Thread.sleep(100);

        one.complete(1);
        two.complete(2);
        three.complete(3);

        assertEquals(Arrays.asList(1, 2, 3), result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void futuresOrder() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Integer> one = new CompletableFuture<>();
        CompletableFuture<Integer> two = new CompletableFuture<>();
        CompletableFuture<Integer> three = new CompletableFuture<>();

        CompletionStage<List<Integer>> result = ReactiveStreams.of(one, two, three)
                .flatMapCompletionStage(Function.identity())
                .toList()
                .run();

        three.complete(3);
        Thread.sleep(100);
        two.complete(2);
        Thread.sleep(100);
        one.complete(1);

        assertEquals(Arrays.asList(1, 2, 3), result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void oneAtTime() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Integer> one = new CompletableFuture<>();
        CompletableFuture<Integer> two = new CompletableFuture<>();
        CompletableFuture<Integer> three = new CompletableFuture<>();

        AtomicInteger concurrentMaps = new AtomicInteger(0);
        AtomicLong c = new AtomicLong(0);
        CompletionStage<List<Integer>> result = ReactiveStreams.of(one, two, three)
                .flatMapCompletionStage(i -> {
                    assertEquals(1, concurrentMaps.incrementAndGet(), ">>" + c.incrementAndGet());
                    return i;
                })
                .toList()
                .run();

        Thread.sleep(100);
        concurrentMaps.decrementAndGet();
        one.complete(1);
        Thread.sleep(100);
        concurrentMaps.decrementAndGet();
        two.complete(2);
        Thread.sleep(100);
        concurrentMaps.decrementAndGet();
        three.complete(3);

        assertEquals(Arrays.asList(1, 2, 3), result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void failOnNull() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = ReactiveStreams.generate(() -> 4).onTerminate(() -> cancelled.complete(null))
                .flatMapCompletionStage(t -> CompletableFuture.completedFuture(null))
                .toList().run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void failedCs() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = ReactiveStreams.generate(() -> 4)
                .onTerminate(() -> cancelled.complete(null))
                .flatMapCompletionStage(i -> {
                    CompletableFuture<Object> failed = new CompletableFuture<>();
                    failed.completeExceptionally(new TestRuntimeException());
                    return failed;
                })
                .toList()
                .run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
    }
}
