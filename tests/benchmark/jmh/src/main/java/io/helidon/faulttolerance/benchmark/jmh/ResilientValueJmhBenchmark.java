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

package io.helidon.faulttolerance.benchmark.jmh;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.faulttolerance.ResilientValueConfig;
import io.helidon.faulttolerance.Timeout;
import io.helidon.faulttolerance.TimeoutConfig;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ResilientValueJmhBenchmark {
    private static final String VALUE = "value";

    private String directValue;
    private ResilientValue<String> cachedValue;
    private ResilientValue<String> openValue;
    private AtomicInteger cachedLoads;
    private AtomicInteger unavailableLoads;

    @Setup(Level.Trial)
    public void setup() {
        directValue = VALUE;
        cachedLoads = new AtomicInteger();
        unavailableLoads = new AtomicInteger();

        var cachedRetry = RetryConfig.builder()
                .overallTimeout(Duration.ofSeconds(1))
                .enableMetrics(false)
                .build();
        var cachedCircuitBreaker = CircuitBreakerConfig.builder()
                .enableMetrics(false)
                .build();
        var cachedTimeout = TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(1))
                .currentThread(true)
                .enableMetrics(false)
                .build();
        cachedValue = ResilientValue.create(new ResilientConfig<>("jmh-cached", () -> {
            cachedLoads.incrementAndGet();
            return VALUE;
        }, cachedRetry, cachedCircuitBreaker, cachedTimeout));
        cachedValue.get();

        var retry = RetryConfig.builder()
                .calls(1)
                .delay(Duration.ZERO)
                .overallTimeout(Duration.ofSeconds(1))
                .enableMetrics(false)
                .addApplyOn(ResilientValue.UnavailableException.class)
                .build();
        var circuitBreaker = CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofHours(1))
                .enableMetrics(false)
                .addApplyOn(ResilientValue.UnavailableException.class)
                .build();
        var timeout = TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(1))
                .currentThread(true)
                .enableMetrics(false)
                .build();
        openValue = ResilientValue.create(new ResilientConfig<>("jmh-open", () -> {
            unavailableLoads.incrementAndGet();
            throw new ResilientValue.UnavailableException("Expected benchmark failure");
        }, retry, circuitBreaker, timeout));

        try {
            openValue.get();
            throw new IllegalStateException("Expected the resilient value load to fail");
        } catch (ResilientValue.UnavailableException _) {
            // expected; the circuit breaker is now open
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (cachedLoads.get() != 1) {
            throw new IllegalStateException("Cached value was loaded " + cachedLoads.get() + " times");
        }
        if (unavailableLoads.get() != 1) {
            throw new IllegalStateException("Unavailable value was loaded " + unavailableLoads.get() + " times");
        }
    }

    @Benchmark
    @Threads(4)
    public String directFieldRead() {
        return directValue;
    }

    @Benchmark
    @Threads(4)
    public String cachedValueRead() {
        return cachedValue.get();
    }

    @Benchmark
    @Threads(4)
    public boolean openBreakerRejection() {
        try {
            openValue.get();
            return false;
        } catch (ResilientValue.UnavailableException _) {
            return true;
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Threads(1)
    public String firstLoad(FirstLoadState state) {
        return state.value.get();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Group("contendedFirstLoad")
    @GroupThreads(1)
    public String contendedFirstLoadLeader(ContendedFirstLoadState state) {
        return state.loadAsLeader();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Group("contendedFirstLoad")
    @GroupThreads(1)
    public String contendedFirstLoadFollower(ContendedFirstLoadState state) {
        return state.loadAsFollower();
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @Threads(1)
    public String halfOpenRecovery(HalfOpenRecoveryState state) {
        return state.value.get();
    }

    private static Retry retry() {
        return RetryConfig.builder()
                .calls(1)
                .delay(Duration.ZERO)
                .overallTimeout(Duration.ofSeconds(10))
                .enableMetrics(false)
                .addApplyOn(ResilientValue.UnavailableException.class)
                .build();
    }

    private static CircuitBreaker circuitBreaker() {
        return CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofHours(1))
                .enableMetrics(false)
                .addApplyOn(ResilientValue.UnavailableException.class)
                .build();
    }

    private static Timeout timeout() {
        return TimeoutConfig.builder()
                .timeout(Duration.ofSeconds(5))
                .currentThread(true)
                .enableMetrics(false)
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating benchmark callers", e);
        }
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (thread.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        if (thread.getState() != Thread.State.WAITING) {
            throw new IllegalStateException("Follower did not wait for the active load");
        }
    }

    @State(Scope.Thread)
    public static class FirstLoadState {
        private Retry retry;
        private CircuitBreaker circuitBreaker;
        private Timeout timeout;
        private AtomicInteger loads;
        private ResilientValue<String> value;

        @Setup(Level.Trial)
        public void setupPolicies() {
            retry = retry();
            circuitBreaker = circuitBreaker();
            timeout = timeout();
            loads = new AtomicInteger();
        }

        @Setup(Level.Iteration)
        public void setupValue() {
            loads.set(0);
            value = ResilientValue.create(new ResilientConfig<>("jmh-first-load", () -> {
                loads.incrementAndGet();
                return VALUE;
            }, retry, circuitBreaker, timeout));
        }

        @TearDown(Level.Iteration)
        public void verifyValue() {
            if (!value.loaded() || loads.get() != 1) {
                throw new IllegalStateException("First-load benchmark did not load exactly once");
            }
        }
    }

    @State(Scope.Group)
    public static class ContendedFirstLoadState {
        private Retry retry;
        private CircuitBreaker circuitBreaker;
        private Timeout timeout;
        private CountDownLatch loaderStarted;
        private CountDownLatch followerEntering;
        private AtomicInteger loads;
        private AtomicReference<Thread> followerThread;
        private ResilientValue<String> value;

        @Setup(Level.Trial)
        public void setup() {
            retry = retry();
            circuitBreaker = circuitBreaker();
            timeout = timeout();
            loads = new AtomicInteger();
            followerThread = new AtomicReference<>();
        }

        @Setup(Level.Iteration)
        public void setupValue() {
            loaderStarted = new CountDownLatch(1);
            followerEntering = new CountDownLatch(1);
            loads.set(0);
            followerThread.set(null);
            value = ResilientValue.create(new ResilientConfig<>("jmh-contended-load", () -> {
                loads.incrementAndGet();
                loaderStarted.countDown();
                await(followerEntering);
                awaitWaiting(followerThread.get());
                return VALUE;
            }, retry, circuitBreaker, timeout));
        }

        @TearDown(Level.Iteration)
        public void verifyValue() {
            if (!value.loaded() || loads.get() != 1) {
                throw new IllegalStateException("Contended benchmark did not share one load");
            }
        }

        private String loadAsLeader() {
            return value.get();
        }

        private String loadAsFollower() {
            await(loaderStarted);
            followerThread.set(Thread.currentThread());
            followerEntering.countDown();
            return value.get();
        }
    }

    @State(Scope.Thread)
    public static class HalfOpenRecoveryState {
        private Retry retry;
        private Timeout timeout;
        private QueuedExecutor executor;
        private CircuitBreaker circuitBreaker;
        private AtomicInteger loads;
        private ResilientValue<String> value;

        @Setup(Level.Trial)
        public void setupPolicies() {
            retry = retry();
            timeout = timeout();
            executor = new QueuedExecutor();
            loads = new AtomicInteger();
        }

        @Setup(Level.Iteration)
        public void setupValue() {
            loads.set(0);
            circuitBreaker = CircuitBreakerConfig.builder()
                    .volume(1)
                    .errorRatio(100)
                    .successThreshold(1)
                    .delay(Duration.ZERO)
                    .executor(executor)
                    .enableMetrics(false)
                    .addApplyOn(ResilientValue.UnavailableException.class)
                    .build();
            value = ResilientValue.create(new ResilientConfig<>("jmh-half-open-recovery", () -> {
                if (loads.incrementAndGet() == 1) {
                    throw new ResilientValue.UnavailableException("Expected benchmark failure");
                }
                return VALUE;
            }, retry, circuitBreaker, timeout));
            try {
                value.get();
                throw new IllegalStateException("Expected the initial load to fail");
            } catch (ResilientValue.UnavailableException _) {
                // expected; run the queued transition to half-open before measurement
            }
            if (!executor.runNext() || circuitBreaker.state() != CircuitBreaker.State.HALF_OPEN) {
                throw new IllegalStateException("Circuit breaker did not reach half-open state");
            }
        }

        @TearDown(Level.Iteration)
        public void verifyValue() {
            if (!value.loaded()
                    || loads.get() != 2
                    || circuitBreaker.state() != CircuitBreaker.State.CLOSED) {
                throw new IllegalStateException("Half-open benchmark did not recover on the second load");
            }
        }
    }

    private record ResilientConfig<T>(String description,
                                      Supplier<T> loader,
                                      Retry retry,
                                      CircuitBreaker circuitBreaker,
                                      Timeout timeout) implements ResilientValueConfig<T> {
    }

    private static final class QueuedExecutor extends AbstractExecutorService {
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
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private boolean runNext() {
            Runnable task = tasks.poll();
            if (task == null) {
                return false;
            }
            task.run();
            return true;
        }
    }
}
