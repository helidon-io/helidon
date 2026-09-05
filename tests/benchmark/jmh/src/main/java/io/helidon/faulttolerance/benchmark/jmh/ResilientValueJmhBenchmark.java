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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.RetryConfig;
import io.helidon.faulttolerance.ResilientValue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
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

        cachedValue = ResilientValue.create("jmh-cached", () -> {
            cachedLoads.incrementAndGet();
            return VALUE;
        }, RetryConfig.create(), CircuitBreakerConfig.create());
        cachedValue.get();

        RetryConfig retryConfig = RetryConfig.builder()
                .calls(1)
                .delay(Duration.ZERO)
                .overallTimeout(Duration.ofSeconds(1))
                .enableMetrics(false)
                .buildPrototype();
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.builder()
                .volume(1)
                .errorRatio(100)
                .successThreshold(1)
                .delay(Duration.ofHours(1))
                .enableMetrics(false)
                .buildPrototype();
        openValue = ResilientValue.create("jmh-open", () -> {
            unavailableLoads.incrementAndGet();
            throw ResilientValue.unavailable("Expected benchmark failure");
        }, retryConfig, circuitBreakerConfig);

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
    public String directFieldRead() {
        return directValue;
    }

    @Benchmark
    public String cachedValueRead() {
        return cachedValue.get();
    }

    @Benchmark
    public boolean openBreakerRejection() {
        try {
            openValue.get();
            return false;
        } catch (ResilientValue.UnavailableException _) {
            return true;
        }
    }
}
