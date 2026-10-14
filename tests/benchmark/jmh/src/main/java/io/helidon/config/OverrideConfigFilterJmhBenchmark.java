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
package io.helidon.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.config.spi.OverrideSource;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Measures steady-state override matching after the runtime deprecation warning has been emitted.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@SuppressWarnings("removal")
public class OverrideConfigFilterJmhBenchmark {
    @Param({"matching", "unmatched", "empty"})
    private String scenario;

    private OverrideConfigFilter filter;
    private Config.Key key;

    /**
     * Prepares the matching path and emits the warning outside measurement.
     */
    @Setup
    public void setup() {
        var data = OverrideSource.OverrideData.createFromWildcards(List.of(Map.entry("prod.*.level", "WARNING"))).data();
        var entries = scenario.equals("empty") ? data.subList(0, 0) : data;
        filter = new OverrideConfigFilter(() -> entries);
        key = Config.Key.create(scenario.equals("unmatched") ? "test.app.level" : "prod.app.level");
        // Consume the once-only warning outside the measured path, including the nonmatching cases.
        new OverrideConfigFilter(() -> data).apply(Config.Key.create("prod.app.level"), "INFO");
    }

    /**
     * Applies one override filter to an existing value.
     *
     * @return filtered value
     */
    @Benchmark
    public String apply() {
        return filter.apply(key, "INFO");
    }
}
