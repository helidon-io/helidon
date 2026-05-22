/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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
 */

package io.helidon.microprofile.faulttolerance;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.annotation.Metric;

/**
 * A bean with methods that update metrics.
 */
@Dependent
class MetricsBean {

    private AtomicInteger invocations = new AtomicInteger(0);

    @Inject
    @Metric(name = "counter")
    private Counter counter;
    private final CountDownLatch retryingBulkheadEntered = new CountDownLatch(1);
    private final CountDownLatch retryingBulkheadRelease = new CountDownLatch(1);
    private final CountDownLatch timedBulkheadEntered = new CountDownLatch(1);
    private final CountDownLatch timedBulkheadRelease = new CountDownLatch(1);
    private final AtomicInteger retryingBulkheadAttempts = new AtomicInteger();

    Counter getCounter() {
        return counter;
    }

    @Retry(maxRetries = 5, delay = 50L)
    String retryOne(int number) {
        if (invocations.incrementAndGet() <= number) {
            FaultToleranceTest.printStatus("MetricsBean::retryOne()", "failure");
            throw new RuntimeException("Oops");
        }
        FaultToleranceTest.printStatus("MetricsBean::retryOne()", "success");
        return "success";
    }

    @Retry(maxRetries = 5, delay = 50L)
    String retryTwo(int number) {
        if (invocations.incrementAndGet() <= number) {
            FaultToleranceTest.printStatus("MetricsBean::retryTwo()", "failure");
            throw new RuntimeException("Oops");
        }
        FaultToleranceTest.printStatus("MetricsBean::retryTwo()", "success");
        return "success";
    }

    @Retry(maxRetries = 5, delay = 50L)
    String retryThree(int number) {
        if (invocations.incrementAndGet() <= number) {
            FaultToleranceTest.printStatus("MetricsBean::retryThree()", "failure");
            throw new RuntimeException("Oops");
        }
        FaultToleranceTest.printStatus("MetricsBean::retryThree()", "success");
        return "success";
    }

    @Retry(maxRetries = 5, delay = 50L)
    String retryFour(int number) {
        if (invocations.incrementAndGet() <= number) {
            FaultToleranceTest.printStatus("MetricsBean::retryFour()", "failure");
            throw new RuntimeException("Oops");
        }
        FaultToleranceTest.printStatus("MetricsBean::retryFour()", "success");
        return "success";
    }

    @Retry(maxRetries = 5, delay = 50L)
    String retryFive(int number) {
        FaultToleranceTest.printStatus("MetricsBean::retryFive()", "success");
        return "success";
    }

    @Timeout(value = 1000, unit = ChronoUnit.MILLIS)
    String forceTimeout() throws InterruptedException {
        FaultToleranceTest.printStatus("MetricsBean::forceTimeout()", "failure");
        Thread.sleep(1500);
        return "failure";
    }

    @Timeout(value = 1000, unit = ChronoUnit.MILLIS)
    String noTimeout() throws InterruptedException {
        FaultToleranceTest.printStatus("MetricsBean::noTimeout()", "success");
        Thread.sleep(500);
        return "success";
    }

    @CircuitBreaker(
        successThreshold = CircuitBreakerBean.SUCCESS_THRESHOLD,
        requestVolumeThreshold = CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD,
        failureRatio = CircuitBreakerBean.FAILURE_RATIO,
        delay = CircuitBreakerBean.DELAY)
    void exerciseBreaker(boolean success) {
        if (success) {
            FaultToleranceTest.printStatus("MetricsBean::exerciseBreaker()", "success");
        } else {
            FaultToleranceTest.printStatus("MetricsBean::exerciseBreaker()", "failure");
            throw new RuntimeException("Oops");
        }
    }

    @CircuitBreaker(
        successThreshold = CircuitBreakerBean.SUCCESS_THRESHOLD,
        requestVolumeThreshold = CircuitBreakerBean.REQUEST_VOLUME_THRESHOLD,
        failureRatio = CircuitBreakerBean.FAILURE_RATIO,
        delay = CircuitBreakerBean.DELAY)
    void exerciseGauges(boolean success) {
        if (success) {
            FaultToleranceTest.printStatus("MetricsBean::exerciseGauges()", "success");
        } else {
            FaultToleranceTest.printStatus("MetricsBean::exerciseGauges()", "failure");
            throw new RuntimeException("Oops");
        }
    }

    static class TestException extends Exception {
    }

    @CircuitBreaker(requestVolumeThreshold = 2,
            failureRatio = 1.0D,
            delay = 1000,
            successThreshold = 2,
            failOn = {TestException.class})
    void exerciseBreakerException(boolean runtime) throws Exception {
        throw runtime ? new RuntimeException("oops") : new TestException();
    }

    @Fallback(fallbackMethod = "onFailure")
    String fallback() {
        FaultToleranceTest.printStatus("MetricsBean::fallback()", "failure");
        throw new RuntimeException("Oops");
    }

    String onFailure() {
        FaultToleranceTest.printStatus("MetricsBean::onFailure()", "success");
        return "fallback";
    }

    @Asynchronous
    @Bulkhead(value = 3, waitingTaskQueue = 3)
    CompletableFuture<String> concurrent(long sleepMillis) {
        FaultToleranceTest.printStatus("MetricsBean::concurrent()", "success");
        try {
            Thread.sleep(sleepMillis);
        } catch (Exception e) {
            // falls through
        }
        return CompletableFuture.completedFuture("success");
    }

    @Asynchronous
    @Bulkhead(value = 3, waitingTaskQueue = 3)
    CompletableFuture<String> concurrentAsync(long sleepMillis) {
        FaultToleranceTest.printStatus("MetricsBean::concurrentAsync()", "success");
        try {
            Thread.sleep(sleepMillis);
        } catch (Exception e) {
            // falls through
        }
        return CompletableFuture.completedFuture("success");
    }

    @Asynchronous
    @Retry(maxRetries = 1, delay = 10L, retryOn = TimeoutException.class)
    @Timeout(value = 300, unit = ChronoUnit.MILLIS)
    @Bulkhead(value = 1, waitingTaskQueue = 4)
    CompletableFuture<String> retryingBulkhead(boolean block) {
        int attempt = retryingBulkheadAttempts.incrementAndGet();
        FaultToleranceTest.printStatus("MetricsBean::retryingBulkhead()", "attempt " + attempt);
        if (block) {
            retryingBulkheadEntered.countDown();
            await(retryingBulkheadRelease);
            return CompletableFuture.completedFuture("blocker");
        }
        return CompletableFuture.completedFuture("retry-" + attempt);
    }

    boolean awaitRetryingBulkheadEntered() throws InterruptedException {
        return retryingBulkheadEntered.await(5, TimeUnit.SECONDS);
    }

    void releaseRetryingBulkhead() {
        retryingBulkheadRelease.countDown();
    }

    @Asynchronous
    @Timeout(value = 300, unit = ChronoUnit.MILLIS)
    @Bulkhead(value = 1, waitingTaskQueue = 1)
    CompletableFuture<String> timedBulkhead(boolean block) {
        FaultToleranceTest.printStatus("MetricsBean::timedBulkhead()", block ? "block" : "success");
        if (block) {
            timedBulkheadEntered.countDown();
            await(timedBulkheadRelease);
            return CompletableFuture.completedFuture("blocker");
        }
        return CompletableFuture.completedFuture("queued");
    }

    boolean awaitTimedBulkheadEntered() throws InterruptedException {
        return timedBulkheadEntered.await(5, TimeUnit.SECONDS);
    }

    void releaseTimedBulkhead() {
        timedBulkheadRelease.countDown();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for controlled bulkhead release");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
