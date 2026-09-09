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

class MetricsRecordingJmhRunnerTest {
    @Test
    void runExactBenchmark() throws RunnerException {
        String benchmark = Pattern.quote(MetricsRecordingJmhBenchmark.class.getName());
        String defaultInclude = "^" + benchmark + "\\.record(Counter|DistributionSummary|Timer)$";
        String include = System.getProperty("metrics.recording.jmh.include", defaultInclude);
        String result = System.getProperty("metrics.recording.jmh.result", "target/metrics-recording-jmh.json");

        Options options = new OptionsBuilder()
                .include(include)
                .threads(1)
                .forks(Integer.getInteger("metrics.recording.jmh.forks", 3))
                .warmupIterations(Integer.getInteger("metrics.recording.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("metrics.recording.jmh.warmupMillis", 1000)))
                .measurementIterations(Integer.getInteger("metrics.recording.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("metrics.recording.jmh.measurementMillis", 2000)))
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .build();
        new Runner(options).run();
    }
}
