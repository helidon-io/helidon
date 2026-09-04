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

package io.helidon.metrics.benchmark.jmh;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class MetricsFormatterJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        String benchmark = Pattern.quote(MetricsFormatterJmhBenchmark.class.getName());
        String include = "^" + benchmark + "\\.(formatUnfiltered|formatTagSelected)$";
        String result = System.getProperty("metrics.formatter.jmh.result", "target/metrics-formatter-jmh.json");

        Options options = new OptionsBuilder()
                .include(include)
                .threads(1)
                .forks(Integer.getInteger("metrics.formatter.jmh.forks", 3))
                .warmupIterations(Integer.getInteger("metrics.formatter.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("metrics.formatter.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("metrics.formatter.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("metrics.formatter.jmh.measurementMillis", 2000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .build();
        new Runner(options).run();
    }
}
