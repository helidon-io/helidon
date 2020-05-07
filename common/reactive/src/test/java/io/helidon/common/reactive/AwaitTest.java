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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class AwaitTest {

    private static final long EXPECTED_SUM = 10L;
    private static final long SAFE_WAIT_MILLIS = 200L;

    @Test
    void forEachAwait() {
        AtomicLong sum = new AtomicLong();
        testMulti()
                .forEach(sum::addAndGet)
                .await();
        assertEquals(EXPECTED_SUM, sum.get());
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
        assertEquals(EXPECTED_SUM, sum.get());
        assertEquals(5, completedTimes.get());
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
        assertEquals(EXPECTED_SUM, sum.get());
    }

    @Test
    void forEachAwaitTimeout() {
        AtomicLong sum = new AtomicLong();
        testMulti()
                .forEach(sum::addAndGet)
                .await(SAFE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(EXPECTED_SUM, sum.get());
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
        assertEquals(EXPECTED_SUM, (long) testSingle().await());
    }

    @Test
    void singleAwaitTimeout() {
        assertEquals(EXPECTED_SUM, (long) testSingle().await(SAFE_WAIT_MILLIS, TimeUnit.MILLISECONDS));
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
