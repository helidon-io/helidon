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

package io.helidon.common.benchmark.jmh;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.helidon.common.concurrency.limits.LimitAlgorithm;
import io.helidon.common.concurrency.limits.RateLimitingAlgorithmType;
import io.helidon.common.concurrency.limits.ThroughputLimit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ThroughputLimitJmhBenchmark {
    @Benchmark
    public LimitAlgorithm.Outcome acquireAndSuccess(NormalRateState state) {
        state.nanos += 1_000_001;
        return acquireAndComplete(state.limit);
    }

    @Benchmark
    public LimitAlgorithm.Outcome acquireHighRateAndSuccess(HighRateState state) {
        state.nanos = state.invocations++ / 2 + 1;
        return acquireAndComplete(state.limit);
    }

    private static LimitAlgorithm.Outcome acquireAndComplete(ThroughputLimit limit) {
        LimitAlgorithm.Outcome outcome = limit.tryAcquireOutcome();
        if (outcome instanceof LimitAlgorithm.Outcome.Accepted accepted) {
            accepted.token().success();
            return outcome;
        }
        throw new IllegalStateException("The configured clock must permit every benchmark operation: " + outcome);
    }

    @State(Scope.Thread)
    public static class NormalRateState {
        @Param({"TOKEN_BUCKET", "FIXED_RATE"})
        private RateLimitingAlgorithmType algorithm;

        private ThroughputLimit limit;
        private long nanos;

        @Setup
        public void setup() {
            limit = ThroughputLimit.builder()
                    .amount(1000)
                    .duration(Duration.ofSeconds(1))
                    .rateLimitingAlgorithm(algorithm)
                    .clock(() -> nanos)
                    .build();
            int initialPermits = algorithm == RateLimitingAlgorithmType.TOKEN_BUCKET ? 1000 : 1;
            for (int i = 0; i < initialPermits; i++) {
                acquireAndComplete(limit);
            }
        }
    }

    @State(Scope.Thread)
    public static class HighRateState {
        @Param({"TOKEN_BUCKET", "FIXED_RATE"})
        private RateLimitingAlgorithmType algorithm;

        private ThroughputLimit limit;
        private long nanos;
        private long invocations;

        @Setup
        public void setup() {
            limit = ThroughputLimit.builder()
                    .amount(200)
                    .duration(Duration.ofNanos(100))
                    .rateLimitingAlgorithm(algorithm)
                    .clock(() -> nanos)
                    .build();
            int initialPermits = algorithm == RateLimitingAlgorithmType.TOKEN_BUCKET ? 200 : 1;
            for (int i = 0; i < initialPermits; i++) {
                acquireAndComplete(limit);
            }
        }
    }
}
