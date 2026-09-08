/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResilientValueTest {
    @Test
    void retriesAndCachesSuccessfulValue() {
        AtomicInteger calls = new AtomicInteger();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 if (calls.incrementAndGet() < 3) {
                                                                     throw new ResilientValue.UnavailableException("not ready");
                                                                 }
                                                                 return "loaded";
                                                             },
                                                             retry(3),
                                                             circuitBreaker(),
                                                             timeout());

        assertThat(value.isLoaded(), is(false));
        assertThat(value.get(), is("loaded"));
        assertThat(value.isLoaded(), is(true));
        assertThat(value.get(), is("loaded"));
        assertThat(calls.get(), is(3));
    }

    @Test
    void circuitBreakerSuppressesLoadsAndAllowsRecovery() {
        TestExecutor executor = new TestExecutor();
        AtomicInteger calls = new AtomicInteger();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 if (calls.incrementAndGet() <= 3) {
                                                                     throw new ResilientValue.UnavailableException("not ready");
                                                                 }
                                                                 return "loaded";
                                                             },
                                                             retry(3),
                                                             circuitBreaker(executor),
                                                             timeout());

        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(calls.get(), is(3));

        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(calls.get(), is(3));

        assertThat(executor.runNext(), is(true));
        assertThat(value.get(), is("loaded"));
        assertThat(calls.get(), is(4));
    }

    @Test
    void retryTimeoutOpensCircuitBreaker() {
        TestExecutor executor = new TestExecutor();
        AtomicInteger calls = new AtomicInteger();
        RetryConfig retry = RetryConfig.builder()
                .calls(3)
                .delay(Duration.ofMillis(1))
                .delayFactor(2)
                .overallTimeout(Duration.ofNanos(1))
                .buildPrototype();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 calls.incrementAndGet();
                                                                 throw new ResilientValue.UnavailableException("not ready");
                                                             },
                                                             retry(retry),
                                                             circuitBreaker(executor),
                                                             timeout(Duration.ofNanos(1)));

        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(calls.get(), is(1));
        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(calls.get(), is(1));
    }

    @Test
    void timeoutIsRetriedInsideCircuitBreaker() {
        TestExecutor executor = new TestExecutor();
        FailingTimeout timeout = new FailingTimeout();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> "unreachable",
                                                             retry(2),
                                                             circuitBreaker(executor),
                                                             timeout);

        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(timeout.calls(), is(2));
        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(timeout.calls(), is(2));
    }

    @Test
    void validatesTimeoutAgainstRetry() {
        assertThrows(IllegalArgumentException.class,
                     () -> ResilientValue.create("test value",
                                                 () -> "value",
                                                 retry(1),
                                                 circuitBreaker(),
                                                 timeout(Duration.ZERO)));
        assertThrows(IllegalArgumentException.class,
                     () -> ResilientValue.create("test value",
                                                 () -> "value",
                                                 retry(1),
                                                 circuitBreaker(),
                                                 timeout(Duration.ofSeconds(2))));
        assertThrows(IllegalArgumentException.class,
                     () -> ResilientValue.create("test value",
                                                 () -> "value",
                                                 retry(1),
                                                 circuitBreaker(),
                                                 TimeoutConfig.builder()
                                                         .timeout(Duration.ofSeconds(1))
                                                         .currentThread(false)
                                                         .build()));
    }

    @Test
    void unexpectedExceptionBypassesRetryAndBreaker() {
        AtomicInteger calls = new AtomicInteger();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 if (calls.incrementAndGet() == 1) {
                                                                     throw new IllegalStateException("unexpected");
                                                                 }
                                                                 return "loaded";
                                                             },
                                                             retry(3),
                                                             circuitBreaker(),
                                                             timeout());

        assertThrows(IllegalStateException.class, value::get);
        assertThat(calls.get(), is(1));
        assertThat(value.get(), is("loaded"));
        assertThat(calls.get(), is(2));
    }

    @Test
    void suppliedRetryControlsExceptionClassification() {
        AtomicInteger calls = new AtomicInteger();
        Retry retry = RetryConfig.builder()
                .calls(2)
                .delay(Duration.ZERO)
                .delayFactor(0)
                .overallTimeout(Duration.ofSeconds(1))
                .addApplyOn(IllegalStateException.class)
                .build();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 if (calls.incrementAndGet() == 1) {
                                                                     throw new IllegalStateException("retry this");
                                                                 }
                                                                 return "loaded";
                                                             },
                                                             retry,
                                                             circuitBreaker(),
                                                             timeout());

        assertThat(value.get(), is("loaded"));
        assertThat(calls.get(), is(2));
    }

    @Test
    void nullResultIsNotCachedOrRetried() {
        AtomicInteger calls = new AtomicInteger();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> calls.incrementAndGet() == 1 ? null : "loaded",
                                                             retry(3),
                                                             circuitBreaker(),
                                                             timeout());

        assertThrows(NullPointerException.class, value::get);
        assertThat(calls.get(), is(1));
        assertThat(value.get(), is("loaded"));
        assertThat(calls.get(), is(2));
    }

    @Test
    void concurrentFollowerWaitsForLoad() throws InterruptedException {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch continueLoading = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> loaderResult = new AtomicReference<>();
        AtomicReference<String> followerResult = new AtomicReference<>();
        AtomicReference<Throwable> loaderFailure = new AtomicReference<>();
        AtomicReference<Throwable> followerFailure = new AtomicReference<>();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 calls.incrementAndGet();
                                                                 loading.countDown();
                                                                 await(continueLoading);
                                                                 return "loaded";
                                                             },
                                                             retry(1),
                                                             circuitBreaker(),
                                                             timeout());

        Thread loaderThread = Thread.ofVirtual().start(() -> {
            try {
                loaderResult.set(value.get());
            } catch (Throwable t) {
                loaderFailure.set(t);
            }
        });
        Thread followerThread = Thread.ofVirtual().unstarted(() -> {
            try {
                followerResult.set(value.get());
            } catch (Throwable t) {
                followerFailure.set(t);
            }
        });
        try {
            assertThat(loading.await(10, TimeUnit.SECONDS), is(true));
            followerThread.start();
            awaitWaiting(followerThread);
            assertThat(calls.get(), is(1));
        } finally {
            continueLoading.countDown();
            loaderThread.join(TimeUnit.SECONDS.toMillis(10));
            followerThread.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertThat(loaderThread.isAlive(), is(false));
        assertThat(followerThread.isAlive(), is(false));
        assertThat(loaderResult.get(), is("loaded"));
        assertThat(followerResult.get(), is("loaded"));
        assertThat(loaderFailure.get(), is((Throwable) null));
        assertThat(followerFailure.get(), is((Throwable) null));
        assertThat(calls.get(), is(1));
    }

    @Test
    void concurrentFollowerReceivesLoadFailure() throws InterruptedException {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch continueLoading = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Throwable> loaderFailure = new AtomicReference<>();
        AtomicReference<Throwable> followerFailure = new AtomicReference<>();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 calls.incrementAndGet();
                                                                 loading.countDown();
                                                                 await(continueLoading);
                                                                 throw new ResilientValue.UnavailableException("not ready");
                                                             },
                                                             retry(1),
                                                             circuitBreaker(),
                                                             timeout());

        Thread loaderThread = Thread.ofVirtual().start(() -> captureFailure(value, loaderFailure));
        Thread followerThread = Thread.ofVirtual().unstarted(() -> captureFailure(value, followerFailure));
        try {
            assertThat(loading.await(10, TimeUnit.SECONDS), is(true));
            followerThread.start();
            awaitWaiting(followerThread);
            assertThat(calls.get(), is(1));
        } finally {
            continueLoading.countDown();
            loaderThread.join(TimeUnit.SECONDS.toMillis(10));
            followerThread.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertThat(loaderThread.isAlive(), is(false));
        assertThat(followerThread.isAlive(), is(false));
        assertThat(loaderFailure.get(), instanceOf(ResilientValue.UnavailableException.class));
        assertThat(followerFailure.get(), instanceOf(ResilientValue.UnavailableException.class));
        assertThat(calls.get(), is(1));
    }

    @Test
    void timeoutDoesNotOverlapRetry() throws InterruptedException {
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        CountDownLatch firstAttemptInterrupted = new CountDownLatch(1);
        CountDownLatch releaseFirstAttempt = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 int call = calls.incrementAndGet();
                                                                 int currentActive = active.incrementAndGet();
                                                                 maximumActive.accumulateAndGet(currentActive, Math::max);
                                                                 try {
                                                                     if (call == 1) {
                                                                         firstAttemptStarted.countDown();
                                                                         awaitAfterInterrupt(releaseFirstAttempt,
                                                                                             firstAttemptInterrupted);
                                                                     }
                                                                     return "loaded";
                                                                 } finally {
                                                                     active.decrementAndGet();
                                                                 }
                                                             },
                                                             retry(2),
                                                             circuitBreaker(),
                                                             timeout(Duration.ofMillis(20)));

        Thread loaderThread = Thread.ofVirtual().start(() -> {
            try {
                result.set(value.get());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        try {
            assertThat(firstAttemptStarted.await(10, TimeUnit.SECONDS), is(true));
            assertThat(firstAttemptInterrupted.await(10, TimeUnit.SECONDS), is(true));
            assertThat(calls.get(), is(1));
            assertThat(active.get(), is(1));
        } finally {
            releaseFirstAttempt.countDown();
            loaderThread.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertThat(loaderThread.isAlive(), is(false));
        assertThat(result.get(), is("loaded"));
        assertThat(failure.get(), is((Throwable) null));
        assertThat(calls.get(), is(2));
        assertThat(maximumActive.get(), is(1));
    }

    @Test
    void callerInterruptStopsRetries() throws InterruptedException {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        ResilientValue<String> value = ResilientValue.create("test value",
                                                             () -> {
                                                                 calls.incrementAndGet();
                                                                 loading.countDown();
                                                                 try {
                                                                     release.await();
                                                                     return "unexpected";
                                                                 } catch (InterruptedException e) {
                                                                     throw new SupplierException(e);
                                                                 }
                                                             },
                                                             retry(2),
                                                             circuitBreaker(),
                                                             timeout(Duration.ofSeconds(1)));

        Thread loaderThread = Thread.ofVirtual().start(() -> {
            try {
                value.get();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            assertThat(loading.await(10, TimeUnit.SECONDS), is(true));
            loaderThread.interrupt();
            loaderThread.join(TimeUnit.SECONDS.toMillis(10));
        } finally {
            release.countDown();
        }

        assertThat(loaderThread.isAlive(), is(false));
        assertThat(failure.get(), instanceOf(ResilientValue.UnavailableException.class));
        assertThat(calls.get(), is(1));
        assertThat(interrupted.get(), is(true));
    }

    @Test
    void rejectsInvalidRetryConfiguration() {
        assertInvalidRetry("zero calls", it -> it.calls(0));
        assertInvalidRetry("negative delay", it -> it.delay(Duration.ofMillis(-1)));
        assertInvalidRetry("zero overall timeout", it -> it.overallTimeout(Duration.ZERO));
        assertInvalidRetry("invalid delay factor", it -> it.delayFactor(-2));
        assertInvalidRetry("NaN delay factor", it -> it.delayFactor(Double.NaN));
        assertInvalidRetry("infinite delay factor", it -> it.delayFactor(Double.POSITIVE_INFINITY));
        assertInvalidRetry("negative jitter", it -> it.jitter(Duration.ofSeconds(-2)));

        RetryConfig factorTakesPrecedence = RetryConfig.builder()
                .delayFactor(2)
                .jitter(Duration.ofMillis(10))
                .buildPrototype();
        RetryConfig unusedLargeFactor = RetryConfig.builder()
                .calls(2)
                .delay(Duration.ofMillis(1))
                .delayFactor(Double.MAX_VALUE)
                .buildPrototype();
        RetryConfig unusedLargeDelay = RetryConfig.builder()
                .calls(1)
                .delay(Duration.ofMillis(Long.MAX_VALUE))
                .buildPrototype();

        assertThat(factorTakesPrecedence.delayFactor(), is(2D));
        assertThat(unusedLargeFactor.delayFactor(), is(Double.MAX_VALUE));
        assertThat(unusedLargeDelay.delay(), is(Duration.ofMillis(Long.MAX_VALUE)));
    }

    @Test
    void rejectsInvalidCircuitBreakerConfiguration() {
        assertInvalidCircuitBreaker("zero volume", it -> it.volume(0));
        assertInvalidCircuitBreaker("zero error ratio", it -> it.errorRatio(0));
        assertInvalidCircuitBreaker("error ratio over 100", it -> it.errorRatio(101));
        assertInvalidCircuitBreaker("zero success threshold", it -> it.successThreshold(0));
        assertInvalidCircuitBreaker("negative delay", it -> it.delay(Duration.ofMillis(-1)));
        assertInvalidCircuitBreaker("overflowing delay", it -> it.delay(Duration.ofSeconds(Long.MAX_VALUE)));
        assertInvalidCircuitBreaker("overflowing volume and ratio",
                                    it -> it.volume(Integer.MAX_VALUE).errorRatio(2));
    }

    @Test
    void hasFaultToleranceDefaults() {
        RetryConfig retry = RetryConfig.create();
        CircuitBreakerConfig breaker = CircuitBreakerConfig.create();

        assertThat(retry.calls(), is(3));
        assertThat(retry.delay(), is(Duration.ofMillis(200)));
        assertThat(retry.delayFactor(), is(-1D));
        assertThat(retry.overallTimeout(), is(Duration.ofSeconds(1)));
        assertThat(breaker.volume(), is(10));
        assertThat(breaker.errorRatio(), is(60));
        assertThat(breaker.delay(), is(Duration.ofSeconds(5)));
        assertThat(breaker.successThreshold(), is(1));
    }

    @Test
    void configFactoriesPreserveFaultToleranceDefaults() {
        RetryConfig retry = RetryConfig.create(config(Map.of("calls", "5")));
        CircuitBreakerConfig breaker = CircuitBreakerConfig.create(config(Map.of("delay", "PT2S")));

        assertThat(retry.calls(), is(5));
        assertThat(retry.delay(), is(Duration.ofMillis(200)));
        assertThat(retry.overallTimeout(), is(Duration.ofSeconds(1)));
        assertThat(breaker.delay(), is(Duration.ofSeconds(2)));
        assertThat(breaker.volume(), is(10));
        assertThat(breaker.errorRatio(), is(60));
        assertThat(breaker.successThreshold(), is(1));

        TestExecutor executor = new TestExecutor();
        AtomicInteger calls = new AtomicInteger();
        RetryConfig testRetry = RetryConfig.create(config(Map.of("calls", "5",
                                                                  "delay", "PT0S",
                                                                  "delay-factor", "0")));
        CircuitBreakerConfig testBreaker = CircuitBreakerConfig.builder(breaker)
                .volume(1)
                .errorRatio(100)
                .delay(Duration.ZERO)
                .executor(executor)
                .buildPrototype();
        ResilientValue<String> value = ResilientValue.create("configured test value",
                                                             () -> {
                                                                 calls.incrementAndGet();
                                                                 throw new ResilientValue.UnavailableException("safe failure");
                                                             },
                                                             retry(testRetry),
                                                             circuitBreaker(testBreaker),
                                                             timeout());

        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(calls.get(), is(5));
        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(calls.get(), is(5));
    }

    @Test
    void logsOnlyRealLoadFailureAndRecovery() throws InterruptedException {
        Logger logger = Logger.getLogger(ResilientValue.class.getName());
        CapturingHandler handler = new CapturingHandler();
        Level originalLevel = logger.getLevel();
        boolean originalUseParentHandlers = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);

        try {
            assertFailureAndRecoveryLogs(handler);
            assertFollowerDoesNotLog(handler);
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(originalUseParentHandlers);
            logger.setLevel(originalLevel);
        }
    }

    private static Retry retry(int calls) {
        return RetryConfig.builder()
                .calls(calls)
                .delay(Duration.ZERO)
                .delayFactor(0)
                .overallTimeout(Duration.ofSeconds(1))
                .addApplyOn(ResilientValue.UnavailableException.class)
                .build();
    }

    private static Timeout timeout() {
        return timeout(Duration.ofSeconds(1));
    }

    private static Timeout timeout(Duration duration) {
        return TimeoutConfig.builder()
                .timeout(duration)
                .currentThread(true)
                .build();
    }

    private static CircuitBreaker circuitBreaker() {
        return CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofDays(1))
                .addApplyOn(ResilientValue.UnavailableException.class)
                .build();
    }

    private static CircuitBreaker circuitBreaker(TestExecutor executor) {
        return CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ZERO)
                .executor(executor)
                .addApplyOn(ResilientValue.UnavailableException.class)
                .build();
    }

    private static Retry retry(RetryConfig config) {
        return RetryConfig.builder(config)
                .clearApplyOn()
                .addApplyOn(ResilientValue.UnavailableException.class)
                .clearSkipOn()
                .build();
    }

    private static CircuitBreaker circuitBreaker(CircuitBreakerConfig config) {
        return CircuitBreakerConfig.builder(config)
                .clearApplyOn()
                .addApplyOn(ResilientValue.UnavailableException.class)
                .clearSkipOn()
                .build();
    }

    private static Config config(Map<String, String> values) {
        return Config.just(ConfigSources.create(values));
    }

    private static void assertFailureAndRecoveryLogs(CapturingHandler handler) {
        TestExecutor executor = new TestExecutor();
        AtomicInteger calls = new AtomicInteger();
        ResilientValue<String> value = ResilientValue.create("logged test value",
                                                             () -> {
                                                                 if (calls.incrementAndGet() == 1) {
                                                                    throw new ResilientValue.UnavailableException(
                                                                            "safe failure detail",
                                                                            new IllegalStateException("raw cause"));
                                                                 }
                                                                 return "loaded";
                                                             },
                                                             retry(1),
                                                             circuitBreaker(executor),
                                                             timeout());

        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThrows(ResilientValue.UnavailableException.class, value::get);
        assertThat(executor.runNext(), is(true));
        assertThat(value.get(), is("loaded"));

        assertThat(handler.messages(Level.WARNING),
                   is(List.of("logged test value is unavailable; retries are exhausted: safe failure detail")));
        assertThat(handler.messages(Level.INFO), is(List.of("logged test value is available again")));
        assertThat(String.join("\n", handler.messages()),
                   not(containsString("raw cause")));
        assertThat(handler.hasThrown(), is(false));
    }

    private static void assertFollowerDoesNotLog(CapturingHandler handler) throws InterruptedException {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch continueLoading = new CountDownLatch(1);
        AtomicReference<Throwable> loaderFailure = new AtomicReference<>();
        AtomicReference<Throwable> followerFailure = new AtomicReference<>();
        ResilientValue<String> value = ResilientValue.create("concurrent logged value",
                                                             () -> {
                                                                 loading.countDown();
                                                                 await(continueLoading);
                                                                 return "loaded";
                                                             },
                                                             retry(1),
                                                             circuitBreaker(),
                                                             timeout());
        Thread loaderThread = Thread.ofVirtual().start(() -> {
            try {
                value.get();
            } catch (Throwable t) {
                loaderFailure.set(t);
            }
        });
        Thread followerThread = Thread.ofVirtual().unstarted(() -> {
            try {
                value.get();
            } catch (Throwable t) {
                followerFailure.set(t);
            }
        });

        try {
            assertThat(loading.await(10, TimeUnit.SECONDS), is(true));
            followerThread.start();
            awaitWaiting(followerThread);
        } finally {
            continueLoading.countDown();
            loaderThread.join(TimeUnit.SECONDS.toMillis(10));
            followerThread.join(TimeUnit.SECONDS.toMillis(10));
        }
        assertThat(loaderThread.isAlive(), is(false));
        assertThat(followerThread.isAlive(), is(false));
        assertThat(loaderFailure.get(), is((Throwable) null));
        assertThat(followerFailure.get(), is((Throwable) null));
        assertThat(handler.messages(Level.WARNING).size(), is(1));
        assertThat(handler.messages(Level.INFO).size(), is(1));
    }

    private static void assertInvalidRetry(String message, Consumer<RetryConfig.Builder> configurer) {
        RetryConfig.Builder builder = RetryConfig.builder();
        configurer.accept(builder);
        assertThrows(IllegalArgumentException.class, builder::buildPrototype, message);
    }

    private static void assertInvalidCircuitBreaker(String message,
                                                    Consumer<CircuitBreakerConfig.Builder> configurer) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.builder();
        configurer.accept(builder);
        assertThrows(IllegalArgumentException.class, builder::buildPrototype, message);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting in test loader", e);
        }
    }

    private static void awaitAfterInterrupt(CountDownLatch latch, CountDownLatch interrupted) {
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException _) {
                interrupted.countDown();
            }
        }
    }

    private static void captureFailure(ResilientValue<?> value, AtomicReference<Throwable> failure) {
        try {
            value.get();
        } catch (Throwable t) {
            failure.set(t);
        }
    }

    private static void awaitWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (thread.isAlive() && thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        assertThat(thread.getState(), is(Thread.State.WAITING));
    }

    private static final class TestExecutor extends AbstractExecutorService {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> remaining = List.copyOf(tasks);
            tasks.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && tasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        boolean runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                return false;
            }
            task.run();
            return true;
        }
    }

    private static final class FailingTimeout implements Timeout {
        private final AtomicInteger calls = new AtomicInteger();
        private final TimeoutConfig prototype = TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(1))
                .currentThread(true)
                .buildPrototype();

        @Override
        public String name() {
            return "failing-timeout";
        }

        @Override
        public <T> T invoke(Supplier<? extends T> supplier) {
            calls.incrementAndGet();
            throw new TimeoutException("Expected test timeout");
        }

        @Override
        public TimeoutConfig prototype() {
            return prototype;
        }

        int calls() {
            return calls.get();
        }
    }

    private static final class CapturingHandler extends Handler {
        private final Formatter formatter = new SimpleFormatter();
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            records.clear();
        }

        List<String> messages() {
            return records.stream()
                    .map(formatter::formatMessage)
                    .toList();
        }

        List<String> messages(Level level) {
            return records.stream()
                    .filter(it -> it.getLevel().equals(level))
                    .map(formatter::formatMessage)
                    .toList();
        }

        boolean hasThrown() {
            return records.stream().anyMatch(it -> it.getThrown() != null);
        }
    }
}
