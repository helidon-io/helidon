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

package io.helidon.http.benchmark.jmh;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import io.helidon.http.MethodCreateJmhBenchmark;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

class MethodCreateJmhRunnerTest {
    private static final String METHODS = "(canonicalGet|canonicalPost|lowercaseGet)";

    @Test
    void runExactBenchmarks() throws RunnerException {
        String result = System.getProperty("http.method.jmh.result", "target/http-method-jmh.json");
        String include = "^" + Pattern.quote(MethodCreateJmhBenchmark.class.getName()) + "\\." + METHODS + "$";

        Options options = new OptionsBuilder()
                .include(include)
                .threads(1)
                .forks(Integer.getInteger("http.method.jmh.forks", 3))
                .warmupIterations(Integer.getInteger("http.method.jmh.warmupIterations", 3))
                .warmupTime(TimeValue.milliseconds(Long.getLong("http.method.jmh.warmupMillis", 500)))
                .measurementIterations(Integer.getInteger("http.method.jmh.measurementIterations", 5))
                .measurementTime(TimeValue.milliseconds(Long.getLong("http.method.jmh.measurementMillis", 1_000)))
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.NANOSECONDS)
                .addProfiler(GCProfiler.class)
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(result)
                .timeout(TimeValue.seconds(Long.getLong("http.method.jmh.timeoutSeconds", 30)))
                .build();

        new Runner(options).run();
    }
}
