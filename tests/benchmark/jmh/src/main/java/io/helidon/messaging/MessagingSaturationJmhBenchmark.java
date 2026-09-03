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

package io.helidon.messaging;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.infra.ThreadParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

/**
 * Tail-latency comparison between a saturated messaging channel and a fair-lock same-work baseline.
 * <p>
 * Select the contention level with the JMH {@code -t} option. The benchmark deliberately leaves the thread count
 * unspecified so the same fork can cover single-caller and saturated runs.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
public class MessagingSaturationJmhBenchmark {
    private static final String PAYLOAD = "message";
    private static final long WORK_TOKENS = 10_000;
    private static final int PENDING_BUDGET = 4_096;

    /**
     * Emit one payload through a shared channel with a single in-flight slot and no execution queue. Transient
     * saturated-admission rejections are retried, and their cost remains part of the caller-observed latency.
     *
     * @param state shared messaging state
     * @param counters admission counters
     * @param control JMH measurement phase
     */
    @Benchmark
    public void messagingEmit(MessagingState state, SaturationCounters counters, Control control) {
        emitUntilDelivered(state, counters, isMeasured(control));
    }

    /**
     * Measure saturated successful-delivery throughput and transient admission retries.
     *
     * @param state shared messaging state
     * @param counters admission counters
     * @param control JMH measurement phase
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void messagingThroughput(MessagingState state, SaturationCounters counters, Control control) {
        emitUntilDelivered(state, counters, isMeasured(control));
    }

    private void emitUntilDelivered(MessagingState state, SaturationCounters counters, boolean measured) {
        while (true) {
            try {
                state.emitter.emit(PAYLOAD);
                return;
            } catch (MessagingRejectedException e) {
                if (e.reason() != MessagingRejectedException.Reason.SATURATED) {
                    throw e;
                }
                if (measured) {
                    counters.saturatedRetries++;
                }
                Thread.yield();
            }
        }
    }

    private static boolean isMeasured(Control control) {
        return control.startMeasurement && !control.stopMeasurement;
    }

    /**
     * Execute the same fixed CPU work while holding a shared fair lock.
     *
     * @param state shared fair-lock state
     */
    @Benchmark
    public void fairLockBaseline(FairLockState state) {
        runWithFairLock(state);
    }

    /**
     * Measure same-work fair-lock throughput at the configured contention level.
     *
     * @param state shared fair-lock state
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void fairLockThroughput(FairLockState state) {
        runWithFairLock(state);
    }

    private void runWithFairLock(FairLockState state) {
        state.lock.lock();
        try {
            Blackhole.consumeCPU(WORK_TOKENS);
        } finally {
            state.lock.unlock();
        }
    }

    /**
     * Messaging resources shared by all benchmark worker threads.
     */
    @State(Scope.Benchmark)
    public static class MessagingState {
        private MessagingGraph graph;
        private Emitter<String> emitter;

        /**
         * Create and start the saturated graph once per trial.
         */
        @Setup(Level.Trial)
        public void setup() {
            MessagingExecutionConfig executionConfig = MessagingExecutionConfig.builder()
                    .queueCapacity(0)
                    .maxPendingAdmissions(PENDING_BUDGET)
                    .maxPendingMessages(PENDING_BUDGET)
                    .maxInFlightMessages(1)
                    .shutdownTimeout(Duration.ofSeconds(30))
                    .build();
            MessagingGraph.Assembler assembler = MessagingGraph.assembler().executionConfig(executionConfig);
            try {
                MessagingChannel<String> channel = assembler.channel("saturated", String.class);
                assembler.payloadSink(channel, ignored -> Blackhole.consumeCPU(WORK_TOKENS));
                graph = assembler.build();
                graph.start();
                emitter = graph.emitter(channel);
            } catch (RuntimeException | Error failure) {
                closeAfterSetupFailure(assembler, failure);
                throw failure;
            }
        }

        /**
         * Close the graph after the trial has stopped all benchmark invocations.
         */
        @TearDown(Level.Trial)
        public void tearDown() {
            emitter = null;
            MessagingGraph currentGraph = graph;
            graph = null;
            if (currentGraph != null) {
                currentGraph.close();
            }
        }

        private void closeAfterSetupFailure(MessagingGraph.Assembler assembler, Throwable failure) {
            try {
                if (graph == null) {
                    assembler.close();
                } else {
                    graph.close();
                    graph = null;
                }
            } catch (RuntimeException | Error closeFailure) {
                if (failure != closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
    }

    /**
     * Fair-lock resources shared by all benchmark worker threads.
     */
    @State(Scope.Benchmark)
    public static class FairLockState {
        private final ReentrantLock lock = new ReentrantLock(true);
    }

    /**
     * Per-worker admission counters reported as JMH secondary results.
     */
    @State(Scope.Thread)
    public static class SaturationCounters {
        private static final ConcurrentHashMap<Integer, SaturationCounters> ACTIVE = new ConcurrentHashMap<>();

        /**
         * Transient saturated-admission retries before successful delivery.
         */
        private long saturatedRetries;

        /**
         * Reset and register this per-worker counter after the profiler clears the previous iteration's registry.
         *
         * @param threadParams JMH worker identity
         */
        @Setup(Level.Iteration)
        public void reset(ThreadParams threadParams) {
            saturatedRetries = 0;
            ACTIVE.put(threadParams.getThreadIndex(), this);
        }

        private static long saturatedRetries() {
            return ACTIVE.values().stream().mapToLong(counter -> counter.saturatedRetries).sum();
        }
    }

    /**
     * Reports delivery and retry totals from the same fork and iteration as each messaging latency or throughput result.
     */
    public static class SaturationProfiler implements InternalProfiler {
        private static final String MESSAGING_METHOD = MessagingSaturationJmhBenchmark.class.getName() + ".messaging";

        @Override
        public String getDescription() {
            return "Messaging delivery and saturation retry event totals";
        }

        @Override
        public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
            if (benchmarkParams.getBenchmark().startsWith(MESSAGING_METHOD)) {
                SaturationCounters.ACTIVE.clear();
            }
        }

        @Override
        public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams,
                                                           IterationParams iterationParams,
                                                           IterationResult result) {
            if (!benchmarkParams.getBenchmark().startsWith(MESSAGING_METHOD)) {
                return List.of();
            }
            return List.of(new ScalarResult("deliveries",
                                            result.getMetadata().getMeasuredOps(),
                                            "events",
                                            AggregationPolicy.SUM),
                           new ScalarResult("saturatedRetries",
                                            SaturationCounters.saturatedRetries(),
                                            "events",
                                            AggregationPolicy.SUM));
        }
    }
}
