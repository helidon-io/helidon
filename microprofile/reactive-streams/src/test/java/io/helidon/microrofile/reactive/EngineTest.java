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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.helidon.common.reactive.DropWhileProcessor;
import io.helidon.common.reactive.FilterProcessor;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.PeekProcessor;
import io.helidon.common.reactive.RSCompatibleProcessor;
import io.helidon.common.reactive.TakeWhileProcessor;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.microprofile.reactive.streams.operators.CompletionSubscriber;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

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
                .filter(x -> x % 2 == 0)
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
                .filter(i -> i % 2 == 0)
                .peek(afterFilter::append)
                .limit(5L)
                .to(ReactiveStreams.fromSubscriber(new ConsumableSubscriber<>(sum::addAndGet, 10)))
                .run();
        assertEquals("1-2-3-4-5-6-7-8-9-10-", beforeFilter.toString());
        assertEquals("246810", afterFilter.toString());
        assertEquals(2 + 4 + 6 + 8 + 10, sum.get());
    }

    @Test
    void limit() {
        AtomicInteger sum = new AtomicInteger();
        IntSequencePublisher publisher = new IntSequencePublisher();
        ConsumableSubscriber<Integer> subscriber = new ConsumableSubscriber<>(sum::addAndGet);
        ReactiveStreams
                .fromPublisher(publisher)
                .peek(System.out::println)
                .limit(5)
                .peek(System.out::println)
                .buildRs()
                .subscribe(subscriber);
        assertEquals(1 + 2 + 3 + 4 + 5, sum.get());
    }

    @Test
    void subscriberCreation() throws ExecutionException, InterruptedException, TimeoutException {
        AtomicInteger peekedSum = new AtomicInteger();
        AtomicInteger sum = new AtomicInteger();
        IntSequencePublisher publisher = new IntSequencePublisher();
        CompletionSubscriber<Integer, Void> subscriber = ReactiveStreams.<Integer>builder()
                .limit(5)
                .peek(peekedSum::addAndGet)
                .forEach(sum::addAndGet)
                .build();
        publisher.subscribe(subscriber);
        subscriber.getCompletion().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(1 + 2 + 3 + 4 + 5, sum.get());
        assertEquals(1 + 2 + 3 + 4 + 5, peekedSum.get());
    }

    @Test
    void processorBuilder() {
        StringBuilder stringBuffer = new StringBuilder();

        Publisher<Integer> publisherBuilder =
                ReactiveStreams
                        .of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
                        .buildRs();

        Processor<Integer, String> processor = ReactiveStreams.<Integer>builder()
                .map(i -> i + 1)
                .flatMap(i -> ReactiveStreams.of(i, i))
                .map(i -> Integer.toString(i))
                .buildRs();

        ConsumableSubscriber<String> subscriber = new ConsumableSubscriber<>(stringBuffer::append);

        publisherBuilder.subscribe(processor);
        processor.subscribe(HybridSubscriber.from(subscriber));
        assertEquals("1122334455667788991010", stringBuffer.toString());
    }

    @Test
    void ofForEach() throws ExecutionException, InterruptedException {
        AtomicInteger sum = new AtomicInteger();
        ReactiveStreams
                .of(3, 4)
                .forEach(sum::addAndGet)
                .run().toCompletableFuture().get();
        assertEquals(3 + 4, sum.get());
    }

    @Test
    void publisherToForEach() {
        AtomicInteger sum = new AtomicInteger();
        Publisher<Integer> publisher = ReactiveStreams.of(3, 4).buildRs();
        ReactiveStreams
                .fromPublisher(publisher)
                .forEach(sum::addAndGet)
                .run();
        assertEquals(3 + 4, sum.get());
    }

    @Test
    void concat() throws InterruptedException, ExecutionException, TimeoutException {
        final List<Integer> resultList = new ArrayList<>();
        ReactiveStreams
                .concat(ReactiveStreams.of(1, 2, 3),
                        ReactiveStreams.of(4, 5, 6))
                .forEach(resultList::add).run()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6), resultList);
    }

    @Test
    void concatCancelOnFail() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();

        CompletionStage<Void> completion =
                ReactiveStreams
                        .concat(
                                ReactiveStreams.failed(new TestRuntimeException()),
                                ReactiveStreams.generate(() -> 1)
                                        .onTerminate(() -> cancelled.complete(null))
                        )
                        .ignore()
                        .run();

        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> completion.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void complStage() throws InterruptedException, ExecutionException, TimeoutException {
        final List<Integer> resultList = new ArrayList<>();
        CompletionStage<Void> run = ReactiveStreams.of(1, 2, 3)
                .forEach(resultList::add).run();
        run.toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(1, 2, 3), resultList);
    }

    @Test
    void collect() throws ExecutionException, InterruptedException {
        assertEquals(ReactiveStreams.of(1, 2, 3)
                .collect(() -> new AtomicInteger(0), AtomicInteger::addAndGet
                ).run().toCompletableFuture().get().get(), 6);
    }

    @Test
    void cancel() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<Void> result = ReactiveStreams.fromPublisher(s -> s.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.complete(null);
            }
        }))
                .cancel()
                .run();

        cancelled.get(1, TimeUnit.SECONDS);
        result.toCompletableFuture().get(1, TimeUnit.SECONDS);
    }

    @Test
    void cancelWithFailures() {
        ReactiveStreams
                .failed(new TestThrowable())
                .cancel()
                .run();
    }

    @Test
    void findFirst() throws InterruptedException, ExecutionException, TimeoutException {
        Optional<Integer> result = ReactiveStreams
                .of(1, 2, 3)
                .findFirst()
                .run()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);

        assertEquals(Integer.valueOf(1), result.get());
    }

    @Test
    void failed() {
        assertThrows(TestThrowable.class, () -> ReactiveStreams
                .failed(new TestThrowable())
                .findFirst()
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void finisherTest() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals("1, 2, 3", ReactiveStreams
                .of("1", "2", "3")
                .collect(Collectors.joining(", "))
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void collectorExceptionPropagation() {
        //supplier
        assertThrows(ExecutionException.class, () -> ReactiveStreams.of("1", "2", "3")
                .collect(Collector.<String, StringJoiner, String>of(
                        () -> {
                            throw new TestRuntimeException();
                        },
                        StringJoiner::add,
                        StringJoiner::merge,
                        StringJoiner::toString
                ))
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
        //accumulator
        assertThrows(ExecutionException.class, () -> ReactiveStreams.of("1", "2", "3")
                .collect(Collector.<String, StringJoiner, String>of(
                        () -> new StringJoiner(","),
                        (s, t) -> {
                            throw new TestRuntimeException();
                        },
                        StringJoiner::merge,
                        StringJoiner::toString
                ))
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);

        //finisher
        assertThrows(ExecutionException.class, () -> ReactiveStreams.of("1", "2", "3")
                .collect(Collector.<String, StringJoiner, String>of(
                        () -> new StringJoiner(","),
                        StringJoiner::add,
                        StringJoiner::merge,
                        f -> {
                            throw new TestRuntimeException();
                        }
                ))
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
    }


    @Test
    void onTerminate() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> terminator = new CompletableFuture<>();
        ReactiveStreams
                .of("1", "2", "3")
                .onTerminate(() -> terminator.complete(null))
                .collect(Collectors.joining(", "))
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS);
        terminator.get(1, TimeUnit.SECONDS);
    }

    @Test
    void publisherWithTerminate() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> terminator = new CompletableFuture<>();
        Publisher<? extends Number> publisher = ReactiveStreams.of(1, 2, 3)
                .onTerminate(() -> {
                    terminator.complete(null);
                })
                .buildRs();

        Optional<? extends Number> result = ReactiveStreams.fromPublisher(publisher)
                .findFirst().run().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(1, result.get());
        terminator.get(1, TimeUnit.SECONDS);
    }

    @Test
    void concatCancel() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<Void> completion = ReactiveStreams
                .concat(
                        ReactiveStreams.failed(new TestRuntimeException()),
                        ReactiveStreams.of(1, 2, 3)
                                .onTerminate(() -> {
                                    cancelled.complete(null);
                                })
                )
                .ignore()
                .run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> completion.toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
    }

    @Test
    void filter() throws InterruptedException, ExecutionException, TimeoutException {
        CompletionStage<List<Integer>> cs = ReactiveStreams.of(1, 2, 3, 4, 5, 6)
                .filter((i) -> {
                    return (i & 1) == 1;
                }).toList()
                .run();
        assertEquals(Arrays.asList(1, 3, 5), cs.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void publisherToSubscriber() throws InterruptedException, ExecutionException, TimeoutException {
        CompletionSubscriber<Object, Optional<Object>> subscriber = ReactiveStreams.builder()
                .limit(5L)
                .findFirst()
                .build();
        ReactiveStreams.of(1, 2, 3)
                .to(subscriber)
                .run()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        assertEquals(1, subscriber.getCompletion().toCompletableFuture().get(1, TimeUnit.SECONDS).get());
    }

    @Test
    void filterExceptionCancelUpstream() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Integer>> result = ReactiveStreams.of(1, 2, 3).onTerminate(() -> {
            cancelled.complete(null);
        }).filter((foo) -> {
            throw new TestRuntimeException();
        }).toList().run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class,
                () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS),
                TestRuntimeException.TEST_MSG);
    }

    @Test
    void streamOfStreams() throws InterruptedException, ExecutionException, TimeoutException {
        List<Integer> result = ReactiveStreams.of(ReactiveStreams.of(1, 2))
                .flatMap(i -> i)
                .toList()
                .run().toCompletableFuture()
                .get(1, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(1, 2), result);
    }

    @Test
    void reentrantFlatMapPublisher() throws InterruptedException, ExecutionException, TimeoutException {
        ProcessorBuilder<PublisherBuilder<Integer>, Integer> flatMap =
                ReactiveStreams.<PublisherBuilder<Integer>>builder()
                        .flatMap(Function.identity());
        assertEquals(Arrays.asList(1, 2), ReactiveStreams.of(
                ReactiveStreams.of(1, 2))
                .via(flatMap)
                .toList()
                .run().toCompletableFuture()
                .get(1, TimeUnit.SECONDS));
        assertEquals(Arrays.asList(3, 4),
                ReactiveStreams.of(ReactiveStreams.of(3, 4))
                        .via(flatMap)
                        .toList()
                        .run().toCompletableFuture()
                        .get(1, TimeUnit.SECONDS));
    }

    @Test
    void concatCancelOtherStage() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();

        CompletionStage<Void> completion = ReactiveStreams.concat(
                ReactiveStreams.failed(new TestRuntimeException()),
                ReactiveStreams.of(1, 2.3).onTerminate(() -> cancelled.complete(null)))
                .ignore()
                .run();

        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> completion.toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
    }

    @Test
    void flatMapExceptionPropagation() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = ReactiveStreams.of(1, 2, 3)
                .onTerminate(() -> cancelled.complete(null))
                .flatMap(foo -> {
                    throw new TestRuntimeException();
                })
                .toList()
                .run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
    }

    @Test
    void flatMapSubStreamException() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = ReactiveStreams.of(1, 2, 3)
                .onTerminate(() -> cancelled.complete(null))
                .flatMap(f -> ReactiveStreams.failed(new TestRuntimeException()))
                .toList()
                .run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class,
                () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS),
                TestRuntimeException.TEST_MSG);
    }

    @Test
    void dropWhile() throws InterruptedException, ExecutionException, TimeoutException {
        ProcessorBuilder<Integer, Integer> dropWhile = ReactiveStreams.<Integer>builder()
                .dropWhile(i -> i < 3);

        List<Integer> firstResult = ReactiveStreams.of(1, 2, 3, 4)
                .via(dropWhile)
                .toList()
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS);

        List<Integer> secondResult = ReactiveStreams.of(0, 1, 6, 7)
                .via(dropWhile)
                .toList()
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(Arrays.asList(3, 4), firstResult);
        assertEquals(Arrays.asList(6, 7), secondResult);
    }

    @Test
    void disctinct() throws InterruptedException, ExecutionException, TimeoutException {
        ProcessorBuilder<Integer, Integer> distinct = ReactiveStreams.<Integer>builder().distinct();
        List<Integer> firstResult = ReactiveStreams.of(1, 2, 2, 3)
                .via(distinct)
                .toList()
                .run().toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        List<Integer> secondResult = ReactiveStreams.of(3, 3, 4, 5)
                .via(distinct)
                .toList()
                .run().toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(1, 2, 3), firstResult);
        assertEquals(Arrays.asList(3, 4, 5), secondResult);
    }

    @Test
    void nullInMap() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = ReactiveStreams.of(1, 2, 3)
                .onTerminate(() -> cancelled.complete(null))
                .map(t -> null)
                .toList().run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void fromCompletionStage() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(Collections.singletonList("TEST"),
                ReactiveStreams.fromCompletionStage(CompletableFuture.completedFuture("TEST"))
                        .toList()
                        .run().toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void fromCompletionStageWithNullNegative() throws InterruptedException, ExecutionException, TimeoutException {
        assertThrows(ExecutionException.class, () -> ReactiveStreams.fromCompletionStage(CompletableFuture.completedFuture(null))
                .toList()
                .run().toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void fromCompletionStageWithNullPositive() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(Optional.empty(),
                ReactiveStreams.fromCompletionStageNullable(CompletableFuture.completedFuture(null))
                        .findFirst()
                        .run().toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void coupled() throws InterruptedException, ExecutionException, TimeoutException {

        CompletionSubscriber<Integer, List<Integer>> subscriber = ReactiveStreams.<Integer>builder().toList().build();
        Publisher<String> publisher = ReactiveStreams.of("4", "5", "6").buildRs();

        Processor<Integer, String> processor = ReactiveStreams.coupled(subscriber, publisher).buildRs();
//        Processor<Integer, String> processor = ReactiveStreams.<Integer>builder().map(String::valueOf).buildRs();

        List<String> result = ReactiveStreams.of(1, 2, 3)
                .via(processor)
                .peek(s -> {
                    System.out.println(">>>>" + s);
                })
                .toList()
                .run()
                .toCompletableFuture().get(1, TimeUnit.SECONDS);

        subscriber.getCompletion().toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertEquals(Arrays.asList("4", "5", "6"), result);
    }

    @Test
    void generate() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(Arrays.asList(4, 4, 4),
                ReactiveStreams.generate(() -> 4)
                        .limit(3L)
                        .toList()
                        .run()
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS));
    }

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
    void flatMapCancelPropagation() throws InterruptedException, ExecutionException, TimeoutException {
        try {
            ReactiveStreams.of(1, 2, 3)
                    .toList()
                    .run().toCompletableFuture().get(1, TimeUnit.SECONDS);
        } catch (ExecutionException ignored) {
            //There was a bug with non-reentrant BaseProcessor used in flatMap
        }

        CompletableFuture<Void> outerCancelled = new CompletableFuture<>();
        CompletableFuture<Void> innerCancelled = new CompletableFuture<>();
        ReactiveStreams.generate(() -> 4)
                .onTerminate(() -> outerCancelled.complete(null))
                .flatMap(i -> ReactiveStreams.generate(() -> 5)
                        .onTerminate(() -> innerCancelled.complete(null)))
                .limit(5)
                .toList()
                .run().toCompletableFuture().get(200, TimeUnit.MILLISECONDS);
        outerCancelled.get(200, TimeUnit.MILLISECONDS);
        innerCancelled.get(200, TimeUnit.MILLISECONDS);
    }

    @Test
    void flatMap() throws InterruptedException, ExecutionException, TimeoutException {
        List<Integer> result = ReactiveStreams.generate(() -> 4)
                .flatMap(i -> ReactiveStreams.of(9, 8, 7))
                .limit(4)
                .toList()
                .run().toCompletableFuture().get(200, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList(9, 8, 7, 9), result);
    }

    @Test
    void flatMapIterable() throws InterruptedException, ExecutionException, TimeoutException {
        List<Integer> result = ReactiveStreams.of(1, 2, 3)
                .flatMapIterable(n -> Arrays.asList(n, n, n))
                .toList()
                .run()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(1, 1, 1, 2, 2, 2, 3, 3, 3), result);
    }

    @Test
    void flatMapIterableFailOnNull() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = ReactiveStreams.generate(() -> 4).onTerminate(() -> cancelled.complete(null))
                .flatMapIterable(t -> Collections.singletonList(null))
                .toList().run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS));
    }

    @Test
    void flatMapCompletionStage() throws InterruptedException, ExecutionException, TimeoutException {
        ProcessorBuilder<Integer, Integer> mapper = ReactiveStreams.<Integer>builder()
                .flatMapCompletionStage(i -> CompletableFuture.completedFuture(i + 1));
        List<Integer> result1 = ReactiveStreams.of(1, 2, 3).via(mapper).toList().run().toCompletableFuture().get(1, TimeUnit.SECONDS);
        List<Integer> result2 = ReactiveStreams.of(4, 5, 6).via(mapper).toList().run().toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals(Arrays.asList(2, 3, 4), result1);
        assertEquals(Arrays.asList(5, 6, 7), result2);
    }

    @Test
    void coupledCompleteOnCancel() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> publisherCancelled = new CompletableFuture<>();
        CompletableFuture<Void> downstreamCompleted = new CompletableFuture<>();

        ReactiveStreams
                .fromCompletionStage(new CompletableFuture<>())
                .via(
                        ReactiveStreams
                                .coupled(ReactiveStreams.builder()
                                                .cancel(),
                                        ReactiveStreams
                                                .fromCompletionStage(new CompletableFuture<>())
                                                .onTerminate(() -> {
                                                    publisherCancelled.complete(null);
                                                }))
                )
                .onComplete(() -> downstreamCompleted.complete(null))
                .ignore()
                .run();

        publisherCancelled.get(1, TimeUnit.SECONDS);
        downstreamCompleted.get(1, TimeUnit.SECONDS);
    }

    @Test
    void coupledCompleteUpStreamOnCancel() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> subscriberCompleted = new CompletableFuture<>();
        CompletableFuture<Void> upstreamCancelled = new CompletableFuture<>();

        ReactiveStreams
                .fromCompletionStage(new CompletableFuture<>())
                .onTerminate(() -> upstreamCancelled.complete(null))
                .via(ReactiveStreams
                        .coupled(ReactiveStreams.builder().onComplete(() -> {
                                    subscriberCompleted.complete(null);
                                })
                                        .ignore(),
                                ReactiveStreams
                                        .fromCompletionStage(new CompletableFuture<>())))
                .cancel()
                .run();

        subscriberCompleted.get(1, TimeUnit.SECONDS);
        upstreamCancelled.get(1, TimeUnit.SECONDS);
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
    void coupledStageReentrant() {
        ProcessorBuilder<Object, Integer> coupled = ReactiveStreams.coupled(ReactiveStreams.builder().ignore(), ReactiveStreams.of(1, 2, 3));
        Supplier<List<Integer>> coupledTest = () -> {
            try {
                return ReactiveStreams
                        .fromCompletionStage(new CompletableFuture<>())
                        .via(coupled)
                        .toList()
                        .run()
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        };

        IntStream.range(0, 20).forEach(i -> {
            assertEquals(Arrays.asList(1, 2, 3), coupledTest.get());
        });
    }

    @Test
    void coupledCancelOnPublisherFail() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Throwable> subscriberFailed = new CompletableFuture<>();
        CompletableFuture<Void> upstreamCancelled = new CompletableFuture<>();

        ReactiveStreams
                .fromCompletionStage(new CompletableFuture<>())
                .onTerminate(() -> upstreamCancelled.complete(null))
                .via(
                        ReactiveStreams
                                .coupled(ReactiveStreams
                                                .builder()
                                                .onError(value -> {
                                                    subscriberFailed.complete(value);
                                                })
                                                .ignore(),
                                        ReactiveStreams
                                                .failed(new TestRuntimeException())))
                .ignore()
                .run();

        assertTrue(subscriberFailed.get(1, TimeUnit.SECONDS) instanceof TestRuntimeException);
        upstreamCancelled.get(1, TimeUnit.SECONDS);
    }

    @Test
    @Disabled
    void coupledCancelOnUpstreamFail() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> publisherCancelled = new CompletableFuture<>();
        CompletableFuture<Throwable> downstreamFailed = new CompletableFuture<>();

        ReactiveStreams.failed(new TestRuntimeException())
                .via(
                        ReactiveStreams.coupled(ReactiveStreams.builder().ignore(),
                                ReactiveStreams
                                        .fromCompletionStage(new CompletableFuture<>()).onTerminate(() -> publisherCancelled.complete(null)))
                ).onError(downstreamFailed::complete)
                .ignore()
                .run();

        publisherCancelled.get(1, TimeUnit.SECONDS);
        assertTrue(downstreamFailed.get(1, TimeUnit.SECONDS) instanceof TestRuntimeException);
    }

    @Test
    void limitToZero() throws InterruptedException, ExecutionException, TimeoutException {
        assertEquals(Collections.emptyList(), ReactiveStreams
                .generate(() -> 4)
                .limit(0L)
                .toList()
                .run()
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS));
    }

    @Test
    void mapOnError() throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        CompletionStage<List<Object>> result = ReactiveStreams.generate(() -> "test")
                .onTerminate(() -> cancelled.complete(null))
                .map(foo -> {
                    throw new TestRuntimeException();
                })
                .toList()
                .run();
        cancelled.get(1, TimeUnit.SECONDS);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(1, TimeUnit.SECONDS), TestRuntimeException.TEST_MSG);
    }

    @Test
    void finiteStream() throws InterruptedException, ExecutionException, TimeoutException {
        finiteOnCompleteTest(new PeekProcessor<>(integer -> Function.identity()));
        finiteOnCompleteTest(new FilterProcessor<>(integer -> true));
        finiteOnCompleteTest(new TakeWhileProcessor<>(integer -> true));
        finiteOnCompleteTest(new DropWhileProcessor<>(integer -> false));
    }

    @Test
    void limitProcessorTest() throws InterruptedException, TimeoutException, ExecutionException {
        testProcessor(new FilterProcessor<>(n -> n < 3), s -> {
            s.request(1);
            s.request(2);
        }, 3);
        testProcessor(new FilterProcessor<>(n -> n < 6), s -> {
            s.request(1);
            s.request(2);
        }, 6);
        testProcessor(new FilterProcessor<>(n -> n < 5), s -> {
            s.request(1);
            s.request(2);
        }, 6);
    }

    private <T, U> void finiteOnCompleteTest(Flow.Processor<Integer, Integer> processor)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<Void> completed = new CompletableFuture<>();
        ReactiveStreams.of(1, 2, 3)
                .via(HybridProcessor.from(processor))
                .onComplete(() -> completed.complete(null))
                .toList().run().toCompletableFuture().get(1, TimeUnit.SECONDS);

        completed.get(1, TimeUnit.SECONDS);
    }

    private void testProcessor(Flow.Processor<Integer, Integer> processor,
                               Consumer<CountingSubscriber> testBody,
                               int expectedSum) {
        if (processor instanceof RSCompatibleProcessor) {
            ((RSCompatibleProcessor<Integer, Integer>) processor).setRSCompatible(true);
        }
        CountingSubscriber subscriber = new CountingSubscriber();
        IntSequencePublisher intSequencePublisher = new IntSequencePublisher();
        intSequencePublisher.subscribe(HybridProcessor.from(processor));
        processor.subscribe(HybridSubscriber.from(subscriber));
        testBody.accept(subscriber);
        assertEquals(expectedSum, subscriber.getSum().get());
    }
}