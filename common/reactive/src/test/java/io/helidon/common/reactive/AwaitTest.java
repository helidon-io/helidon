/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.testng.Assert.assertThrows;

import org.junit.jupiter.api.Test;

public class AwaitTest {

    private static final long EXPECTED_SUM = 10L;
    private static final long SAFE_WAIT_MILLIS = 200L;

    @Test
    void sameInstanceCallbacks() throws ExecutionException, InterruptedException {
        CompletableFuture<String> peekFuture = new CompletableFuture<>();
        CompletableFuture<String> whenCompleteFuture = new CompletableFuture<>();

        Single<String> future =
                Single.just("1")
                .peek(peekFuture::complete);
        future.thenAccept(whenCompleteFuture::complete);

        assertThat("Peek needs to be invoked at await!", peekFuture.isDone(), is(not(true)));
        assertThat("WhenComplete needs to be invoked at await!", whenCompleteFuture.isDone(), is(not(true)));

        future.await();

        assertThat("Peek needs to be invoked at await!", peekFuture.isDone(), is(true));
        assertThat(peekFuture.get(), is(equalTo("1")));
        assertThat("WhenComplete needs to be invoked at await!", whenCompleteFuture.isDone(), is(true));
        assertThat(whenCompleteFuture.get(), is(equalTo("1")));
    }

    @Test
    void lazyCSConversion() throws ExecutionException, InterruptedException {
        CompletableFuture<String> peekFuture = new CompletableFuture<>();
        CompletableFuture<String> whenCompleteFuture = new CompletableFuture<>();

        CompletionAwaitable<String> awaitable = Single.just("1")
                .peek(peekFuture::complete)
                .whenComplete((s, throwable) -> whenCompleteFuture.complete(s));

        assertThat("Peek needs to be invoked at await!", peekFuture.isDone(), is(not(true)));
        assertThat("WhenComplete needs to be invoked at await!", whenCompleteFuture.isDone(), is(not(true)));

        awaitable.await(100, TimeUnit.MILLISECONDS);

        assertThat("Peek needs to be invoked at await!", peekFuture.isDone(), is(true));
        assertThat(peekFuture.get(), is(equalTo("1")));
        assertThat("WhenComplete needs to be invoked at await!", whenCompleteFuture.isDone(), is(true));
        assertThat(whenCompleteFuture.get(), is(equalTo("1")));
    }


    @Test
    void lazyCSConversionCallbackOrderSingle() {
        List<Integer> result = new ArrayList<>();
        AtomicInteger cnt = new AtomicInteger(0);
        Runnable registerCall = () -> result.add(cnt.incrementAndGet());

        CompletionAwaitable<String> awaitable = Single.just("2")
                .flatMapSingle(Single::just)
                .peek(s -> registerCall.run())
                .map(s -> {
                    registerCall.run();
                    return s;
                })
                .flatMapSingle(Single::just)
                .peek(s -> registerCall.run())
                .flatMapSingle(Single::just)
                .map(s -> {
                    registerCall.run();
                    return s;
                })
                .flatMapSingle(Single::just)
                .whenComplete((s, throwable) -> registerCall.run())
                .thenApply(s -> {
                    registerCall.run();
                    return s;
                })
                .whenComplete((s, throwable) -> registerCall.run());

        assertThat(result, emptyCollectionOf(Integer.class));
        awaitable.await(SAFE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(result, equalTo(IntStream.rangeClosed(1, cnt.get()).boxed().collect(Collectors.toList())));
    }


    @Test
    void lazyCSConversionCallbackOrderMulti() {
        List<Integer> result = new ArrayList<>();
        AtomicInteger cnt = new AtomicInteger(0);
        Runnable registerCall = () -> result.add(cnt.incrementAndGet());

        CompletionAwaitable<Void> awaitable = Multi.just(1L, 2L,3L )
                .flatMap(Single::just)
                .peek(s -> registerCall.run())
                .map(s -> {
                    registerCall.run();
                    return s;
                })
                .flatMap(Single::just)
                .peek(s -> registerCall.run())
                .flatMap(Single::just)
                .map(s -> {
                    registerCall.run();
                    return s;
                })
                .flatMap(Single::just)
                .forEach(aLong -> registerCall.run())
                .whenComplete((s, throwable) -> registerCall.run())
                .thenApply(s -> {
                    registerCall.run();
                    return s;
                })
                .whenComplete((s, throwable) -> registerCall.run());

        //For each operator triggers stream on its own, we are just waiting for it to finish
        awaitable.await(SAFE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(result, equalTo(IntStream.rangeClosed(1, cnt.get()).boxed().collect(Collectors.toList())));
    }

    @Test
    void forEachAwait() {
        AtomicLong sum = new AtomicLong();
        testMulti()
                .forEach(sum::addAndGet)
                .await();
        assertThat(sum.get(), equalTo(EXPECTED_SUM));
    }

    @Test
    void forEachAwaitChain() {
        AtomicLong sum = new AtomicLong();
        AtomicLong completedTimes = new AtomicLong();
        testMulti()
                .forEach(sum::addAndGet)
                .whenComplete((aVoid, throwable) -> completedTimes.incrementAndGet())
                .whenComplete((aVoid, throwable) -> completedTimes.incrementAndGet())
                .whenComplete((aVoid, throwable) -> completedTimes.incrementAndGet())
                .thenRun(completedTimes::incrementAndGet)
                .thenAccept(aVoid -> completedTimes.incrementAndGet())
                .await();
        assertThat(sum.get(), equalTo(EXPECTED_SUM));
        assertThat(completedTimes.get(), is(5L));
    }

    @Test
    void forEachWhenComplete() throws InterruptedException, ExecutionException, TimeoutException {
        AtomicLong sum = new AtomicLong();
        CompletableFuture<Void> completeFuture = new CompletableFuture<>();
        testMulti()
                .forEach(sum::addAndGet)

                .whenComplete((aVoid, throwable) -> Optional.ofNullable(throwable)
                        .ifPresentOrElse(completeFuture::completeExceptionally, () -> completeFuture.complete(null)));
        completeFuture.get(SAFE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(sum.get(), equalTo(EXPECTED_SUM));
    }

    @Test
    void forEachAwaitTimeout() {
        AtomicLong sum = new AtomicLong();
        testMulti()
                .forEach(sum::addAndGet)
                .await(SAFE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(sum.get(), equalTo(EXPECTED_SUM));
    }

    @Test
    void forEachCancel() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        Single<Void> single = testMulti()
                .onCancel(() -> cancelled.complete(null))
                .forEach(l -> latch.countDown());

        single.cancel();
        // Wait for 1 item out of 5
        latch.await(50, TimeUnit.MILLISECONDS);
        // Expect cancel eventually(100 millis had to be enough)
        cancelled.get(100, TimeUnit.MILLISECONDS);
    }

    @Test
    void forEachAwaitTimeoutNegative() {
        assertThrows(CompletionException.class, () -> testMulti()
                .forEach(TestConsumer.noop())
                .await(10, TimeUnit.MILLISECONDS));
    }

    @Test
    void singleAwait() {
        assertThat(testSingle().await(), equalTo(EXPECTED_SUM));
    }

    @Test
    void singleAwaitTimeout() {
        assertThat(testSingle().await(SAFE_WAIT_MILLIS, TimeUnit.MILLISECONDS), equalTo(EXPECTED_SUM));
    }

    @Test
    void singleAwaitTimeoutNegative() {
        assertThrows(CompletionException.class, () -> testSingle().await(10, TimeUnit.MILLISECONDS));
    }

    /**
     * Return stream of 5 long numbers 0,1,2,3,4 emitted in interval of 20 millis,
     * whole stream should be finished shortly after 100 millis.
     *
     * @return {@link io.helidon.common.reactive.Multi<Long>}
     */
    private Multi<Long> testMulti() {
        return Multi.interval(20, TimeUnit.MILLISECONDS, Executors.newSingleThreadScheduledExecutor())
                .limit(5);
    }

    private Single<Long> testSingle() {
        return testMulti().reduce(Long::sum);
    }
}
